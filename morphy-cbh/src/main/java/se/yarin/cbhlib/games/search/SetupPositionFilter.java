package se.yarin.cbhlib.games.search;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.chess.GameModel;

public class SetupPositionFilter extends SearchFilterBase {
    private final boolean withSetupPosition;

    public SetupPositionFilter(Database database, boolean withSetupPosition) {
        super(database);
        this.withSetupPosition = withSetupPosition;
    }

    @Override
    public boolean matches(Game game) {
        if (game.isGuidingText()) {
            return false;
        }

        try {
            GameModel model = game.getModel();
            return model.moves().isSetupPosition() == withSetupPosition;
        } catch (ChessBaseException e) {
            return false;
        }
    }
}
