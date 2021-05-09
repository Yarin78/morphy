package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Game;
import se.yarin.morphy.entities.GameTag;

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
        GameTag tag = game.gameTag();
        return tag == null ? "" : tag.englishTitle();
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
