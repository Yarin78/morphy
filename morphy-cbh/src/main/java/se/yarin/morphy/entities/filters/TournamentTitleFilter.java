package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.storage.ItemStorageFilter;

public class TournamentTitleFilter implements ItemStorageFilter<Tournament> {
    @NotNull
    private final String title;
    private final boolean caseSensitive;
    private final boolean exactMatch;

    public TournamentTitleFilter(@NotNull String title, boolean caseSensitive, boolean exactMatch) {
        this.title = title;
        this.caseSensitive = caseSensitive;
        this.exactMatch = exactMatch;
    }

    @Override
    public boolean matches(@NotNull Tournament tournament) {
        if (exactMatch) {
            return caseSensitive ? tournament.title().equals(title) : tournament.title().equalsIgnoreCase(title);
        }
        return caseSensitive ? tournament.title().startsWith(title) : tournament.title().toLowerCase().startsWith(title.toLowerCase());
    }

    // TODO: matchesSerialized (or not, can we compare serialized strings safely?)
}
