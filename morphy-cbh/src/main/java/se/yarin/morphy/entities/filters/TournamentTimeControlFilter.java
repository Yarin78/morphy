package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.cbhlib.Database;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.TournamentTimeControl;
import se.yarin.morphy.storage.ItemStorageFilter;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TournamentTimeControlFilter implements ItemStorageFilter<Tournament> {

    private final @NotNull Set<TournamentTimeControl> timeControls;

    public TournamentTimeControlFilter(@NotNull String timeControls) {
        List<String> specTimes = Arrays.stream(timeControls.split("\\|")).collect(Collectors.toList());
        this.timeControls = Arrays.stream(TournamentTimeControl.values()).filter(x -> specTimes.contains(x.getName())).collect(Collectors.toSet());
    }

    public TournamentTimeControlFilter(@NotNull Set<TournamentTimeControl> timeControls) {
        this.timeControls = timeControls;
    }

    @Override
    public boolean matches(@NotNull Tournament tournament) {
        return timeControls.contains(tournament.timeControl());
    }
}
