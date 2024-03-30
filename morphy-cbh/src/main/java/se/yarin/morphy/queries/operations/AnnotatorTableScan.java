package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.Annotator;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.List;
import java.util.stream.Stream;

public class AnnotatorTableScan extends QueryOperator<Annotator> {
    private final @NotNull EntityIndexReadTransaction<Annotator> txn;
    private final @Nullable EntityFilter<Annotator> annotatorFilter;

    private final int firstAnnotatorId;

    public AnnotatorTableScan(@NotNull QueryContext queryContext, @Nullable EntityFilter<Annotator> annotatorFilter) {
        this(queryContext, annotatorFilter, 0);
    }

    public AnnotatorTableScan(@NotNull QueryContext queryContext, @Nullable EntityFilter<Annotator> annotatorFilter, int firstAnnotatorId) {
        super(queryContext, true);
        this.txn = transaction().annotatorTransaction();
        this.annotatorFilter = annotatorFilter;
        this.firstAnnotatorId = firstAnnotatorId;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }

    public @NotNull QuerySortOrder<Annotator> sortOrder() {
        return QuerySortOrder.byId();
    }

    public boolean mayContainDuplicates() {
        return false;
    }

    @Override
    public Stream<QueryData<Annotator>> operatorStream() {
        return txn.stream(this.annotatorFilter).map(QueryData::new);
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        int numPlayers = context().database().annotatorIndex().count();
        double ratio = context().queryPlanner().annotatorFilterEstimate(annotatorFilter);
        long estimateRows = OperatorCost.capRowEstimate((int) Math.round(numPlayers * ratio));

        long pageReads = context().database().annotatorIndex().numDiskPages();

        operatorCost.estimateRows(estimateRows)
                .estimateDeserializations(estimateRows) // Non-matching rows will mostly be caught non-deserialized
                .estimatePageReads(pageReads)
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
