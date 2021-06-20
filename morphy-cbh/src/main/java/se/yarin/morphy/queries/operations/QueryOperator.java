package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Instrumentation;
import se.yarin.morphy.metrics.*;
import se.yarin.morphy.queries.QueryContext;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class QueryOperator<T> {
    private static final double SCATTERED_IO_COST_MULTIPLIER = 4;
    private static final double SCATTERED_IO_PAGE_COST = 0.08;
    private static final double BURST_IO_PAGES_COST = 0.02;
    private static final double DESERIALIZATION_COST = 0.0006;

    private final @NotNull QueryContext queryContext;
    private final AtomicInteger actualRowCount = new AtomicInteger(0);
    private final AtomicInteger actualPageReads = new AtomicInteger(0);
    private final AtomicInteger actualDeser = new AtomicInteger(0);

    private @Nullable QueryCost actualQueryCost;

    public QueryOperator(@NotNull QueryContext queryContext) {
        this.queryContext = queryContext;
    }

    public @NotNull QueryContext context() {
        return queryContext;
    }

    public @NotNull Database database() {
        return queryContext.database();
    }

    public DatabaseReadTransaction transaction() {
        return queryContext.transaction();
    }

    public Stream<T> stream() {
        Stream<T> stream = operatorStream();
        if (queryContext.traceCost()) {
            stream = stream.peek(t -> actualRowCount.incrementAndGet());
        }
        return stream;
    }

    public List<T> executeProfiled() {
        Instrumentation instrumentation = context().databaseContext().instrumentation();
        var queryMetrics = instrumentation.pushContext("query", true);
        try {
            long start = System.currentTimeMillis();
            List<T> queryResult = stream().collect(Collectors.toList());
            long elapsed = System.currentTimeMillis() - start;

            long numRows = updateActual(queryMetrics);

            long numDeser = 0, numPageReads = 0;
            for (@NotNull ItemMetrics itemMetrics : queryMetrics.getMetricsByType(ItemMetrics.class).values()) {
                numDeser += itemMetrics.deserializations();
            }
            for (@NotNull FileMetrics fileMetrics : queryMetrics.getMetricsByType(FileMetrics.class).values()) {
                numPageReads += fileMetrics.physicalPageReads();
            }

            this.actualQueryCost = ImmutableQueryCost.builder()
                .rows(numRows)
                .numDeserializations(numDeser)
                .pageReads(numPageReads)
                .ioCost(numPageReads)
                .cpuCost(numDeser * 10 + numRows) // TODO deser cost should be variable per item type
                .wallClockTime(elapsed)
                .build();
            return queryResult;
        } finally {
            instrumentation.popContext();
        }
    }

    protected long updateActual(@NotNull MetricsRepository metricsRepository) {
        long totalRowsProcessed = actualRows();
        for (QueryOperator<?> source : sources()) {
            totalRowsProcessed += source.updateActual(metricsRepository);
        }

        // TODO: The same metric provider shouldn't be used on multiple query operators
        for (MetricsProvider metricProvider : metricProviders()) {
            metricProvider.getMetricsKeys().stream().distinct().forEach(key -> {
                //if (metricsRepository.exists(key)) {
                    Metrics metric = metricsRepository.getMetrics(key);
                    if (metric instanceof ItemMetrics) {
                        actualDeser.addAndGet(((ItemMetrics) metric).deserializations());
                    }
                    if (metric instanceof FileMetrics) {
                        //actualPageReads.addAndGet(((FileMetrics) metric).logicalPageReads() + ((FileMetrics) metric).physicalPageReads());
                        actualPageReads.addAndGet(((FileMetrics) metric).physicalPageReads());
                    }
                //}
            });
        }

        return totalRowsProcessed;
    }

    protected abstract List<MetricsProvider> metricProviders();

    protected abstract Stream<T> operatorStream();

    public abstract OperatorCost estimateCost();

    public QueryCost estimateQueryCost() {
        long rows = 0, pageReads = 0, deser = 0;
        double ioCost = 0.0, cpuCost = 0.0;
        Queue<QueryOperator<?>> operators = new LinkedList<>();
        operators.add(this);
        while (!operators.isEmpty()) {
            QueryOperator<?> op = operators.poll();
            operators.addAll(op.sources());

            OperatorCost opCost = op.estimateCost();
            rows += opCost.rows();
            pageReads += opCost.pageReads();
            deser += opCost.numDeserializations();

            if (op.getClass().getName().contains("TableScan")) {
                ioCost += opCost.pageReads() * BURST_IO_PAGES_COST;
            } else {
                ioCost += opCost.pageReads() * SCATTERED_IO_PAGE_COST;
            }
            cpuCost += opCost.numDeserializations() * DESERIALIZATION_COST;
        }

        return ImmutableQueryCost.builder()
                .pageReads(pageReads)
                .numDeserializations(deser)
                .rows(rows)
                .ioCost(ioCost)
                .cpuCost(cpuCost)
                .wallClockTime((long) (ioCost + cpuCost))
                .build();
    }

    public @NotNull QueryCost actualQueryCost() {
        if (actualQueryCost == null) {
            throw new IllegalStateException("To get actual query cost, the uery must be executed with profiling enabled.");
        }
        return actualQueryCost;
    }

    public abstract List<QueryOperator<?>> sources();

    public int actualRows() {
        return actualRowCount.get();
    }

    public String debugString(boolean includeCost) {
        StringBuilder sb = new StringBuilder();
        buildDebugString(sb, 0, includeCost);
        return sb.toString();
    }

    public void buildDebugString(StringBuilder sb, int indent, boolean includeCost) {
        sb.append("  ".repeat(indent));
        sb.append(this);
        if (includeCost) {
            OperatorCost estimate = estimateCost();
            sb.append(String.format(" {estimateRows=%d, estimateDeser=%d, estimatePageReads=%d, actualRows=%d, actualDeser=%d, actualPageReads=%d}",
                    estimate.rows(), estimate.numDeserializations(), estimate.pageReads(), actualRows(), actualDeser.get(), actualPageReads.get()));
        }
        sb.append("\n");
        for (QueryOperator<?> source : sources()) {
            source.buildDebugString(sb, indent + 1, includeCost);
        }
    }

    protected String indent(String s) {
        return Arrays.stream(s.split("\n")).map(line -> "  " + line).collect(Collectors.joining("\n"));
    }
}
