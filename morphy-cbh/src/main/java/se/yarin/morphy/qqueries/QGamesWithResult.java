package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.GameResult;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.games.filters.ResultsFilter;

import java.util.stream.Stream;

public class QGamesWithResult extends ItemQuery<Game> {
  private final @NotNull ResultsFilter filter;

  public QGamesWithResult(@NotNull String result) {
    this(new ResultsFilter(result));
  }

  public QGamesWithResult(@NotNull ResultsFilter filter) {
    this.filter = filter;
  }

  @Override
  public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Game game) {
    return filter.matches(game.id(), game.header());
  }

  @Override
  public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
    return INFINITE;
  }

  @Override
  public @NotNull Stream<Game> stream(@NotNull DatabaseReadTransaction txn) {
    return txn.stream(GameFilter.of(filter, null));
  }
}
