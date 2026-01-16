package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.Team;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.games.filters.TeamFilter;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QGamesByTeams extends ItemQuery<Game> {
  private final @NotNull ItemQuery<Team> teamQuery;
  private @Nullable List<Team> teamResult;
  private @Nullable TeamFilter teamFilter;

  public QGamesByTeams(@NotNull ItemQuery<Team> teamQuery) {
    this.teamQuery = teamQuery;
  }

  @Override
  public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Game game) {
    evaluateSubQuery(txn);
    assert teamFilter != null;
    return teamFilter.matches(game.id(), game.extendedHeader());
  }

  public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
    if (teamResult == null) {
      return INFINITE;
    }
    return teamResult.stream().mapToInt(Entity::count).sum();
  }

  public void evaluateSubQuery(@NotNull DatabaseReadTransaction txn) {
    if (teamResult == null) {
      teamResult = teamQuery.stream(txn).collect(Collectors.toList());
      teamFilter = new TeamFilter(teamResult, null);
    }
  }

  @Override
  public @NotNull Stream<Game> stream(@NotNull DatabaseReadTransaction txn) {
    evaluateSubQuery(txn);
    assert teamFilter != null;
    return txn.stream(GameFilter.of(null, teamFilter));
  }
}
