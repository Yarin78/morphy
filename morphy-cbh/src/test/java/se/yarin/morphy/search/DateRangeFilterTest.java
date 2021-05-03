package se.yarin.morphy.search;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.chess.Date;
import se.yarin.morphy.Database;
import se.yarin.morphy.ResourceLoader;

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

    @Test
    public void testFromRange() {
        DateRangeFilter filter = new DateRangeFilter("1950-");

        // Non-serialized filtering
        assertEquals(649, databaseInMemory.getGames(filter).size());

        // Serialized filtering
        assertEquals(649, database.getGames(filter).size());
    }

    @Test
    public void testRange() {
        DateRangeFilter filter = new DateRangeFilter("1921-04-1927-10-13");

        // Non-serialized filtering
        assertEquals(21, databaseInMemory.getGames(filter).size());

        // Serialized filtering
        assertEquals(21, database.getGames(filter).size());
    }
}