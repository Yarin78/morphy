package se.yarin.cbhlib.games.search;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.GameHeader;

import java.io.IOException;

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
    public void initSearch() throws IOException {
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
    public abstract boolean matches(GameHeader gameHeader) throws IOException;
}
