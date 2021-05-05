package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.morphy.Database;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;

import java.util.Objects;

import static org.junit.Assert.assertEquals;

public class RawGameHeaderFilterTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private Database database;

    @Before
    public void setupFilterTest() {
        database = ResourceLoader.openWorldChDatabase();
    }

    private int countMatches(@NotNull Database database, @NotNull ItemStorageFilter<GameHeader> filter) {
        return (int) database.gameHeaderIndex().getFiltered(filter).stream().filter(Objects::nonNull).count();
    }

    @Test
    public void testGetGuidingTexts() {
        RawGameHeaderFilter filter = new RawGameHeaderFilter("(byte(0) & 2) > 0");
        assertEquals(13, countMatches(database, filter));
    }
}
