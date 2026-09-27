package se.yarin.morphy.cli.games;

import se.yarin.morphy.api.Database;
import se.yarin.morphy.model.GameDto;

import java.util.function.Consumer;

public interface GameConsumer extends Consumer<GameDto> {
  default void setCurrentDatabase(Database database) {}

  /** Whether this consumer needs the moves materialised (as PGN) in each game. */
  default boolean needsMoves() {
    return false;
  }

  void init();

  void searchDone(long total, long consumed, long elapsedMillis);

  void finish();
}
