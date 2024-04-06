package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.filters.CombinedFilter;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.EntityIndexFilter;
import se.yarin.morphy.entities.filters.ManualFilter;
import se.yarin.morphy.queries.operations.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class EntityQueryPlanner {
    private @NotNull QueryPlanner queryPlanner;

    public @NotNull QueryPlanner queryPlanner() {
        return queryPlanner;
    }

    public void setQueryPlanner(@NotNull QueryPlanner queryPlanner) {
        this.queryPlanner = queryPlanner;
    }

    public EntityQueryPlanner(@NotNull QueryPlanner queryPlanner) {
        this.queryPlanner = queryPlanner;
    }


    public <T extends Entity & Comparable<T>> List<QueryOperator<T>> getQueryPlans(@NotNull QueryContext context, @NotNull EntityQuery<T> entityQuery, boolean fullData) {
        List<QueryOperator<T>> candidateQueryPlans = new ArrayList<>();

        List<EntitySourceQuery<T>> sources = getSources(context, entityQuery);

        for (List<EntitySourceQuery<T>> sourceCombination : queryPlanner().sourceCombinations(sources)) {
            // Join the sources starting with the one returning least amount of expected rows
            List<EntitySourceQuery<T>> sortedByEstimatedRows = sourceCombination.stream().sorted(Comparator.comparingLong(EntitySourceQuery::estimateRows)).collect(Collectors.toList());

            candidateQueryPlans.add(getFinalQueryOperator(entityQuery, sortedByEstimatedRows, fullData));

            if (!entityQuery.sortOrder().isNone()) {
                // We also want to check if one of the source queries already has the data in the right order.
                // If so, by starting with that one and the applying the other sources in order we might end up with
                // a better result as we don't need the final sort.
                for (int i = 1; i < sortedByEstimatedRows.size(); i++) { // i=0 is already covered above
                    EntitySourceQuery<T> sourceQuery = sortedByEstimatedRows.get(i);
                    if (sourceQuery.operator().sortOrder().isSameOrStronger(entityQuery.sortOrder())) {
                        List<EntitySourceQuery<T>> alternateOrder = new ArrayList<>(sortedByEstimatedRows);
                        alternateOrder.remove(sourceQuery);
                        alternateOrder.add(0, sourceQuery);
                        candidateQueryPlans.add(getFinalQueryOperator(entityQuery, alternateOrder, fullData));
                    }
                }
            }
        }
        return candidateQueryPlans;
    }

    <T extends Entity & Comparable<T>> List<EntitySourceQuery<T>> getSources(@NotNull QueryContext context, @NotNull EntityQuery<T> entityQuery) {
        ArrayList<EntitySourceQuery<T>> sources = new ArrayList<>();

        EntityFilter<T> combinedFilter = CombinedFilter.combine(entityQuery.filters());

        Integer startId = null, endId = null;
        T startEntity = null, endEntity = null;

        for (EntityFilter<T> filter : entityQuery.filters()) {
            if (filter instanceof ManualFilter) {
                ManualFilter<T> manualFilter = (ManualFilter<T>) filter;
                QueryOperator<T> entities = new Manual<>(context, Set.copyOf(manualFilter.ids()));
                sources.add(EntitySourceQuery.fromQueryOperator(entities, true, List.of(filter)));
                int minId = manualFilter.minId(), maxId = manualFilter.maxId() + 1; // endId is exclusive
                if (startId == null || minId > startId)
                    startId = minId;
                if (endId == null || maxId < endId)
                    endId = maxId;
            }
            if (filter instanceof EntityIndexFilter) {
                EntityIndexFilter<T> indexFilter = (EntityIndexFilter<T>) filter;
                T p = indexFilter.start();
                if (p != null && (startEntity == null || p.compareTo(startEntity) > 0))
                    startEntity = p;
                p = indexFilter.end();
                if (p != null && (endEntity == null || p.compareTo(endEntity) < 0))
                    endEntity = p;
            }
        }

        sources.add(EntitySourceQuery.fromQueryOperator(new EntityTableScan<>(context, entityQuery.entityType(), combinedFilter, startId, endId), true, entityQuery.filters()));

        boolean reverseNameOrder = entityQuery.sortOrder().isSameOrStronger((QuerySortOrder<T>) QuerySortOrder.byEntityDefaultIndex(entityQuery.entityType(), true));
        sources.add(EntitySourceQuery.fromQueryOperator(new EntityIndexRangeScan<>(context, entityQuery.entityType(), combinedFilter, startEntity, endEntity, reverseNameOrder), true, entityQuery.filters()));

        GameQuery gameQuery = entityQuery.gameQuery();
        if (gameQuery != null) {
            QueryOperator<Game> gameQueryPlan = queryPlanner.selectBestQueryPlan(queryPlanner().getGameQueryPlans(context, gameQuery, true));
            GameEntityJoinCondition joinCondition = entityQuery.joinCondition();
            QueryOperator<T> complexEntityQuery = new Distinct<>(context, new Sort<>(context, new EntityIdsByGames<T>(context, entityQuery.entityType(), gameQueryPlan, joinCondition)));
            sources.add(EntitySourceQuery.fromQueryOperator(complexEntityQuery, false, List.of()));
        }

        return sources;
    }

    <T extends Entity & Comparable<T>> QueryOperator<T> getFinalQueryOperator(@NotNull EntityQuery<T> entityQuery, @NotNull List<EntitySourceQuery<T>> sources, boolean fullData) {
        boolean fullDataRequired = fullData;
        QuerySortOrder<T> sortOrder = entityQuery.sortOrder();
        if (sortOrder.requiresData()) {
            fullDataRequired = true;
        }

        EntitySourceQuery<T> current = sources.get(0);
        for (int i = 1; i < sources.size(); i++) {
            current = EntitySourceQuery.join(current, sources.get(i));
        }

        List<EntityFilter<T>> filtersLeft = new ArrayList<>(entityQuery.filters());
        filtersLeft.removeAll(current.filtersCovered());

        QueryOperator<T> operator = current.operator();
        if (!filtersLeft.isEmpty() || (!operator.hasFullData() && fullDataRequired)) {
            operator = new EntityLookup<>(current.context(), entityQuery.entityType(), operator, CombinedFilter.combine(filtersLeft));
        }

        return operator.sortedAndDistinct(sortOrder, entityQuery.limit());}
}
