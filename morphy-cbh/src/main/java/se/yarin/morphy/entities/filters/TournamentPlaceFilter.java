package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.storage.ItemStorageFilter;

public class TournamentPlaceFilter implements ItemStorageFilter<Tournament> {

    private final @NotNull String place;
    private final boolean caseSensitive;
    private final boolean exactMatch;

    public TournamentPlaceFilter(@NotNull String place, boolean caseSensitive, boolean exactMatch) {
        this.place = place;
        this.caseSensitive = caseSensitive;
        this.exactMatch = exactMatch;
    }

    private boolean matches(@NotNull String placeName) {
        if (exactMatch) {
            return caseSensitive ? placeName.equals(place) : placeName.equalsIgnoreCase(place);
        }
        return caseSensitive ? placeName.startsWith(place) : placeName.toLowerCase().startsWith(place.toLowerCase());
    }

    @Override
    public boolean matches(@NotNull Tournament tournament) {
        return matches(tournament.place());
    }
}
