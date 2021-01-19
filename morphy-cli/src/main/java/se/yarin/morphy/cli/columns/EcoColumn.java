package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Game;

public class EcoColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "ECO";
    }

    @Override
    public String getValue(Game game) {
        String eco;
        if (game.isGuidingText()) {
            eco = "";
        } else {
            eco = game.getEco().toString().substring(0, 3);
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
