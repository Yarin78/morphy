package se.yarin.cbhlib.games.search;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
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
    public void testSingleHeaderFilter() {
        GameSearcher searcher = new GameSearcher(database);

        searcher.addFilter(new SearchFilterBase(database) {
            @Override
            public boolean matches(Game game) {
                return game.getPlayedDate().year() == 2018;
            }
        });

        GameSearcher.SearchResult result = searcher.search(0, true);
        assertTrue(result.getGames().stream().allMatch(game -> game.getPlayedDate().year() == 2018));
        assertEquals(15, result.getGames().size());
        assertEquals(15, result.getTotalGames());
    }

    @Test
    public void testSingleExtendedHeaderFilter() {
        GameSearcher searcher = new GameSearcher(database);

        searcher.addFilter(new SearchFilterBase(database) {
            @Override
            public boolean matches(Game game) {
                return game.getGameVersion() >= 1000;
            }
        });

        GameSearcher.SearchResult result = searcher.search(30, true);
        assertTrue(result.getGames().stream()
                .allMatch(game -> game.getGameVersion() >= 1000));

        assertEquals(13, result.getGames().size());
        assertEquals(13, result.getTotalGames());
    }

    @Test
    public void testCombinedFilters() {
        GameSearcher searcher = new GameSearcher(database);

        searcher.addFilter(new SearchFilterBase(database) {
            @Override
            public boolean matches(Game game) {
                return game.getPlayedDate().year() >= 2000;
            }
        });

        searcher.addFilter(new SearchFilterBase(database) {
            @Override
            public boolean matches(Game game) {
                return game.getResult() != GameResult.DRAW;
            }
        });

        GameSearcher.SearchResult result = searcher.search(30, true);
        assertTrue(result.getGames().stream()
                .allMatch(game -> game.getResult() != GameResult.DRAW && game.getPlayedDate().year() >= 2000));

        assertEquals(30, result.getGames().size());
        assertEquals(61, result.getTotalGames());
    }

}
