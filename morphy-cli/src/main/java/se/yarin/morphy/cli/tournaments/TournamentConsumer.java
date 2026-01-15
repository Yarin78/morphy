package se.yarin.morphy.cli.tournaments;

import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.qqueries.QueryResult;

import java.util.function.Consumer;

public interface TournamentConsumer extends Consumer<Tournament> {
  void setCurrentDatabase(Database database);

  void init();

  void searchDone(QueryResult<Tournament> queryResult);

  void finish();
}
