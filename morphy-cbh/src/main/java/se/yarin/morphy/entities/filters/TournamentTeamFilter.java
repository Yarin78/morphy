package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Tournament;

public class TournamentTeamFilter implements EntityFilter<Tournament> {
  private final boolean teamTournament;

  public TournamentTeamFilter() {
    this(true);
  }

  public TournamentTeamFilter(boolean teamTournament) {
    this.teamTournament = teamTournament;
  }

  @Override
  public boolean matches(@NotNull Tournament tournament) {
    return tournament.teamTournament() == teamTournament;
  }

  @Override
  public boolean matchesSerialized(byte[] serializedItem) {
    return ((serializedItem[75] & 1) == 1) == teamTournament;
  }

  @Override
  public String toString() {
    return teamTournament ? "isTeam" : "!isTeam";
  }

  @Override
  public EntityType entityType() {
    return EntityType.TOURNAMENT;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TournamentTeamFilter that = (TournamentTeamFilter) o;
    return teamTournament == that.teamTournament;
  }

  @Override
  public int hashCode() {
    return Boolean.hashCode(teamTournament);
  }
}
