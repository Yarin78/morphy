package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.storage.ItemStorageFilter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TournamentNationFilter implements ItemStorageFilter<Tournament> {
    private final @NotNull Set<Nation> nations;

    public TournamentNationFilter(@NotNull String nations) {
        Stream<Nation> nationStream = Arrays.stream(nations.split("\\|")).map(Nation::fromName);
        this.nations = nationStream.collect(Collectors.toCollection(HashSet::new));
    }

    public TournamentNationFilter(@NotNull Set<Nation> nations) {
        this.nations = nations;
    }

    @Override
    public boolean matches(@NotNull Tournament tournament) {
        return nations.contains(tournament.nation());
    }
}
