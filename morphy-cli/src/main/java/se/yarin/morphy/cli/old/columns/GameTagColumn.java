package se.yarin.morphy.cli.old.columns;

import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.entities.GameTagEntity;

public class GameTagColumn implements GameColumn {

    @Override
    public String getHeader() {
        return "Game Tag";
    }

    @Override
    public int marginRight() {
        return 2;
    }

    @Override
    public String getValue(Game game) {
        GameTagEntity tag = game.getGameTag();
        return tag == null ? "" : tag.getEnglishTitle();
    }

    @Override
    public String getId() {
        return "tag";
    }

    @Override
    public int width() {
        return 20;
    }
}
