package se.yarin.morphy.cli.games;

import se.yarin.morphy.Game;
import se.yarin.morphy.exceptions.MorphyException;

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
        if (!game.guidingText()) {
            try {
                game.getModel();
            } catch (MorphyException e) {
                numFailedModels += 1;
            }
        }
    }
}
