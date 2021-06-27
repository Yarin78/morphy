package se.yarin.morphy.entities;

import org.immutables.value.Value;
import se.yarin.chess.Date;
import se.yarin.morphy.IdObject;

import java.util.List;

@Value.Immutable
public abstract class TournamentExtra {
    @Value.Default
    public double latitude() {
        return 0.0;
    }

    @Value.Default
    public double longitude() {
        return 0.0;
    }

    @Value.Default
    public List<TiebreakRule> tiebreakRules() {
        return List.of();
    }

    @Value.Default
    public Date endDate() {
        return Date.unset();
    }

    public static TournamentExtra empty() {
        return ImmutableTournamentExtra.builder().build();
    }

    public boolean isEmpty() {
        return this.equals(empty());
    }
}
