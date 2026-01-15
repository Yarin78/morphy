package se.yarin.morphy.cli.games;

import se.yarin.morphy.Game;
import se.yarin.morphy.qqueries.QueryResult;

import java.util.function.Consumer;

public interface GameConsumer extends Consumer<Game> {
  void init();

  void searchDone(QueryResult<Game> searchResult);

  void finish();
}
