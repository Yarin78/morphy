package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.TournamentType;
import se.yarin.morphy.storage.ItemStorageFilter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TournamentTypeFilter implements ItemStorageFilter<Tournament> {

    private final @NotNull Set<TournamentType> types;

    public TournamentTypeFilter(@NotNull Set<TournamentType> types) {
        this.types = new HashSet<>(types);
    }

    public TournamentTypeFilter(@NotNull String tournamentType) {
        List<String> specTypes = Arrays.stream(tournamentType.split("\\|")).collect(Collectors.toList());
        types = Arrays.stream(TournamentType.values()).filter(x -> specTypes.contains(x.getName())).collect(Collectors.toSet());
    }

    @Override
    public boolean matches(@NotNull Tournament tournament) {
        return types.contains(tournament.type());
    }
}
