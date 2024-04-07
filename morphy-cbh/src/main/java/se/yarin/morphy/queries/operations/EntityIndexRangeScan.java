package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class EntityIndexRangeScan<T extends Entity & Comparable<T>> extends QueryOperator<T> {
    private final EntityIndexReadTransaction<T> txn;
    private final @Nullable EntityFilter<T> entityFilter;
    private final @Nullable T rangeStart, rangeEnd;
    private final boolean reverse;
    private final EntityType entityType;

    public EntityIndexRangeScan(@NotNull QueryContext queryContext, @NotNull EntityType entityType, @Nullable EntityFilter<T> entityFilter, @Nullable T rangeStart, @Nullable T rangeEnd, boolean reverse) {
        super(queryContext, true);
        this.txn = (EntityIndexReadTransaction<T>) queryContext.transaction().entityTransaction(entityType);
        this.entityType = entityType;
        this.entityFilter = entityFilter;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.reverse = reverse; // If true, rangeStart should be greater than rangeEnd
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }

    public @NotNull QuerySortOrder<T> sortOrder() {
        return (QuerySortOrder<T>) QuerySortOrder.byEntityDefaultIndex(entityType, reverse);
    }

    public boolean mayContainDuplicates() {
        return false;
    }

    public Stream<QueryData<T>> operatorStream() {
        if (!this.reverse) {
            return txn.streamOrderedAscending(rangeStart, rangeEnd, entityFilter).map(QueryData::new);
        } else {
            return txn.streamOrderedDescending(rangeStart, rangeEnd, entityFilter).map(QueryData::new);
        }
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        long scanCount = context().queryPlanner().entityRangeEstimate(!reverse ? rangeStart : rangeEnd, !reverse ? rangeEnd : rangeStart, null);
        long matchCount = context().queryPlanner().entityRangeEstimate(!reverse ? rangeStart : rangeEnd, !reverse ? rangeEnd : rangeStart, entityFilter);

        operatorCost
                .estimateRows(matchCount)
                .estimatePageReads(context().queryPlanner().estimateEntityPageReads(scanCount, entityType))
                .estimateDeserializations(matchCount);
    }

    @Override
    public String toString() {
        ArrayList<String> params = new ArrayList<>();
        if (rangeStart != null) {
            params.add("start: '" + rangeStart + "'");
        }
        if (rangeEnd != null) {
            params.add("end: '" + rangeEnd + "'");
        }
        if (entityFilter != null) {
            params.add("filter: " + entityFilter);
        }
        if (reverse) {
            params.add("reverse: true");
        }
        return entityType.nameSingularCapitalized() + "IndexRangeScan(" + String.join(", ", params) + ")";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return this.txn.metricsProviders();
    }
}
