package se.yarin.morphy.cli;

import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.search.GameSearcher;

public class StatsGameConsumer implements GameConsumer {
    private int numFailedModels = 0;

    @Override
    public void init() {

    }

    @Override
    public void done(GameSearcher.SearchResult result) {
        System.out.println("# games where a GameModel couldn't be created: " + numFailedModels);

        System.out.printf("%d hits  (%.2f s)%n", result.getTotalHits(), result.getElapsedTime() / 1000.0);
    }

    @Override
    public void accept(GameSearcher.Hit hit) {
        GameHeader header = hit.getGameHeader();
        if (!header.isGuidingText()) {
            try {
                hit.getModel();
            } catch (ChessBaseException e) {
                numFailedModels += 1;
            }
        }
    }
}
