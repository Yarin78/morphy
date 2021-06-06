package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.queries.QueryContext;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class QueryOperator<T> {
    private final @NotNull QueryContext queryContext;
    private final AtomicInteger actualRowCount = new AtomicInteger(0);

    public QueryOperator(@NotNull QueryContext queryContext) {
        this.queryContext = queryContext;
    }

    public QueryContext context() {
        return queryContext;
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

    protected abstract Stream<T> operatorStream();

    public abstract OperatorCost estimateCost();

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
            sb.append(String.format(" {estimateRows=%d, estimateDeser=%d, estimatePageReads=%d, actualRows=%d}",
                    estimate.rows(), estimate.numDeserializations(), estimate.pageReads(), actualRows()));
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
