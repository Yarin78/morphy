package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameResult;

public class ResultsColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "Res";
    }

    @Override
    public String getValue(Database database, GameHeader header, GameModel game) {
        String result;
        if (header.isGuidingText()) {
            result = "Txt";
        } else {
            result = header.getResult().toString();
            if (header.getResult() == GameResult.DRAW) {
                result = "½-½";
            } else if (header.getResult() == GameResult.NOT_FINISHED) {
                result = header.getLineEvaluation().toASCIIString();
            }
        }
        return result;
    }

    @Override
    public String getId() {
        return "result";
    }
}
