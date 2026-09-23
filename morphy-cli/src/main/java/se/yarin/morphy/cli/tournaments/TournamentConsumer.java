package se.yarin.morphy.cli.tournaments;

import se.yarin.morphy.DatabaseCbh;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.cli.queries.QueryResult;

import java.util.function.Consumer;

public interface TournamentConsumer extends Consumer<Tournament> {
  void setCurrentDatabase(DatabaseCbh database);

  void init();

  void searchDone(QueryResult<Tournament> queryResult);

  void finish();
}
