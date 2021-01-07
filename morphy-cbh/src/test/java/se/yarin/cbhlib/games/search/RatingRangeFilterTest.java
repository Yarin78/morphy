package se.yarin.cbhlib.games.search;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.ResourceLoader;
import se.yarin.cbhlib.games.GameHeader;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class RatingRangeFilterTest {
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
    public void testParseRatingRange() {
        RatingRangeFilter rrf1 = new RatingRangeFilter(database, "2200-", RatingRangeFilter.RatingColor.ANY);
        assertEquals(rrf1.getMinRating(), 2200);
        assertEquals(rrf1.getMaxRating(), 9999);

        RatingRangeFilter rrf2 = new RatingRangeFilter(database, "500-900", RatingRangeFilter.RatingColor.ANY);
        assertEquals(rrf2.getMinRating(), 500);
        assertEquals(rrf2.getMaxRating(), 900);

        RatingRangeFilter rrf3 = new RatingRangeFilter(database, "-2750", RatingRangeFilter.RatingColor.ANY);
        assertEquals(rrf3.getMinRating(), 0);
        assertEquals(rrf3.getMaxRating(), 2750);
    }

    @Test
    public void testFilterRangeAverage() {
        RatingRangeFilter filter = new RatingRangeFilter(database, 2750, 9999, RatingRangeFilter.RatingColor.AVERAGE);

        long count = database.getHeaderBase().stream().filter(filter::matches).count();
        assertEquals(208, count);

        count = database.getHeaderBase().stream(1, filter).count();
        assertEquals(208, count);  // Same count expected when using serialize
    }

    @Test
    public void testFilterRangeAny() {
        RatingRangeFilter filter = new RatingRangeFilter(database, 0, 2600, RatingRangeFilter.RatingColor.ANY);

        long count = database.getHeaderBase().stream().filter(filter::matches).count();
        assertEquals(600, count);

        count = database.getHeaderBase().stream(1, filter).count();
        assertEquals(600, count);  // Same count expected when using serialize
    }

    @Test
    public void testFilterRangeBoth() {
        RatingRangeFilter filter = new RatingRangeFilter(database, 2600, 2700, RatingRangeFilter.RatingColor.BOTH);

        long count = database.getHeaderBase().stream().filter(filter::matches).count();
        assertEquals(18, count);

        count = database.getHeaderBase().stream(1, filter).count();
        assertEquals(18, count);  // Same count expected when using serialize
    }

    @Test
    public void testFilterRangeDifference() {
        RatingRangeFilter filter = new RatingRangeFilter(database, 150, 9999, RatingRangeFilter.RatingColor.DIFFERENCE);

        long count = database.getHeaderBase().stream().filter(filter::matches).count();
        assertEquals(20, count);

        count = database.getHeaderBase().stream(1, filter).count();
        assertEquals(20, count);  // Same count expected when using serialize
    }
}
