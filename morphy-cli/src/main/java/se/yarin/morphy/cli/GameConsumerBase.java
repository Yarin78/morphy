package se.yarin.morphy.cli;

import se.yarin.cbhlib.games.search.GameSearcher;

public abstract class GameConsumerBase implements GameConsumer {
    protected int totalFoundGames = 0;
    protected int totalConsumedGames = 0;
    protected long totalSearchTime = 0;

    @Override
    public void init() {
    }

    @Override
    public void searchDone(GameSearcher.SearchResult result) {
        totalFoundGames += result.getTotalGames();
        totalConsumedGames += result.getConsumedGames();
        totalSearchTime += result.getElapsedTime();
    }
}
