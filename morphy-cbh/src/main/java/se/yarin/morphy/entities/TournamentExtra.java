package se.yarin.morphy.entities;

import org.immutables.value.Value;
import se.yarin.chess.Date;
import se.yarin.morphy.IdObject;

import java.util.List;

@Value.Immutable
public abstract class TournamentExtra implements IdObject {
  // Auxiliary: excluded from equals/hashCode so content-only comparisons (e.g. against
  // TournamentExtra.empty()) keep working regardless of which record the id came from.
  @Value.Default
  @Value.Auxiliary
  public int id() {
    return -1;
  }

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
    return empty(-1);
  }

  public static TournamentExtra empty(int id) {
    return ImmutableTournamentExtra.builder().id(id).build();
  }

  public boolean isEmpty() {
    // id is @Value.Auxiliary (excluded from equals), so this only compares content.
    return this.equals(empty());
  }
}
