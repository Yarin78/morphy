package se.yarin.cbhlib.games.search;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.ResourceLoader;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.Date;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class DateRangeFilterTest {
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
    public void testParseDateRange() {
        DateRangeFilter df1 = new DateRangeFilter(database, "1950-");
        assertEquals(new Date(1950), df1.getFromDate());
        assertEquals(Date.unset(), df1.getToDate());

        DateRangeFilter df2 = new DateRangeFilter(database, "1923-05-1981-01-07");
        assertEquals(new Date(1923, 5), df2.getFromDate());
        assertEquals(new Date(1981, 1, 7), df2.getToDate());

        DateRangeFilter df3 = new DateRangeFilter(database, "-2000");
        assertEquals(Date.unset(), df3.getFromDate());
        assertEquals(new Date(2000), df3.getToDate());

        DateRangeFilter df4 = new DateRangeFilter(database, "1850-06-01-");
        assertEquals(new Date(1850, 6, 1), df4.getFromDate());
        assertEquals(Date.unset(), df4.getToDate());
    }

    @Test
    public void testFromRange() {
        DateRangeFilter filter = new DateRangeFilter(database, "1950-");

        long count = database.getHeaderBase().stream().filter(filter::matches).count();
        assertEquals(649, count);

        count = database.getHeaderBase().stream(1, filter).count();
        assertEquals(649, count);  // Same count expected when using serialized filtering
    }

    @Test
    public void testRange() {
        DateRangeFilter filter = new DateRangeFilter(database, "1921-04-1927-10-13");

        long count = database.getHeaderBase().stream().filter(filter::matches).count();
        // Some games from the 1921 WCh match, some games from the 1927 WCh match
        assertEquals(21, count);

        count = database.getHeaderBase().stream(1, filter).count();
        assertEquals(21, count);  // Same count expected when using serialized filtering
    }
}
