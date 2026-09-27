package se.yarin.morphy.cli.games;

import se.yarin.morphy.model.GameDto;

public class StatsGameConsumer extends GameConsumerBase {
  @Override
  public void finish() {
    System.out.printf("%d hits  (%.2f s)%n", totalFoundGames, totalSearchTime / 1000.0);
  }

  @Override
  public void accept(GameDto game) {}
}
