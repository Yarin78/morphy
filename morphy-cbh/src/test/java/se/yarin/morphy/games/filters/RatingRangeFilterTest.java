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

public class RatingRangeFilterTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private Database database, databaseInMemory;

  @Before
  public void setupFilterTest() {
    database = ResourceLoader.openWorldChDatabase();
    databaseInMemory = ResourceLoader.openWorldChDatabaseInMemory();
  }

  @Test
  public void testParseRatingRange() {
    RatingRangeFilter rrf1 = new RatingRangeFilter("2200-", RatingRangeFilter.RatingColor.ANY);
    assertEquals(rrf1.minRating(), 2200);
    assertEquals(rrf1.maxRating(), 9999);

    RatingRangeFilter rrf2 = new RatingRangeFilter("500-900", RatingRangeFilter.RatingColor.ANY);
    assertEquals(rrf2.minRating(), 500);
    assertEquals(rrf2.maxRating(), 900);

    RatingRangeFilter rrf3 = new RatingRangeFilter("-2750", RatingRangeFilter.RatingColor.ANY);
    assertEquals(rrf3.minRating(), 0);
    assertEquals(rrf3.maxRating(), 2750);
  }

  private int countMatches(
      @NotNull Database database, @NotNull ItemStorageFilter<GameHeader> filter) {
    return (int)
        database.gameHeaderIndex().getFiltered(filter).stream().filter(Objects::nonNull).count();
  }

  @Test
  public void testFilterRangeAverage() {
    RatingRangeFilter filter =
        new RatingRangeFilter(2750, 9999, RatingRangeFilter.RatingColor.AVERAGE);

    // Non-serialized filtering
    assertEquals(208, countMatches(databaseInMemory, filter));

    // Serialized filtering
    assertEquals(208, countMatches(database, filter));
  }

  @Test
  public void testFilterRangeAny() {
    RatingRangeFilter filter = new RatingRangeFilter(0, 2600, RatingRangeFilter.RatingColor.ANY);

    // Non-serialized filtering
    assertEquals(600, countMatches(databaseInMemory, filter));

    // Serialized filtering
    assertEquals(600, countMatches(database, filter));
  }

  @Test
  public void testFilterRangeBoth() {
    RatingRangeFilter filter =
        new RatingRangeFilter(2600, 2700, RatingRangeFilter.RatingColor.BOTH);

    // Non-serialized filtering
    assertEquals(18, countMatches(databaseInMemory, filter));

    // Serialized filtering
    assertEquals(18, countMatches(database, filter));
  }

  @Test
  public void testFilterRangeDifference() {
    RatingRangeFilter filter =
        new RatingRangeFilter(150, 9999, RatingRangeFilter.RatingColor.DIFFERENCE);

    // Non-serialized filtering
    assertEquals(20, countMatches(databaseInMemory, filter));

    // Serialized filtering
    assertEquals(20, countMatches(database, filter));
  }
}
