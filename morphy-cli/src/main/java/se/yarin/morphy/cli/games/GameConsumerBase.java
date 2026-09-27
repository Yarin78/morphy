package se.yarin.morphy.cli.games;

public abstract class GameConsumerBase implements GameConsumer {
  protected long totalFoundGames = 0;
  protected long totalConsumedGames = 0;
  protected long totalSearchTime = 0;

  @Override
  public void init() {}

  @Override
  public void searchDone(long total, long consumed, long elapsedMillis) {
    totalFoundGames += total;
    totalConsumedGames += consumed;
    totalSearchTime += elapsedMillis;
  }
}
