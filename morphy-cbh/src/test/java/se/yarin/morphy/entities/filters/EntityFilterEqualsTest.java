package se.yarin.morphy.entities.filters;

import org.junit.jupiter.api.Test;
import se.yarin.chess.Date;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.TournamentTimeControl;
import se.yarin.morphy.entities.TournamentType;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EntityFilterEqualsTest {

  @Test
  void testPlayerNameFilterEquals() {
    PlayerNameFilter filter1 = new PlayerNameFilter("Carlsen", "Magnus", true, false);
    PlayerNameFilter filter2 = new PlayerNameFilter("Carlsen", "Magnus", true, false);
    PlayerNameFilter filter3 = new PlayerNameFilter("Carlsen", "Magnus", false, false);
    PlayerNameFilter filter4 = new PlayerNameFilter("Kasparov", "Garry", true, false);

    assertEquals(filter1, filter2);
    assertNotEquals(filter1, filter3);
    assertNotEquals(filter1, filter4);
    assertEquals(filter1.hashCode(), filter2.hashCode());
  }

  @Test
  void testTournamentTitleFilterEquals() {
    TournamentTitleFilter filter1 = new TournamentTitleFilter("Candidates", true, false);
    TournamentTitleFilter filter2 = new TournamentTitleFilter("Candidates", true, false);
    TournamentTitleFilter filter3 = new TournamentTitleFilter("Candidates", false, false);
    TournamentTitleFilter filter4 = new TournamentTitleFilter("World Championship", true, false);

    assertEquals(filter1, filter2);
    assertNotEquals(filter1, filter3);
    assertNotEquals(filter1, filter4);
    assertEquals(filter1.hashCode(), filter2.hashCode());
  }

  @Test
  void testTournamentStartDateFilterEquals() {
    TournamentStartDateFilter filter1 =
        new TournamentStartDateFilter(new Date(2020, 1, 1), new Date(2020, 12, 31));
    TournamentStartDateFilter filter2 =
        new TournamentStartDateFilter(new Date(2020, 1, 1), new Date(2020, 12, 31));
    TournamentStartDateFilter filter3 =
        new TournamentStartDateFilter(new Date(2021, 1, 1), new Date(2021, 12, 31));

    assertEquals(filter1, filter2);
    assertNotEquals(filter1, filter3);
    assertEquals(filter1.hashCode(), filter2.hashCode());
  }

  @Test
  void testTournamentNationFilterEquals() {
    TournamentNationFilter filter1 = new TournamentNationFilter(Set.of(Nation.NORWAY));
    TournamentNationFilter filter2 = new TournamentNationFilter(Set.of(Nation.NORWAY));
    TournamentNationFilter filter3 = new TournamentNationFilter(Set.of(Nation.SWEDEN));

    assertEquals(filter1, filter2);
    assertNotEquals(filter1, filter3);
    assertEquals(filter1.hashCode(), filter2.hashCode());
  }

  @Test
  void testTournamentCategoryFilterEquals() {
    TournamentCategoryFilter filter1 = new TournamentCategoryFilter(10, 15);
    TournamentCategoryFilter filter2 = new TournamentCategoryFilter(10, 15);
    TournamentCategoryFilter filter3 = new TournamentCategoryFilter(10, 16);

    assertEquals(filter1, filter2);
    assertNotEquals(filter1, filter3);
    assertEquals(filter1.hashCode(), filter2.hashCode());
  }

  @Test
  void testTournamentRoundsFilterEquals() {
    TournamentRoundsFilter filter1 = new TournamentRoundsFilter(5, 10);
    TournamentRoundsFilter filter2 = new TournamentRoundsFilter(5, 10);
    TournamentRoundsFilter filter3 = new TournamentRoundsFilter(5, 11);

    assertEquals(filter1, filter2);
    assertNotEquals(filter1, filter3);
    assertEquals(filter1.hashCode(), filter2.hashCode());
  }

  @Test
  void testTournamentTypeFilterEquals() {
    TournamentTypeFilter filter1 =
        new TournamentTypeFilter(Set.of(TournamentType.SWISS_SYSTEM));
    TournamentTypeFilter filter2 =
        new TournamentTypeFilter(Set.of(TournamentType.SWISS_SYSTEM));
    TournamentTypeFilter filter3 =
        new TournamentTypeFilter(Set.of(TournamentType.ROUND_ROBIN));

    assertEquals(filter1, filter2);
    assertNotEquals(filter1, filter3);
    assertEquals(filter1.hashCode(), filter2.hashCode());
  }

  @Test
  void testTournamentTimeControlFilterEquals() {
    TournamentTimeControlFilter filter1 =
        new TournamentTimeControlFilter(Set.of(TournamentTimeControl.NORMAL));
    TournamentTimeControlFilter filter2 =
        new TournamentTimeControlFilter(Set.of(TournamentTimeControl.NORMAL));
    TournamentTimeControlFilter filter3 =
        new TournamentTimeControlFilter(Set.of(TournamentTimeControl.RAPID));

    assertEquals(filter1, filter2);
    assertNotEquals(filter1, filter3);
    assertEquals(filter1.hashCode(), filter2.hashCode());
  }

  @Test
  void testTournamentTeamFilterEquals() {
    TournamentTeamFilter filter1 = new TournamentTeamFilter();
    TournamentTeamFilter filter2 = new TournamentTeamFilter();

    assertEquals(filter1, filter2);
    assertEquals(filter1.hashCode(), filter2.hashCode());
  }

  @Test
  void testAllFilterEquals() {
    AllFilter<Tournament> filter1 = new AllFilter<>(EntityType.TOURNAMENT);
    AllFilter<Tournament> filter2 = new AllFilter<>(EntityType.TOURNAMENT);
    AllFilter<Tournament> filter3 = new AllFilter<>(EntityType.PLAYER);

    assertEquals(filter1, filter2);
    assertNotEquals(filter1, filter3);
    assertEquals(filter1.hashCode(), filter2.hashCode());
  }

  @Test
  void testManualFilterEquals() {
    ManualFilter<Tournament> filter1 = new ManualFilter<>(new int[] {1, 2, 3}, EntityType.TOURNAMENT);
    ManualFilter<Tournament> filter2 = new ManualFilter<>(new int[] {1, 2, 3}, EntityType.TOURNAMENT);
    ManualFilter<Tournament> filter3 = new ManualFilter<>(new int[] {1, 2, 4}, EntityType.TOURNAMENT);

    assertEquals(filter1, filter2);
    assertNotEquals(filter1, filter3);
    assertEquals(filter1.hashCode(), filter2.hashCode());
  }
}
