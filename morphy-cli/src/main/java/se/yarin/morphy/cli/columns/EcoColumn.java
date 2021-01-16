package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.GameModel;

public class EcoColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "ECO";
    }

    @Override
    public String getValue(Database database, GameHeader header, GameModel game) {
        String eco;
        if (header.isGuidingText()) {
            eco = "";
        } else {
            eco = header.getEco().toString().substring(0, 3);
            if (eco.equals("???")) {
                eco = "";
            }
        }
        return eco;
    }

    @Override
    public String getId() {
        return "eco";
    }
}
