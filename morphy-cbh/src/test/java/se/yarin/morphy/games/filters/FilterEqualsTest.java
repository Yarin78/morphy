package se.yarin.morphy.games.filters;

import org.junit.jupiter.api.Test;
import se.yarin.chess.Date;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.queries.GameEntityJoinCondition;

import static org.junit.jupiter.api.Assertions.*;

class FilterEqualsTest {

  @Test
  void testPlayerFilterEquals() {
    PlayerFilter filter1 = new PlayerFilter(123, GameEntityJoinCondition.ANY);
    PlayerFilter filter2 = new PlayerFilter(123, GameEntityJoinCondition.ANY);
    PlayerFilter filter3 = new PlayerFilter(456, GameEntityJoinCondition.ANY);
    PlayerFilter filter4 = new PlayerFilter(123, GameEntityJoinCondition.WHITE);

    assertEquals(filter1, filter2);
    assertNotEquals(filter1, filter3);
    assertNotEquals(filter1, filter4);
    assertEquals(filter1.hashCode(), filter2.hashCode());
  }

  @Test
  void testDateRangeFilterEquals() {
    DateRangeFilter filter1 = new DateRangeFilter(new Date(2020, 1, 1), new Date(2020, 12, 31));
    DateRangeFilter filter2 = new DateRangeFilter(new Date(2020, 1, 1), new Date(2020, 12, 31));
    DateRangeFilter filter3 = new DateRangeFilter(new Date(2021, 1, 1), new Date(2021, 12, 31));

    assertEquals(filter1, filter2);
    assertNotEquals(filter1, filter3);
    assertEquals(filter1.hashCode(), filter2.hashCode());
  }

  @Test
  void testTournamentFilterEquals() {
    TournamentFilter filter1 = new TournamentFilter(100);
    TournamentFilter filter2 = new TournamentFilter(100);
    TournamentFilter filter3 = new TournamentFilter(200);

    assertEquals(filter1, filter2);
    assertNotEquals(filter1, filter3);
    assertEquals(filter1.hashCode(), filter2.hashCode());
  }

  @Test
  void testIsGameFilterEquals() {
    IsGameFilter filter1 = new IsGameFilter();
    IsGameFilter filter2 = new IsGameFilter();

    assertEquals(filter1, filter2);
    assertEquals(filter1.hashCode(), filter2.hashCode());
  }

  @Test
  void testEcoFilterEquals() {
    EcoFilter filter1 = new EcoFilter("B90");
    EcoFilter filter2 = new EcoFilter("B90");
    EcoFilter filter3 = new EcoFilter("B91");
    EcoFilter filter4 = new EcoFilter("B9*");

    assertEquals(filter1, filter2);
    assertNotEquals(filter1, filter3);
    assertNotEquals(filter1, filter4);
    assertEquals(filter1.hashCode(), filter2.hashCode());
  }

  @Test
  void testRoundFilterEquals() {
    RoundFilter filter1 = new RoundFilter(5);
    RoundFilter filter2 = new RoundFilter(5);
    RoundFilter filter3 = new RoundFilter(5, 2);
    RoundFilter filter4 = new RoundFilter(6);

    assertEquals(filter1, filter2);
    assertNotEquals(filter1, filter3);
    assertNotEquals(filter1, filter4);
    assertEquals(filter1.hashCode(), filter2.hashCode());
  }

  @Test
  void testRatingRangeFilterEquals() {
    RatingRangeFilter filter1 =
        new RatingRangeFilter(2000, 2500, RatingRangeFilter.RatingColor.ANY);
    RatingRangeFilter filter2 =
        new RatingRangeFilter(2000, 2500, RatingRangeFilter.RatingColor.ANY);
    RatingRangeFilter filter3 =
        new RatingRangeFilter(2000, 2500, RatingRangeFilter.RatingColor.WHITE);
    RatingRangeFilter filter4 =
        new RatingRangeFilter(2100, 2500, RatingRangeFilter.RatingColor.ANY);

    assertEquals(filter1, filter2);
    assertNotEquals(filter1, filter3);
    assertNotEquals(filter1, filter4);
    assertEquals(filter1.hashCode(), filter2.hashCode());
  }
}
