package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.TournamentTimeControl;
import se.yarin.morphy.util.CBUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TournamentTimeControlFilter implements EntityFilter<Tournament> {

  private final @NotNull Set<TournamentTimeControl> timeControls;

  public TournamentTimeControlFilter(@NotNull String timeControls) {
    List<String> specTimes =
        Arrays.stream(timeControls.split("\\|"))
            .map(s -> s.equals("classic") ? "normal" : s)
            .collect(Collectors.toList());
    this.timeControls =
        Arrays.stream(TournamentTimeControl.values())
            .filter(x -> specTimes.contains(x.getName()))
            .collect(Collectors.toSet());
  }

  public TournamentTimeControlFilter(@NotNull Set<TournamentTimeControl> timeControls) {
    this.timeControls = timeControls;
  }

  @Override
  public boolean matches(@NotNull Tournament tournament) {
    return timeControls.contains(tournament.timeControl());
  }

  @Override
  public boolean matchesSerialized(byte[] serializedItem) {
    return timeControls.contains(CBUtil.decodeTournamentTimeControl(serializedItem[74]));
  }

  @Override
  public String toString() {
    if (timeControls.size() == 1) {
      return "timeControl = '" + timeControls.stream().findFirst().get().getName() + "'";
    } else {
      return "timeControl in ("
          + timeControls.stream()
              .map(timeControl -> String.format("'%s'", timeControl.getName()))
              .collect(Collectors.joining(", "))
          + ")";
    }
  }

  @Override
  public EntityType entityType() {
    return EntityType.TOURNAMENT;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TournamentTimeControlFilter that = (TournamentTimeControlFilter) o;
    return timeControls.equals(that.timeControls);
  }

  @Override
  public int hashCode() {
    return timeControls.hashCode();
  }
}
