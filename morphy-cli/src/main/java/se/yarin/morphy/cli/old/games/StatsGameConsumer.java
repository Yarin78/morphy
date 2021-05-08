package se.yarin.morphy.cli.old.games;

import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.exceptions.ChessBaseException;

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
