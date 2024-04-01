package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Tournament;
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

public class TournamentQueryPlanner extends EntityQueryPlanner<Tournament> {

    public TournamentQueryPlanner(QueryPlanner queryPlanner) {
        super(queryPlanner);
    }

    List<EntitySourceQuery<Tournament>> getTournamentQuerySources(@NotNull QueryContext context, @NotNull TournamentQuery tournamentQuery) {
        ArrayList<EntitySourceQuery<Tournament>> sources = new ArrayList<>();

        EntityFilter<Tournament> combinedFilter = CombinedFilter.combine(tournamentQuery.filters());

        Integer startId = null, endId = null;
        Tournament startEntity = null, endEntity = null;

        for (EntityFilter<Tournament> filter : tournamentQuery.filters()) {
            if (filter instanceof ManualFilter) {
                ManualFilter<Tournament> manualFilter = (ManualFilter<Tournament>) filter;
                QueryOperator<Tournament> tournaments = new Manual<>(context, Set.copyOf(manualFilter.ids()));
                sources.add(EntitySourceQuery.fromQueryOperator(tournaments, true, List.of(filter)));
                int minId = manualFilter.minId(), maxId = manualFilter.maxId() + 1; // endId is exclusive
                if (startId == null || minId > startId)
                    startId = minId;
                if (endId == null || maxId < endId)
                    endId = maxId;
            }
            if (filter instanceof EntityIndexFilter) {
                EntityIndexFilter<Tournament> indexFilter = (EntityIndexFilter<Tournament>) filter;
                Tournament p = indexFilter.start();
                if (p != null && (startEntity == null || p.compareTo(startEntity) > 0))
                    startEntity = p;
                p = indexFilter.end();
                if (p != null && (endEntity == null || p.compareTo(endEntity) < 0))
                    endEntity = p;
            }
        }

        sources.add(EntitySourceQuery.fromQueryOperator(new EntityTableScan<>(context, EntityType.TOURNAMENT, combinedFilter, startId, endId), true, tournamentQuery.filters()));

        boolean reverseNameOrder = tournamentQuery.sortOrder().isSameOrStronger(QuerySortOrder.byTournamentDefaultIndex(true));
        sources.add(EntitySourceQuery.fromQueryOperator(new EntityIndexRangeScan<>(context, EntityType.TOURNAMENT, combinedFilter, startEntity, endEntity, reverseNameOrder), true, tournamentQuery.filters()));

        GameQuery gameQuery = tournamentQuery.gameQuery();
        if (gameQuery != null) {
            QueryOperator<Game> gameQueryPlan = getQueryPlanner().selectBestQueryPlan(getQueryPlanner().getGameQueryPlans(context, gameQuery, true));
            // GamePlayerJoinCondition joinCondition = tournamentQuery.joinCondition();
            // assert joinCondition != null;
            QueryOperator<Tournament> complexPlayerQuery = new Distinct<>(context, new Sort<>(context, new TournamentIdsByGames(context, gameQueryPlan)));
            sources.add(EntitySourceQuery.fromQueryOperator(complexPlayerQuery, false, List.of()));
        }

        return sources;
    }



    public List<QueryOperator<Tournament>> getTournamentQueryPlans(@NotNull QueryContext context, @NotNull TournamentQuery tournamentQuery, boolean fullData) {
        List<QueryOperator<Tournament>> candidateQueryPlans = new ArrayList<>();

        List<EntitySourceQuery<Tournament>> sources = getTournamentQuerySources(context, tournamentQuery);

        for (List<EntitySourceQuery<Tournament>> sourceCombination : getQueryPlanner().sourceCombinations(sources)) {
            // Join the sources starting with the one returning least amount of expected rows
            List<EntitySourceQuery<Tournament>> sortedByEstimatedRows = sourceCombination.stream().sorted(Comparator.comparingLong(EntitySourceQuery::estimateRows)).collect(Collectors.toList());

            candidateQueryPlans.add(getFinalTournamentQueryOperator(tournamentQuery, sortedByEstimatedRows, fullData));

            if (!tournamentQuery.sortOrder().isNone()) {
                // We also want to check if one of the source queries already has the data in the right order.
                // If so, by starting with that one and the applying the other sources in order we might end up with
                // a better result as we don't need the final sort.
                for (int i = 1; i < sortedByEstimatedRows.size(); i++) { // i=0 is already covered above
                    EntitySourceQuery<Tournament> sourceQuery = sortedByEstimatedRows.get(i);
                    if (sourceQuery.operator().sortOrder().isSameOrStronger(tournamentQuery.sortOrder())) {
                        List<EntitySourceQuery<Tournament>> alternateOrder = new ArrayList<>(sortedByEstimatedRows);
                        alternateOrder.remove(sourceQuery);
                        alternateOrder.add(0, sourceQuery);
                        candidateQueryPlans.add(getFinalTournamentQueryOperator(tournamentQuery, alternateOrder, fullData));
                    }
                }
            }
        }

        return candidateQueryPlans;
    }

    private static QueryOperator<Tournament> getFinalTournamentQueryOperator(@NotNull TournamentQuery tournamentQuery, @NotNull List<EntitySourceQuery<Tournament>> sources, boolean fullData) {
        boolean fullDataRequired = fullData;
        QuerySortOrder<Tournament> sortOrder = tournamentQuery.sortOrder();
        if (sortOrder.requiresData()) {
            fullDataRequired = true;
        }

        EntitySourceQuery<Tournament> current = sources.get(0);
        for (int i = 1; i < sources.size(); i++) {
            current = EntitySourceQuery.join(current, sources.get(i));
        }

        List<EntityFilter<Tournament>> filtersLeft = new ArrayList<>(tournamentQuery.filters());
        filtersLeft.removeAll(current.filtersCovered());

        QueryOperator<Tournament> operator = current.operator();
        if (!filtersLeft.isEmpty() || (!operator.hasFullData() && fullDataRequired)) {
            operator = new EntityLookup<>(current.context(), EntityType.TOURNAMENT, operator, CombinedFilter.combine(filtersLeft));
        }

        return operator.sortedAndDistinct(sortOrder, tournamentQuery.limit());
    }

}
