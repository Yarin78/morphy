package se.yarin.cbhlib.games.search;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.ResourceLoader;
import se.yarin.cbhlib.entities.PlayerSearcher;
import se.yarin.cbhlib.games.GameHeader;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlayerFilterTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private Database database;

    @Before
    public void setupEntityTest() throws IOException {
        File file = ResourceLoader.materializeDatabaseStream(
                GameHeader.class,
                "World-ch/World-ch",
                folder.newFolder("World-ch"),
                "World-ch");
        // Can't openInMemory because the serializedFilter tests only works on persistent storage
        database = Database.open(file);
    }

    @Test
    public void testCaseSensitivePrefixSearch() {
        PlayerSearcher carlsen = new PlayerSearcher(database.getPlayerBase(), "Carlsen", true, false);

        PlayerFilter filter = new PlayerFilter(database, carlsen, PlayerFilter.PlayerColor.WHITE);
        filter.initSearch();

        assertEquals(52, filter.countEstimate());  // All Carlsen games are used as the estimate
        assertTrue(filter.firstGameId() > 100);

        long count = database.getHeaderBase().stream().filter(filter::matches).count();
        assertEquals(27, count);  // There are 27 White Magnus Carlsen games in the World-ch database

        count = database.getHeaderBase().stream(1, filter).count();
        assertEquals(27, count);  // Same count expected when using serialized filtering
    }

    @Test
    public void testCaseInsensitiveExactMatch() {
        PlayerSearcher garry = new PlayerSearcher(database.getPlayerBase(), "kasparov, garry", false, true);

        PlayerFilter filter = new PlayerFilter(database, garry, PlayerFilter.PlayerColor.ANY);
        filter.initSearch();

        long count = database.getHeaderBase().stream().filter(filter::matches).count();
        assertEquals(197, count);
    }

}
