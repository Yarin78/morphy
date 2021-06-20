package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.Annotator;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.stream.Stream;

public class AnnotatorTableScan extends QueryOperator<Annotator> {
    private final @NotNull EntityIndexReadTransaction<Annotator> txn;
    private final @Nullable EntityFilter<Annotator> annotatorFilter;

    public AnnotatorTableScan(@NotNull QueryContext queryContext, @Nullable EntityFilter<Annotator> annotatorFilter) {
        super(queryContext);
        this.txn = transaction().annotatorTransaction();
        this.annotatorFilter = annotatorFilter;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }

    @Override
    public Stream<Annotator> operatorStream() {
        return txn.stream(this.annotatorFilter);
    }

    @Override
    public OperatorCost estimateCost() {
        int numPlayers = context().database().annotatorIndex().count();
        double ratio = context().queryPlanner().annotatorFilterEstimate(annotatorFilter);
        long estimateRows = OperatorCost.capRowEstimate((int) Math.round(numPlayers * ratio));

        long pageReads = context().database().annotatorIndex().numDiskPages();

        return ImmutableOperatorCost.builder()
                .rows(estimateRows)
                .numDeserializations(estimateRows) // Non-matching rows will mostly be caught non-deserialized
                .pageReads(pageReads)
                .build();
    }

    @Override
    public String toString() {
        if (annotatorFilter != null) {
            return "AnnotatorTableScan(filter: " + annotatorFilter + ")";
        }
        return "AnnotatorTableScan()";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(database().annotatorIndex());
    }
}
