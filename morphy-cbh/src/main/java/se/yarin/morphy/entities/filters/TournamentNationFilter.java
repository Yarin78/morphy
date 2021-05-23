package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.entities.Tournament;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

public class TournamentNationFilter implements EntityFilter<Tournament>  {
    private final boolean[] nations;

    public TournamentNationFilter(@NotNull String nations) {
        this.nations = new boolean[256];
        Stream<Nation> nationStream = Arrays.stream(nations.split("\\|")).map(Nation::fromName);
        nationStream.forEach(nation -> this.nations[nation.ordinal()] = true);
    }

    public TournamentNationFilter(@NotNull Set<Nation> nations) {
        this.nations = new boolean[256];
        nations.forEach(nation -> this.nations[nation.ordinal()] = true);
    }

    @Override
    public boolean matches(@NotNull Tournament tournament) {
        return nations[tournament.nation().ordinal()];
    }

    @Override
    public boolean matchesSerialized(byte[] serializedItem) {
        return nations[serializedItem[76] & 0xFF];
    }
}
