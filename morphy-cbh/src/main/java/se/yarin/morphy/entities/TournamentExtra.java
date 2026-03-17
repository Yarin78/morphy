package se.yarin.morphy.entities;

import org.immutables.value.Value;
import se.yarin.chess.Date;
import se.yarin.morphy.IdObject;

import java.util.List;

@Value.Immutable
public abstract class TournamentExtra implements IdObject {
  @Value.Default
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
    return latitude() == 0.0
        && longitude() == 0.0
        && tiebreakRules().isEmpty()
        && endDate().equals(Date.unset());
  }
}
