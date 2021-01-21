package se.yarin.cbhlib.games.search;

import lombok.Getter;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.chess.GameResult;

public class ResultsFilter extends SearchFilterBase implements SearchFilter {
    @Getter
    private GameResult result;

    public ResultsFilter(Database db, String results) {
        super(db);
        switch (results.toLowerCase()) {
            case "1-0" :
            case "white":
                result = GameResult.WHITE_WINS;
                break;
            case "i-o":
                result = GameResult.WHITE_WINS_ON_FORFEIT;
                break;
            case "0-1":
            case "black":
                result = GameResult.BLACK_WINS;
            case "o-i":
                result = GameResult.BLACK_WINS_ON_FORFEIT;
                break;
            case "draw":
                result = GameResult.DRAW;
                break;
            case "0-0":
            case "o-o":
                result = GameResult.BOTH_LOST;
                break;
            default:
                throw new IllegalArgumentException("Invalid result: " + results);
        }
    }

    @Override
    public boolean matches(Game game) {
        if (game.isGuidingText()) {
            return false;
        }
        return result.equals(game.getResult());
    }
}
