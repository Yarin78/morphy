package se.yarin.morphy.cli.games;

import se.yarin.morphy.Game;
import se.yarin.morphy.queries.QueryResult;

public abstract class GameConsumerBase implements GameConsumer {
    protected int totalFoundGames = 0;
    protected int totalConsumedGames = 0;
    protected long totalSearchTime = 0;

    @Override
    public void init() {
    }

    @Override
    public void searchDone(QueryResult<Game> result) {
        totalFoundGames += result.total();
        totalConsumedGames += result.consumed();
        totalSearchTime += result.elapsedTime();
    }
}
