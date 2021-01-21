package se.yarin.cbhlib.games.search;

import lombok.Getter;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.entities.TournamentEntity;

import java.util.Set;

public class TournamentPlaceFilter extends SearchFilterBase {
    @Getter
    private final Set<String> places;

    public TournamentPlaceFilter(Database database, String place) {
        super(database);

        places = Set.of(place.split("\\|"));
    }

    @Override
    public boolean matches(Game game) {
        TournamentEntity tournament = game.getTournament();
        return places.stream().anyMatch(tournament.getPlace()::startsWith);
    }
}
