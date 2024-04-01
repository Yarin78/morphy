package se.yarin.morphy.queries;


import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
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

public class PlayerQueryPlanner extends EntityQueryPlanner<Player> {

    public PlayerQueryPlanner(QueryPlanner queryPlanner) {
        super(queryPlanner);
    }

    List<EntitySourceQuery<Player>> getPlayerQuerySources(@NotNull QueryContext context, @NotNull PlayerQuery playerQuery) {
        ArrayList<EntitySourceQuery<Player>> sources = new ArrayList<>();

        EntityFilter<Player> combinedFilter = CombinedFilter.combine(playerQuery.filters());

        Integer startId = null, endId = null;
        Player startEntity = null, endEntity = null;

        for (EntityFilter<Player> filter : playerQuery.filters()) {
            if (filter instanceof ManualFilter) {
                ManualFilter<Player> manualFilter = (ManualFilter<Player>) filter;
                QueryOperator<Player> players = new Manual<>(context, Set.copyOf(manualFilter.ids()));
                sources.add(EntitySourceQuery.fromQueryOperator(players, true, List.of(filter)));
                int minId = manualFilter.minId(), maxId = manualFilter.maxId() + 1; // endId is exclusive
                if (startId == null || minId > startId)
                    startId = minId;
                if (endId == null || maxId < endId)
                    endId = maxId;
            }
            if (filter instanceof EntityIndexFilter) {
                EntityIndexFilter<Player> indexFilter = (EntityIndexFilter<Player>) filter;
                Player p = indexFilter.start();
                if (p != null && (startEntity == null || p.compareTo(startEntity) > 0))
                    startEntity = p;
                p = indexFilter.end();
                if (p != null && (endEntity == null || p.compareTo(endEntity) < 0))
                    endEntity = p;
            }
        }

        sources.add(EntitySourceQuery.fromQueryOperator(new EntityTableScan<>(context, EntityType.PLAYER, combinedFilter, startId, endId), true, playerQuery.filters()));

        boolean reverseNameOrder = playerQuery.sortOrder().isSameOrStronger(QuerySortOrder.byPlayerDefaultIndex(true));
        sources.add(EntitySourceQuery.fromQueryOperator(new EntityIndexRangeScan<>(context, EntityType.PLAYER, combinedFilter, startEntity, endEntity, reverseNameOrder), true, playerQuery.filters()));

        GameQuery gameQuery = playerQuery.gameQuery();
        if (gameQuery != null) {
            QueryOperator<Game> gameQueryPlan = QueryPlanner.selectBestQueryPlan(getQueryPlanner().getGameQueryPlans(context, gameQuery, true));
            GamePlayerJoinCondition joinCondition = playerQuery.joinCondition();
            assert joinCondition != null;
            QueryOperator<Player> complexPlayerQuery = new Distinct<>(context, new Sort<>(context, new PlayerIdsByGames(context, gameQueryPlan, joinCondition)));
            sources.add(EntitySourceQuery.fromQueryOperator(complexPlayerQuery, false, List.of()));
        }

        return sources;
    }


    public List<QueryOperator<Player>> getPlayerQueryPlans(@NotNull QueryContext context, @NotNull PlayerQuery playerQuery, boolean fullData) {
        List<QueryOperator<Player>> candidateQueryPlans = new ArrayList<>();

        List<EntitySourceQuery<Player>> sources = getPlayerQuerySources(context, playerQuery);

        for (List<EntitySourceQuery<Player>> sourceCombination : getQueryPlanner().sourceCombinations(sources)) {
            // Join the sources starting with the one returning least amount of expected rows
            List<EntitySourceQuery<Player>> sortedByEstimatedRows = sourceCombination.stream().sorted(Comparator.comparingLong(EntitySourceQuery::estimateRows)).collect(Collectors.toList());

            candidateQueryPlans.add(getFinalPlayerQueryOperator(playerQuery, sortedByEstimatedRows, fullData));

            if (!playerQuery.sortOrder().isNone()) {
                // We also want to check if one of the source queries already has the data in the right order.
                // If so, by starting with that one and the applying the other sources in order we might end up with
                // a better result as we don't need the final sort.
                for (int i = 1; i < sortedByEstimatedRows.size(); i++) { // i=0 is already covered above
                    EntitySourceQuery<Player> sourceQuery = sortedByEstimatedRows.get(i);
                    if (sourceQuery.operator().sortOrder().isSameOrStronger(playerQuery.sortOrder())) {
                        List<EntitySourceQuery<Player>> alternateOrder = new ArrayList<>(sortedByEstimatedRows);
                        alternateOrder.remove(sourceQuery);
                        alternateOrder.add(0, sourceQuery);
                        candidateQueryPlans.add(getFinalPlayerQueryOperator(playerQuery, alternateOrder, fullData));
                    }
                }
            }
        }

        return candidateQueryPlans;
    }

    private static QueryOperator<Player> getFinalPlayerQueryOperator(@NotNull PlayerQuery playerQuery, @NotNull List<EntitySourceQuery<Player>> sources, boolean fullData) {
        boolean fullDataRequired = fullData;
        QuerySortOrder<Player> sortOrder = playerQuery.sortOrder();
        if (sortOrder.requiresData()) {
            fullDataRequired = true;
        }

        EntitySourceQuery<Player> current = sources.get(0);
        for (int i = 1; i < sources.size(); i++) {
            current = EntitySourceQuery.join(current, sources.get(i));
        }

        List<EntityFilter<Player>> filtersLeft = new ArrayList<>(playerQuery.filters());
        filtersLeft.removeAll(current.filtersCovered());

        QueryOperator<Player> playerOperator = current.operator();
        if (!filtersLeft.isEmpty() || (!playerOperator.hasFullData() && fullDataRequired)) {
            playerOperator = new EntityLookup<>(current.context(), EntityType.PLAYER, playerOperator, CombinedFilter.combine(filtersLeft));
        }

        return playerOperator.sortedAndDistinct(sortOrder, playerQuery.limit());
    }
}
