package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.TournamentTimeControl;
import se.yarin.morphy.storage.ItemStorageFilter;

import java.util.Set;

public class TournamentTimeControlFilter implements ItemStorageFilter<Tournament> {

    private final @NotNull Set<TournamentTimeControl> timeControls;

    public TournamentTimeControlFilter(@NotNull Set<TournamentTimeControl> timeControls) {
        this.timeControls = timeControls;
    }

    @Override
    public boolean matches(@NotNull Tournament tournament) {
        return timeControls.contains(tournament.timeControl());
    }
}
