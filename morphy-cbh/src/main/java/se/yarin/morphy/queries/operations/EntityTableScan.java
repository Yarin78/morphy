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

/**
 * Scans an entity base in ID order from a given ID.
 * Optionally filters out entities on a filter predicate.
 */
public class EntityTableScan<T extends Entity & Comparable<T>> extends QueryOperator<T> {
    private final @NotNull EntityIndexReadTransaction<T> txn;
    private final @NotNull EntityType entityType;
    private final @Nullable EntityFilter<T> entityFilter;
    private final @Nullable Integer startId;
    private final @Nullable Integer endId;


    public EntityTableScan(@NotNull QueryContext queryContext, @NotNull EntityType entityType, @Nullable EntityFilter<T> entityFilter) {
        this(queryContext, entityType, entityFilter, null, null);
    }

    public EntityTableScan(@NotNull QueryContext queryContext, @NotNull EntityType entityType, @Nullable EntityFilter<T> entityFilter, @Nullable Integer startId, @Nullable Integer endId) {
        super(queryContext, true);

        this.txn = (EntityIndexReadTransaction<T>) transaction().entityTransaction(entityType);
        this.entityType = entityType;
        this.entityFilter = entityFilter;
        this.startId = startId;
        this.endId = endId;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }

    public @NotNull QuerySortOrder<T> sortOrder() {
        return QuerySortOrder.byId();
    }

    public boolean mayContainDuplicates() {
        return false;
    }

    @Override
    public Stream<QueryData<T>> operatorStream() {
        return txn.stream(this.entityFilter).map(QueryData::new);
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        int totalEntities = context().database().entityIndex(entityType).count();
        int startId = this.startId == null ? 0 : this.startId;
        int endId = this.endId == null ? totalEntities : this.endId;
        int numScannedEntities = Math.max(0, endId - startId);
        double scanRatio = 1.0 * numScannedEntities / totalEntities;

        double matchingRatio = context().queryPlanner().entityFilterEstimate(entityFilter, entityType);
        long estimateRows = OperatorCost.capRowEstimate((int) Math.round(numScannedEntities * matchingRatio));

        long pageReads = Math.round(context().database().entityIndex(entityType).numDiskPages() * scanRatio);

        operatorCost
                .estimateRows(estimateRows)
                .estimateDeserializations(estimateRows) // Non-matching rows will mostly be caught non-deserialized
                .estimatePageReads(pageReads)
                .build();
    }

    @Override
    public String toString() {
        ArrayList<String> params = new ArrayList<>();
        if (startId != null) {
            params.add("startId: " + startId);
        }
        if (endId != null) {
            params.add("endId: " + endId);
        }
        if (entityFilter != null) {
            params.add("filter: " + entityFilter);
        }

        return entityType.nameSingularCapitalized() + "TableScan(" + String.join(", ", params) + ")";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return this.txn.metricsProviders();
    }
}
