package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.TournamentType;
import se.yarin.morphy.storage.ItemStorageFilter;

import java.util.HashSet;
import java.util.Set;

public class TournamentTypeFilter implements ItemStorageFilter<Tournament> {

    private final @NotNull Set<TournamentType> types;

    public TournamentTypeFilter(@NotNull Set<TournamentType> types) {
        this.types = new HashSet<>(types);
    }

    @Override
    public boolean matches(@NotNull Tournament tournament) {
        return types.contains(tournament.type());
    }
}
