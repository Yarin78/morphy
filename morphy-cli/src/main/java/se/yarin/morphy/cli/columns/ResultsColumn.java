package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Game;
import se.yarin.chess.GameResult;

public class ResultsColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "Res";
    }

    @Override
    public String getValue(Game game) {
        String result;
        if (game.isGuidingText()) {
            result = "Txt";
        } else {
            result = game.getResult().toString();
            if (game.getResult() == GameResult.DRAW) {
                result = "½-½";
            } else if (game.getResult() == GameResult.NOT_FINISHED) {
                result = game.getLineEvaluation().toASCIIString();
            }
        }
        return result;
    }

    @Override
    public String getId() {
        return "result";
    }
}
