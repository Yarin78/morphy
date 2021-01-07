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
import java.util.Iterator;

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
    public void testCaseSensitivePrefixSearch() throws IOException {
        PlayerSearcher carlsen = new PlayerSearcher(database.getPlayerBase(), "Carlsen", true, false);

        PlayerFilter filter = new PlayerFilter(database, carlsen, PlayerFilter.PlayerColor.WHITE);
        filter.initSearch();

        assertEquals(52, filter.countEstimate());  // All Carlsen games are used as the estimate
        assertTrue(filter.firstGameId() > 100);

        int count = 0;
        Iterator<GameHeader> iterator = database.getHeaderBase().iterator();
        while (iterator.hasNext()) {
            GameHeader game = iterator.next();
            if (filter.matches(game)) count += 1;
        }
        assertEquals(27, count);  // There are 27 White Magnus Carlsen games in the World-ch database

        count = 0;
        iterator = database.getHeaderBase().iterator(1, filter);
        while (iterator.hasNext()) {
            iterator.next();
            count += 1;
        }
        assertEquals(27, count);  // Same count expected when using serialized filtering
    }

    @Test
    public void testCaseInsensitiveExactMatch() throws IOException {
        PlayerSearcher garry = new PlayerSearcher(database.getPlayerBase(), "kasparov, garry", false, true);

        PlayerFilter filter = new PlayerFilter(database, garry, PlayerFilter.PlayerColor.ANY);
        filter.initSearch();

        int count = 0;
        for (GameHeader game : database.getHeaderBase()) {
            if (filter.matches(game)) {
                count += 1;
            }
        }
        assertEquals(197, count);
    }

}
