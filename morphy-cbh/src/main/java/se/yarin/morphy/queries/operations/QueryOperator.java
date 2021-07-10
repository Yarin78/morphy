package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.Instrumentation;
import se.yarin.morphy.metrics.*;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class QueryOperator<T extends IdObject> {
    private static final double SCATTERED_IO_COST_MULTIPLIER = 4;
    private static final double SCATTERED_IO_PAGE_COST = 0.08;
    private static final double BURST_IO_PAGES_COST = 0.02;
    private static final double DESERIALIZATION_COST = 0.0006;

    private final @NotNull QueryContext queryContext;
    private final boolean hasFullData;  // If true, data() will be set in the stream, otherwise not
    private @Nullable OperatorCost actualOperatorCost; // Only actual fields set
    private final AtomicInteger actualRowCount = new AtomicInteger(0);

    // These fields are set on the outermost query operator after the query has been executed
    private @Nullable MetricsRepository queryMetrics;
    private long actualWallClockTime;

    public QueryOperator(@NotNull QueryContext queryContext, boolean hasFullData) {
        this.queryContext = queryContext;
        this.hasFullData = hasFullData;
    }

    public @NotNull QueryContext context() {
        return queryContext;
    }

    public @NotNull Database database() {
        return queryContext.database();
    }

    public boolean hasFullData() {
        return hasFullData;
    }

    public DatabaseReadTransaction transaction() {
        return queryContext.transaction();
    }

    public final Stream<QueryData<T>> stream() {
        Stream<QueryData<T>> stream = operatorStream();
        if (queryContext.traceCost()) {
            stream = stream.peek(t -> actualRowCount.incrementAndGet());
        }
        return stream;
    }

    public final List<QueryData<T>> executeProfiled() {
        Instrumentation instrumentation = context().databaseContext().instrumentation();
        var queryMetrics = instrumentation.pushContext("query", true);
        try {
            long start = System.currentTimeMillis();
            List<QueryData<T>> queryResult = stream().collect(Collectors.toList());
            this.actualWallClockTime = System.currentTimeMillis() - start;
            Set<MetricsKey> duplicateKeys = streamMetricsKeys()
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet().stream()
                    .filter(m -> m.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            setOperatorActualMetrics(queryMetrics, duplicateKeys);
            this.queryMetrics = queryMetrics;
            return queryResult;
        } finally {
            instrumentation.popContext();
        }
    }

    protected Stream<MetricsKey> streamMetricsKeys() {
        Stream<MetricsKey> metricsKeys = metricProviders().stream()
                .flatMap(metricsProvider -> metricsProvider.getMetricsKeys().stream().distinct());

        for (QueryOperator<?> source : sources()) {
            metricsKeys = Stream.concat(metricsKeys, source.streamMetricsKeys());
        }
        return metricsKeys;
    }

    protected void setOperatorActualMetrics(@NotNull MetricsRepository metricsRepository, Set<MetricsKey> duplicateKeys) {
        for (QueryOperator<?> source : sources()) {
            source.setOperatorActualMetrics(metricsRepository, duplicateKeys);
        }

        int deserializations = 0, physicalReads = 0, logicalReads = 0;
        boolean duplicate = false;
        for (MetricsProvider metricProvider : metricProviders()) {
            List<MetricsKey> metricKeys = metricProvider.getMetricsKeys().stream().distinct().collect(Collectors.toList());
            for (MetricsKey key : metricKeys) {
                Metrics metric = metricsRepository.getMetrics(key);
                if (metric instanceof ItemMetrics) {
                    deserializations += (((ItemMetrics) metric).deserializations());
                }
                if (metric instanceof FileMetrics) {
                    physicalReads += ((FileMetrics) metric).physicalPageReads();
                    logicalReads += ((FileMetrics) metric).logicalPageReads();
                }
                if (duplicateKeys.contains(key)) {
                    duplicate = true;
                }
            }
        }

        actualOperatorCost = ImmutableOperatorCost.builder()
                .actualDeserializations(deserializations)
                .actualPhysicalPageReads(physicalReads)
                .actualLogicalPageReads(logicalReads)
                .actualRows(actualRowCount.get())
                .actualIsDuplicate(duplicate)
                .build();
    }

    protected abstract List<MetricsProvider> metricProviders();

    protected abstract Stream<QueryData<T>> operatorStream();

    protected abstract void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost);

    /**
     * Gets the cost of the single query operator.
     * If the query hasn't yet been executed, only the estimate will be set.
     * After execution, if profiling was enabled, actual cost will be included
     * @return the cost of this query operator
     */
    public final OperatorCost getOperatorCost() {
        ImmutableOperatorCost.Builder cost = ImmutableOperatorCost.builder();
        if (this.actualOperatorCost != null) {
            cost.from(this.actualOperatorCost);
        }
        estimateOperatorCost(cost);
        return cost.build();
    }

    /**
     * Gets a summary of the query cost for the entire query.
     * If it hasn't yet been executed, only the estimate will be set.
     * After execution, if profiling was enabled, actual cost will be included.
     * @return the cost of this query
     */
    public final QueryCost getQueryCost() {
        long totalEstimateRows = 0, totalEstimatePageReads = 0, totalEstimateDeserializations = 0;
        double totalEstimateIOCost = 0.0, totalEstimateCPUCost = 0.0;
        long totalActualRows = 0;

        // Process operators in BFS order
        Queue<QueryOperator<?>> operators = new LinkedList<>();
        operators.add(this);
        while (!operators.isEmpty()) {
            QueryOperator<?> op = operators.poll();
            operators.addAll(op.sources());

            // Estimate
            OperatorCost opCost = op.getOperatorCost();
            totalEstimateRows += opCost.estimateRows();
            totalEstimatePageReads += opCost.estimatePageReads();
            totalEstimateDeserializations += opCost.estimateDeserializations();

            if (op.getClass().getName().contains("TableScan")) {
                totalEstimateIOCost += opCost.estimatePageReads() * BURST_IO_PAGES_COST;
            } else {
                totalEstimateIOCost += opCost.estimatePageReads() * SCATTERED_IO_PAGE_COST;
            }
            totalEstimateCPUCost += opCost.estimateDeserializations() * DESERIALIZATION_COST;

            // Actual
            totalActualRows += op.actualRowCount.get();
        }

        long totalActualDeserializations = 0, totalActualPhysicalReads = 0, totalActualLogicalReads = 0;
        if (queryMetrics != null) {
            for (@NotNull ItemMetrics itemMetrics : queryMetrics.getMetricsByType(ItemMetrics.class).values()) {
                totalActualDeserializations += itemMetrics.deserializations();
            }
            for (@NotNull FileMetrics fileMetrics : queryMetrics.getMetricsByType(FileMetrics.class).values()) {
                totalActualPhysicalReads += fileMetrics.physicalPageReads();
                totalActualLogicalReads += fileMetrics.logicalPageReads();
            }
        }

        return ImmutableQueryCost.builder()
                .estimatedPageReads(totalEstimatePageReads)
                .estimatedDeserializations(totalEstimateDeserializations)
                .estimatedRows(totalEstimateRows)
                .estimatedIOCost(totalEstimateIOCost)
                .estimatedCpuCost(totalEstimateCPUCost)
                .estimatedTotalCost(totalEstimateIOCost + totalEstimateCPUCost)
                .actualRows(totalActualRows)
                .actualDeserializations(totalActualDeserializations)
                .actualLogicalPageReads(totalActualLogicalReads)
                .actualPhysicalPageReads(totalActualPhysicalReads)
                .actualWallClockTime(actualWallClockTime)
                .build();
    }

    public abstract List<QueryOperator<?>> sources();

    /**
     * The sort order that the data from this operator will be in
     */
    public abstract @NotNull QuerySortOrder<T> sortOrder();

    public abstract boolean mayContainDuplicates();

    public String debugString(boolean includeCost) {
        StringBuilder sb = new StringBuilder();
        buildDebugString(sb, 0, includeCost);
        return sb.toString();
    }

    public void buildDebugString(StringBuilder sb, int indent, boolean includeCost) {
        sb.append("  ".repeat(indent));
        sb.append(this);
        if (includeCost) {
            OperatorCost cost = getOperatorCost();
            sb.append(String.format(" {data: %b, estimate: {rows=%d, deser=%d, pageReads=%d}, actual%s: {rows=%d, deser=%d, physicalPageReads=%d, logicalPageReads=%d}}",
                    hasFullData,
                    cost.estimateRows(), cost.estimateDeserializations(), cost.estimatePageReads(),
                    cost.actualIsDuplicate() ? " (*)" : "",
                    cost.actualRows(), cost.actualDeserializations(), cost.actualPhysicalPageReads(), cost.actualLogicalPageReads()));
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
