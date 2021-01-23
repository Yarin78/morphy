package se.yarin.morphy.cli;

import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.cbhlib.games.search.GameSearcher;

public class StatsGameConsumer extends GameConsumerBase {
    private int numFailedModels = 0;

    @Override
    public void init() {

    }

    @Override
    public void finish() {
        System.out.println("# games where a GameModel couldn't be created: " + numFailedModels);

        System.out.printf("%d hits  (%.2f s)%n", totalFoundGames, totalSearchTime / 1000.0);
    }

    @Override
    public void accept(Game game) {
        if (!game.isGuidingText()) {
            try {
                game.getModel();
            } catch (ChessBaseException e) {
                numFailedModels += 1;
            }
        }
    }
}
