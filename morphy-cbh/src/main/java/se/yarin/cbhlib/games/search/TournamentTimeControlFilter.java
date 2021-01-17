package se.yarin.cbhlib.games.search;

import lombok.Getter;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;
import se.yarin.cbhlib.entities.TournamentTimeControl;
import se.yarin.cbhlib.games.GameHeader;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TournamentTimeControlFilter extends SearchFilterBase {
    @Getter
    private final Set<TournamentTimeControl> timeControls;

    public TournamentTimeControlFilter(Database database, String tournamentTimeControls) {
        super(database);

        List<String> specTimes = Arrays.stream(tournamentTimeControls.split("\\|")).collect(Collectors.toList());
        timeControls = Arrays.stream(TournamentTimeControl.values()).filter(x -> specTimes.contains(x.getName())).collect(Collectors.toSet());
    }

    @Override
    public boolean matches(GameHeader gameHeader) {
        TournamentEntity tournament = getDatabase().getTournamentBase().get(gameHeader.getTournamentId());
        return timeControls.contains(tournament.getTimeControl());
    }
}
