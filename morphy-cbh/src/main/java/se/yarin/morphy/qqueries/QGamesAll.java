package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;

import java.util.stream.Stream;

public class QGamesAll extends ItemQuery<Game> {
  @Override
  public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Game item) {
    return true;
  }

  @Override
  public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
    return INFINITE;
  }

  @Override
  public @NotNull Stream<Game> stream(@NotNull DatabaseReadTransaction txn) {
    return txn.stream();
  }
}
