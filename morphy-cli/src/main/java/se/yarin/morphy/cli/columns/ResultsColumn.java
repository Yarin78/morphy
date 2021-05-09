package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Game;
import se.yarin.chess.GameResult;

public class ResultsColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "Res";
    }

    @Override
    public String getValue(Game game) {
        String result;
        if (game.guidingText()) {
            result = "Txt";
        } else {
            result = game.result().toString();
            if (game.result() == GameResult.DRAW) {
                result = "½-½";
            } else if (game.result() == GameResult.NOT_FINISHED) {
                result = game.lineEvaluation().toASCIIString();
            }
        }
        return result;
    }

    @Override
    public String getId() {
        return "result";
    }
}
