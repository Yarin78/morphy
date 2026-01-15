package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.storage.ItemStorageFilter;

import java.util.stream.Stream;

public class QGamesWithFilter extends ItemQuery<Game> {
  private final @NotNull GameFilter filter;

  public QGamesWithFilter(@NotNull GameFilter filter) {
    this.filter = filter;
  }

  @Override
  public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Game game) {
    ItemStorageFilter<GameHeader> headerFilter = filter.gameHeaderFilter();
    ItemStorageFilter<ExtendedGameHeader> extendedHeaderFilter = filter.extendedGameHeaderFilter();
    return (headerFilter == null || headerFilter.matches(game.id(), game.header()))
        && (extendedHeaderFilter == null
            || extendedHeaderFilter.matches(game.id(), game.extendedHeader()));
  }

  @Override
  public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
    return INFINITE;
  }

  @Override
  public @NotNull Stream<Game> stream(@NotNull DatabaseReadTransaction txn) {
    return txn.stream(filter);
  }
}
