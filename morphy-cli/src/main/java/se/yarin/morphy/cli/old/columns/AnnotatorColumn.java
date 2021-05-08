package se.yarin.morphy.cli.old.columns;

import se.yarin.cbhlib.Game;

public class AnnotatorColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "Annotator";
    }

    @Override
    public int width() {
        return 20;
    }

    @Override
    public String getValue(Game game) {
        return game.getAnnotator().getName();
    }

    @Override
    public String getId() {
        return "annotator";
    }
}
