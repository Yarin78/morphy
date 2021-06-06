package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.queries.QueryPlanner;

public class TournamentCategoryFilter implements EntityFilter<Tournament> {
    private final int minCategory;
    private final int maxCategory;

    public TournamentCategoryFilter(int minCategory, int maxCategory) {
        this.minCategory = minCategory;
        this.maxCategory = maxCategory;
    }

    @Override
    public boolean matches(@NotNull Tournament tournament) {
        return tournament.category() >= minCategory && tournament.category() <= maxCategory;
    }

    @Override
    public boolean matchesSerialized(byte[] serializedItem) {
        int category = serializedItem[78];
        return category >= minCategory && category <= maxCategory;
    }

    @Override
    public double expectedMatch(@NotNull QueryPlanner planner) {
        return planner.tournamentCategoryDistribution().ratioBetween(minCategory, maxCategory);
    }

    @Override
    public String toString() {
        if (minCategory == 0) {
            return "category <= " + maxCategory;
        } else {
            return "category >= " + minCategory + " and category <= " + maxCategory;
        }
    }
}
