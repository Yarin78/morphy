package se.yarin.morphy.cli.old.columns;

import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.entities.PlayerEntity;

public class NameColumn implements GameColumn {

    private final boolean isWhite;

    public NameColumn(boolean isWhite) {
        this.isWhite = isWhite;
    }

    @Override
    public String getHeader() {
        return isWhite ? "White" : "  Black";
    }

    @Override
    public int marginRight() {
        return 2;
    }

    @Override
    public String getValue(Game game) {
        if (game.isGuidingText()) {
            return isWhite ? game.getTextTitle() : "";
        }
        PlayerEntity player = isWhite ? game.getWhite() : game.getBlack();
        String name = player.getFullNameShort();
        return isWhite ? name : ("- " + name);
    }

    @Override
    public String getId() {
        return "name";
    }

    @Override
    public int width() {
        return isWhite ? 20 : 22;
    }
}
