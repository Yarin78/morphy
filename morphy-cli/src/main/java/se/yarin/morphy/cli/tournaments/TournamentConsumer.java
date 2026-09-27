package se.yarin.morphy.cli.tournaments;

import se.yarin.morphy.api.Database;
import se.yarin.morphy.model.TournamentDto;

import java.util.function.Consumer;

public interface TournamentConsumer extends Consumer<TournamentDto> {
  default void setCurrentDatabase(Database database) {}

  void init();

  void searchDone(long total, long consumed, long elapsedMillis);

  void finish();
}
