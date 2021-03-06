package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Tournament;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class TournamentPlaceFilter implements EntityFilter<Tournament>  {

    private final @NotNull Set<String> places;
    private final boolean caseSensitive;
    private final boolean exactMatch;

    public TournamentPlaceFilter(@NotNull String place, boolean caseSensitive, boolean exactMatch) {
        String[] places = place.split("\\|");
        if (!caseSensitive) {
            this.places = Arrays.stream(places).map(String::toLowerCase).collect(Collectors.toSet());
        } else {
            this.places = Arrays.stream(places).collect(Collectors.toSet());
        }
        this.caseSensitive = caseSensitive;
        this.exactMatch = exactMatch;
    }

    private boolean matches(@NotNull String placeName) {
        String caseCorrectPlaceName = caseSensitive ? placeName : placeName.toLowerCase();
        if (exactMatch) {
            return places.contains(placeName);
        }
        return places.stream().anyMatch(caseCorrectPlaceName::startsWith);
    }

    @Override
    public boolean matches(@NotNull Tournament tournament) {
        return matches(tournament.place());
    }

    @Override
    public String toString() {
        String placeStr = caseSensitive ? "place" : "lower(place)";

        if (exactMatch) {
            if (places.size() == 1) {
                return placeStr + "='" + places.stream().findFirst().get() + "'";
            }
            return placeStr + " in (" + places.stream()
                    .map(place -> String.format("'%s'", place))
                    .collect(Collectors.joining(", ")) + ")";
        } else {
            return "(" + places.stream()
                    .map(place -> String.format("%s like '%s%%'", placeStr, place))
                    .collect(Collectors.joining(" or ")) + ")";
        }
    }

    @Override
    public EntityType entityType() {
        return EntityType.TOURNAMENT;
    }
}
