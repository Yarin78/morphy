package se.yarin.cbhlib.games.search;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.games.SerializedGameHeaderFilter;

public class GameTypeFilter extends SearchFilterBase implements SerializedGameHeaderFilter {
    private final boolean guidingTexts;

    public GameTypeFilter(Database database, boolean guidingTexts) {
        super(database);
        this.guidingTexts = guidingTexts;
    }

    @Override
    public boolean matches(byte[] serializedGameHeader) {
        boolean isGuidingText = (serializedGameHeader[0] & 2) > 0;
        return isGuidingText == guidingTexts;
    }

    @Override
    public boolean matches(Game game) {
        return game.isGuidingText() == guidingTexts;
    }
}
