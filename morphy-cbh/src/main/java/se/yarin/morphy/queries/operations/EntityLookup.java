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

public class EntityLookup<T extends Entity & Comparable<T>> extends QueryOperator<T> {
    private final @Nullable EntityFilter<T> entityFilter;
    private final @NotNull QueryOperator<T> source;
    private final @NotNull EntityIndexReadTransaction<T> txn;
    private final @NotNull EntityType entityType;

    public EntityLookup(@NotNull QueryContext queryContext, @NotNull EntityType entityType, @NotNull QueryOperator<T> source, @Nullable EntityFilter<T> entityFilter) {
        super(queryContext, true);
        assert !source.hasFullData();
        this.txn = (EntityIndexReadTransaction<T>) transaction().entityTransaction(entityType);
        this.entityType = entityType;
        this.source = source;
        this.entityFilter = entityFilter;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of(source);
    }

    public @NotNull QuerySortOrder<T> sortOrder() {
        return source.sortOrder();
    }

    public boolean mayContainDuplicates() {
        return source.mayContainDuplicates();
    }

    public Stream<QueryData<T>> operatorStream() {
        return this.source.stream()
//                TODO: Something like this is nicer, but this loses the weight
//                .map(entity -> txn.get(entity.id(), entityFilter))
//                .filter(Objects::nonNull)
//                .map(QueryData::new);
                .flatMap(data -> {
                    T entity = txn.get(data.id(), entityFilter);
                    if (entity == null) {
                        return Stream.of();
                    } else {
                        return Stream.of(new QueryData<>(data.id(), entity, data.weight()));
                    }
                });
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        OperatorCost sourceCost = source.getOperatorCost();

        double ratio = context().queryPlanner().entityFilterEstimate(entityFilter, entityType);
        long estimateRows = OperatorCost.capRowEstimate((int) Math.round(sourceCost.estimateRows() * ratio));

        operatorCost
                .estimateRows(estimateRows)
                .estimateDeserializations(estimateRows) // Non-matching rows will mostly be caught non-deserialized
                .estimatePageReads(context().queryPlanner().estimateGamePageReads(sourceCost.estimateRows()));
    }

    @Override
    public String toString() {
        ArrayList<String> params = new ArrayList<>();
        if (entityFilter != null) {
            params.add("filter: " + entityFilter);
        }
        return entityType.nameSingularCapitalized() + "Lookup(" + String.join(", ", params) + ")";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        // TODO: Make generic
        // return List.of(database().playerIndex());
        return List.of(database().tournamentIndex(), database().tournamentExtraStorage());
    }
}
