package se.yarin.cbhlib.games.search;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;

public abstract class SearchFilterBase implements SearchFilter {

    private final Database database;

    public SearchFilterBase(Database database) {
        this.database = database;
    }

    @Override
    public Database getDatabase() {
        return this.database;
    }

    @Override
    public void initSearch() {
    }

    @Override
    public int countEstimate() {
        return SearchFilter.UNKNOWN_COUNT_ESTIMATE;
    }

    @Override
    public int firstGameId() {
        return 1;
    }

    @Override
    public abstract boolean matches(Game game);
}
