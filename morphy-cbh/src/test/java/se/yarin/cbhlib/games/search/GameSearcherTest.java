package se.yarin.cbhlib.games.search;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.ResourceLoader;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.GameResult;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GameSearcherTest {

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
        database = Database.openInMemory(file);
    }

    @Test
    public void testSingleFilter() throws IOException {
        GameSearcher searcher = new GameSearcher(database);

        searcher.addFilter(new SearchFilterBase(database) {
            @Override
            public boolean matches(GameHeader gameHeader) {
                return gameHeader.getPlayedDate().year() == 2018;
            }
        });

        Iterable<GameSearcher.Hit> search = searcher.search();
        int count = 0;
        for (GameSearcher.Hit hit : search) {
            assertEquals(2018, hit.getGameHeader().getPlayedDate().year());
            count += 1;
        }
        assertEquals(15, count);
    }

    @Test
    public void testCombinedFilters() throws IOException {
        GameSearcher searcher = new GameSearcher(database);

        searcher.addFilter(new SearchFilterBase(database) {
            @Override
            public boolean matches(GameHeader gameHeader) {
                return gameHeader.getPlayedDate().year() >= 2000;
            }
        });

        searcher.addFilter(new SearchFilterBase(database) {
            @Override
            public boolean matches(GameHeader gameHeader) {
                return gameHeader.getResult() != GameResult.DRAW;
            }
        });

        Iterable<GameSearcher.Hit> search = searcher.search();

        int count = 0;
        for (GameSearcher.Hit hit : search) {
            assertTrue(hit.getGameHeader().getResult() != GameResult.DRAW);
            assertTrue(hit.getGameHeader().getPlayedDate().year() >= 2000);
            // System.out.println(hit.getGameHeader().getPlayedDate() + ": " + hit.getWhite() + " - " + hit.getBlack() + "  " + hit.getGameHeader().getResult());
            count += 1;
        }
        assertEquals(61, count);
    }
}
