package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.GameModel;

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
    public String getValue(Database database, GameHeader header, GameModel game) {
        int annotatorId = header.getAnnotatorId();
        if (annotatorId <= 0) {
            return "";
        }
        return database.getAnnotatorBase().get(annotatorId).getName();
    }

    @Override
    public String getId() {
        return "annotator";
    }
}
