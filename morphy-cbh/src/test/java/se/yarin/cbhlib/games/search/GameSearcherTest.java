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
    public void testSingleFilter() {
        GameSearcher searcher = new GameSearcher(database);

        searcher.addFilter(new SearchFilterBase(database) {
            @Override
            public boolean matches(GameHeader gameHeader) {
                return gameHeader.getPlayedDate().year() == 2018;
            }
        });

        GameSearcher.SearchResult result = searcher.search(0, true);
        assertTrue(result.getHits().stream().allMatch(hit -> hit.getGameHeader().getPlayedDate().year() == 2018));
        assertEquals(15, result.getHits().size());
        assertEquals(15, result.getTotalHits());
    }

    @Test
    public void testCombinedFilters() {
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

        GameSearcher.SearchResult result = searcher.search(30, true);
        assertTrue(result.getHits().stream()
                .map(GameSearcher.Hit::getGameHeader)
                .allMatch(game -> game.getResult() != GameResult.DRAW && game.getPlayedDate().year() >= 2000));

        assertEquals(30, result.getHits().size());
        assertEquals(61, result.getTotalHits());
    }
}
