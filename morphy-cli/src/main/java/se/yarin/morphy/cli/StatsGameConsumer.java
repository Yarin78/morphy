package se.yarin.morphy.cli;

import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.search.GameSearcher;

public class StatsGameConsumer implements GameConsumer {
    private int numEmptyFlags = 0;

    @Override
    public void init() {

    }

    @Override
    public void done(GameSearcher.SearchResult result) {
        System.out.println("# games with empty flags: " + numEmptyFlags);

        System.out.printf("%d hits  (%.2f s)%n", result.getTotalHits(), result.getElapsedTime() / 1000.0);
    }

    @Override
    public void accept(GameSearcher.Hit hit) {
        GameHeader header = hit.getGameHeader();
        if (header.getFlags().isEmpty()) {
            numEmptyFlags += 1;
        }
    }
}
