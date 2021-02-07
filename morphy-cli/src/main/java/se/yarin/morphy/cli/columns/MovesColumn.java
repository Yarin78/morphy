package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.chess.GameModel;

public class MovesColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "Moves";
    }

    @Override
    public String getValue(Game game) {
        try {
            GameModel model = game.getModel();
            return model.moves().toString();
        } catch (ChessBaseException e) {
            return "<critical error>";
        }
    }

    @Override
    public boolean trimValueToWidth() {
        return false;
    }

    @Override
    public String getId() {
        return "moves";
    }
}
