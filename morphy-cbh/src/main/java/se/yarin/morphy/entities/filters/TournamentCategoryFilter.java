package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.storage.ItemStorageFilter;

public class TournamentCategoryFilter implements ItemStorageFilter<Tournament> {
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
}
