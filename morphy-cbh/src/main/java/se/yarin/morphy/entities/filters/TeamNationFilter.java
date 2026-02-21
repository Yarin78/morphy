package se.yarin.morphy.entities.filters;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.entities.Team;

public class TeamNationFilter implements EntityFilter<Team> {
  private final boolean[] nations;
  private final Set<Nation> nationSet;

  public TeamNationFilter(@NotNull String nations) {
    this.nations = new boolean[256];
    nationSet =
        Arrays.stream(nations.split("\\|")).map(Nation::fromName).collect(Collectors.toSet());
    nationSet.forEach(nation -> this.nations[nation.ordinal()] = true);
  }

  public TeamNationFilter(@NotNull Set<Nation> nations) {
    this.nations = new boolean[256];
    nationSet = Set.copyOf(nations);
    nations.forEach(nation -> this.nations[nation.ordinal()] = true);
  }

  @Override
  public boolean matches(@NotNull Team team) {
    return nations[team.nation().ordinal()];
  }

  @Override
  public String toString() {
    if (nationSet.size() == 1) {
      return "nation = '" + nationSet.stream().findFirst().get().getIocCode() + "'";
    } else {
      return "nation in ("
          + nationSet.stream()
              .map(nation -> String.format("'%s'", nation.getIocCode()))
              .collect(Collectors.joining(", "))
          + ")";
    }
  }

  @Override
  public EntityType entityType() {
    return EntityType.TEAM;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TeamNationFilter that = (TeamNationFilter) o;
    return nationSet.equals(that.nationSet);
  }

  @Override
  public int hashCode() {
    return nationSet.hashCode();
  }
}
