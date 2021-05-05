package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.chess.Date;
import se.yarin.morphy.Database;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;

import java.util.Objects;

import static org.junit.Assert.assertEquals;

public class DateRangeFilterTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private Database database, databaseInMemory;

    @Before
    public void setupFilterTest() {
        database = ResourceLoader.openWorldChDatabase();
        databaseInMemory = ResourceLoader.openWorldChDatabaseInMemory();
    }

    @Test
    public void testParseDateRange() {
        DateRangeFilter df1 = new DateRangeFilter("1950-");
        assertEquals(new Date(1950), df1.getFromDate());
        assertEquals(Date.unset(), df1.getToDate());

        DateRangeFilter df2 = new DateRangeFilter("1923-05-1981-01-07");
        assertEquals(new Date(1923, 5), df2.getFromDate());
        assertEquals(new Date(1981, 1, 7), df2.getToDate());

        DateRangeFilter df3 = new DateRangeFilter("-2000");
        assertEquals(Date.unset(), df3.getFromDate());
        assertEquals(new Date(2000), df3.getToDate());

        DateRangeFilter df4 = new DateRangeFilter("1850-06-01-");
        assertEquals(new Date(1850, 6, 1), df4.getFromDate());
        assertEquals(Date.unset(), df4.getToDate());
    }

    private int countMatches(@NotNull Database database, @NotNull ItemStorageFilter<GameHeader> filter) {
        return (int) database.gameHeaderIndex().getFiltered(filter).stream().filter(Objects::nonNull).count();
    }

    @Test
    public void testFromRange() {
        DateRangeFilter filter = new DateRangeFilter("1950-");

        // Non-serialized filtering
        assertEquals(649, countMatches(databaseInMemory, filter));

        // Serialized filtering
        assertEquals(649, countMatches(database, filter));
    }

    @Test
    public void testRange() {
        DateRangeFilter filter = new DateRangeFilter("1921-04-1927-10-13");

        // Non-serialized filtering
        assertEquals(21, countMatches(databaseInMemory, filter));

        // Serialized filtering
        assertEquals(21, countMatches(database, filter));
    }
}