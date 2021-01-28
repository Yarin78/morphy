package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.util.CBUtil;

public class RawMovesColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "Moves (raw data)";
    }

    @Override
    public String getValue(Game game) {
        long movesOffset = game.getMovesOffset();
        byte[] movesData = game.getDatabase().getMovesBase().getStorage().readBlob(movesOffset).array();
        return CBUtil.toHexString(movesData);
    }

    @Override
    public int marginLeft() {
        return 2;
    }

    @Override
    public boolean trimValueToWidth() {
        return false;
    }

    @Override
    public String getId() {
        return "raw";
    }
}
