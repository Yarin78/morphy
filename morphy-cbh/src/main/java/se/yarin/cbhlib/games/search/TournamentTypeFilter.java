package se.yarin.cbhlib.games.search;

import lombok.Getter;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;
import se.yarin.cbhlib.entities.TournamentType;
import se.yarin.cbhlib.games.GameHeader;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TournamentTypeFilter extends SearchFilterBase {

    @Getter
    private final Set<TournamentType> types;

    public TournamentTypeFilter(Database database, String tournamentType) {
        super(database);

        List<String> specTypes = Arrays.stream(tournamentType.split("\\|")).collect(Collectors.toList());
        types = Arrays.stream(TournamentType.values()).filter(x -> specTypes.contains(x.getName())).collect(Collectors.toSet());
    }

    @Override
    public boolean matches(GameHeader gameHeader) {
        TournamentEntity tournament = getDatabase().getTournamentBase().get(gameHeader.getTournamentId());
        return types.contains(tournament.getType());
    }
}
