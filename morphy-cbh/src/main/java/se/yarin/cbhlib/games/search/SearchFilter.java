package se.yarin.cbhlib.games.search;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.GameLoader;

import java.io.IOException;

/**
 * A game search filter. A filter is always associated with a Database.
 */
public interface SearchFilter {

    int UNKNOWN_COUNT_ESTIMATE = 10000000;

    /**
     * Gets the database the search filter is associated with.
     */
    Database getDatabase();

    /**
     * Called by the {@link GameSearcher} after a filter has been added to a search
     * but before invoking any other methods to the filter.
     * Allows the filter to do some preprocessing if needed; for instance looking up metadata
     * in some database; operations that are not suitable to be done in a constructor.
     * @throws IOException
     */
    void initSearch() throws IOException;

    /**
     * Gets an estimate on how many search hits this filter by itself would return.     *
     */
    int countEstimate();

    /**
     * Gets the id of the first game in the database that could possibly match the filter.
     */
    int firstGameId();

    /**
     * Determines if a specific game in the database matches this filter.
     */
    boolean matches(GameHeader gameHeader) throws IOException;
}
