package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Team;

public class TeamNumberFilter implements EntityFilter<Team> {
  private final int minNumber;
  private final int maxNumber;

  public TeamNumberFilter(int minNumber, int maxNumber) {
    this.minNumber = minNumber;
    this.maxNumber = maxNumber;
  }

  @Override
  public boolean matches(@NotNull Team team) {
    return team.teamNumber() >= minNumber && team.teamNumber() <= maxNumber;
  }

  @Override
  public String toString() {
    if (minNumber == maxNumber) {
      return "teamNumber = " + minNumber;
    } else if (minNumber == 0) {
      return "teamNumber <= " + maxNumber;
    } else if (maxNumber == Integer.MAX_VALUE) {
      return "teamNumber >= " + minNumber;
    } else {
      return "teamNumber >= " + minNumber + " and teamNumber <= " + maxNumber;
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
    TeamNumberFilter that = (TeamNumberFilter) o;
    return minNumber == that.minNumber && maxNumber == that.maxNumber;
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(minNumber, maxNumber);
  }
}
