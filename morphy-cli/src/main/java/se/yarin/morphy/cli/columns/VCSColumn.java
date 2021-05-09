package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Game;

public class VCSColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "VCS";
    }

    @Override
    public String getValue(Game game) {
        int v = game.variationsMagnitude(), c = game.commentariesMagnitude(), s = game.symbolsMagnitude();
        String vMag = " vV", cMag = " cC", sMag = " sS";
        return String.format("%c%c%c", vMag.charAt(Math.max(0, v)), cMag.charAt(Math.max(0, c)), sMag.charAt(Math.max(0, s)));
    }

    @Override
    public String getId() {
        return "vcs";
    }
}
