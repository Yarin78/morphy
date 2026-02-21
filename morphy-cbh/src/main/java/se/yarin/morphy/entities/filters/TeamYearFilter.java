package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Team;

public class TeamYearFilter implements EntityFilter<Team> {
  private final int minYear;
  private final int maxYear;

  public TeamYearFilter(int minYear, int maxYear) {
    this.minYear = minYear;
    this.maxYear = maxYear;
  }

  @Override
  public boolean matches(@NotNull Team team) {
    return team.year() >= minYear && team.year() <= maxYear;
  }

  @Override
  public String toString() {
    if (minYear == maxYear) {
      return "year = " + minYear;
    } else if (minYear == 0) {
      return "year <= " + maxYear;
    } else if (maxYear == 9999) {
      return "year >= " + minYear;
    } else {
      return "year >= " + minYear + " and year <= " + maxYear;
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
    TeamYearFilter that = (TeamYearFilter) o;
    return minYear == that.minYear && maxYear == that.maxYear;
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(minYear, maxYear);
  }
}
