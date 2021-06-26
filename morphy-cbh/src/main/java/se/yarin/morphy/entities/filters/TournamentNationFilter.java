package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.entities.Tournament;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class TournamentNationFilter implements EntityFilter<Tournament>  {
    private final boolean[] nations;
    private final Set<Nation> nationSet;

    public TournamentNationFilter(@NotNull String nations) {
        this.nations = new boolean[256];
        nationSet = Arrays.stream(nations.split("\\|")).map(Nation::fromName).collect(Collectors.toSet());
        nationSet.forEach(nation -> this.nations[nation.ordinal()] = true);
    }

    public TournamentNationFilter(@NotNull Set<Nation> nations) {
        this.nations = new boolean[256];
        nationSet = Set.copyOf(nations);
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

    @Override
    public String toString() {
        if (nationSet.size() == 1) {
            return "nation = '" + nationSet.stream().findFirst().get().getIocCode() + "'";
        } else {
            return "nation in (" + nationSet.stream()
                    .map(nation -> String.format("'%s'", nation.getIocCode()))
                    .collect(Collectors.joining(", ")) + ")";
        }
    }

    @Override
    public EntityType entityType() {
        return EntityType.TOURNAMENT;
    }
}
