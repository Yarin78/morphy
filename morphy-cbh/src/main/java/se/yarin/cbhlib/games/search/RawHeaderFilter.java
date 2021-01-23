package se.yarin.cbhlib.games.search;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.games.SerializedGameHeaderFilter;

public class RawHeaderFilter extends SearchFilterBase implements SerializedGameHeaderFilter {
    public RawHeaderFilter(Database database) {
        super(database);
    }

    @Override
    public boolean matches(byte[] serializedGameHeader) {
        return (serializedGameHeader[0] & 2) > 0;
    }

    @Override
    public boolean matches(Game game) {
        // The raw filter is a bit special as the filter can only happen when scanning a range of games
        // It means that it will not work when looking at individual games
        return true;
    }
}
