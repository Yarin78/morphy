package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Tournament;

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
}
