package se.yarin.cbhlib.games.search;

import lombok.Getter;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;
import se.yarin.cbhlib.games.GameHeader;

import java.util.Set;

public class TournamentPlaceFilter extends SearchFilterBase {
    @Getter
    private final Set<String> places;

    public TournamentPlaceFilter(Database database, String place) {
        super(database);

        places = Set.of(place.split("\\|"));
    }

    @Override
    public boolean matches(GameHeader gameHeader) {
        TournamentEntity tournament = getDatabase().getTournamentBase().get(gameHeader.getTournamentId());
        return places.stream().anyMatch(tournament.getPlace()::startsWith);
    }
}
