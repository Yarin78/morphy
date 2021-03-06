package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.TournamentType;
import se.yarin.morphy.util.CBUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TournamentTypeFilter implements EntityFilter<Tournament>  {

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

    @Override
    public boolean matchesSerialized(byte[] serializedItem) {
        return types.contains(CBUtil.decodeTournamentType(serializedItem[74]));
    }

    @Override
    public String toString() {
        if (types.size() == 1) {
            return "type = '" + types.stream().findFirst().get().getName() + "'";
        } else {
            return "type in (" + types.stream()
                    .map(type -> String.format("'%s'", type.getName()))
                    .collect(Collectors.joining(", ")) + ")";
        }
    }

    @Override
    public EntityType entityType() {
        return EntityType.TOURNAMENT;
    }
}
