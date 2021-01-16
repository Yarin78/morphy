package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.GameModel;

public class VCSColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "VCS";
    }

    @Override
    public String getValue(Database database, GameHeader header, GameModel game) {
        int v = header.getVariationsMagnitude(), c = header.getCommentariesMagnitude(), s = header.getSymbolsMagnitude();
        String vMag = " vV", cMag = " cC", sMag = " sS";
        return String.format("%c%c%c", vMag.charAt(Math.max(0, v)), cMag.charAt(Math.max(0, c)), sMag.charAt(Math.max(0, s)));
    }

    @Override
    public String getId() {
        return "vcs";
    }
}
