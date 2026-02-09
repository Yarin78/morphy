package se.yarin.morphy.service.tournaments.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.yarin.chess.Date;
import se.yarin.morphy.entities.*;

/**
 * Roundtrip test for TournamentDtoConverter. Validates that Tournament entities can be converted to
 * DTOs and back without losing data.
 */
class TournamentDtoConverterTest {

  private TournamentDtoConverter converter;

  @BeforeEach
  void setUp() {
    converter = new TournamentDtoConverter();
  }

  @Test
  void testComprehensiveTournamentRoundtrip() {
    // Create a comprehensive tournament with all fields set
    Tournament originalTournament =
        ImmutableTournament.builder()
            .id(42)
            .title("World Chess Championship 2024")
            .date(new Date(2024, 3, 15))
            .place("Singapore")
            .nation(Nation.SINGAPORE)
            .category(22)
            .rounds(14)
            .type(TournamentType.MATCH)
            .timeControl(TournamentTimeControl.BLITZ)
            .complete(true)
            .teamTournament(false)
            .count(14)
            .firstGameId(1)
            .build();

    TournamentExtra originalExtra =
        ImmutableTournamentExtra.builder().endDate(new Date(2024, 4, 10)).build();

    // Convert to DTO
    TournamentDto dto = converter.toDto(originalTournament, originalExtra);

    // Verify DTO has correct values
    assertEquals(42L, dto.id());
    assertEquals("World Chess Championship 2024", dto.name());
    assertEquals(new Date(2024, 3, 15), dto.startDate());
    assertEquals(new Date(2024, 4, 10), dto.endDate());
    assertEquals("Singapore", dto.site());
    assertEquals("SIN", dto.country()); // IOC code for Singapore
    assertEquals(22, dto.category());
    assertEquals(14, dto.rounds());
    assertEquals("match", dto.type());
    assertEquals("blitz", dto.timeControl());
    assertTrue(dto.complete());
    assertNull(dto.teamTournament()); // False values are null in DTO
    assertEquals(14, dto.gameCount());

    // Convert back to entity
    Tournament recreatedTournament = converter.toTournament(dto);
    TournamentExtra recreatedExtra = converter.toTournamentExtra(dto);

    // Compare tournaments (excluding id, count, and firstGameId which are managed by database)
    assertTournamentFieldsEqual(originalTournament, recreatedTournament);
    assertTournamentExtraFieldsEqual(originalExtra, recreatedExtra);
  }

  @Test
  void testMinimalTournamentRoundtrip() {
    // Create a minimal tournament with only required fields
    Tournament originalTournament =
        ImmutableTournament.builder()
            .id(1)
            .title("Local Club Championship")
            .date(Date.unset())
            .build();

    TournamentExtra originalExtra = TournamentExtra.empty();

    // Convert to DTO
    TournamentDto dto = converter.toDto(originalTournament, originalExtra);

    // Verify DTO has correct values
    assertEquals(1L, dto.id());
    assertEquals("Local Club Championship", dto.name());
    assertNull(dto.startDate()); // Unset date becomes null
    assertNull(dto.endDate());
    assertNull(dto.site());
    assertNull(dto.country());
    assertNull(dto.category());
    assertNull(dto.rounds());
    assertNull(dto.type());
    assertNull(dto.timeControl());
    assertNull(dto.complete());
    assertNull(dto.teamTournament());
    assertNull(dto.gameCount()); // count is 0, so becomes null

    // Convert back to entity
    Tournament recreatedTournament = converter.toTournament(dto);
    TournamentExtra recreatedExtra = converter.toTournamentExtra(dto);

    // Compare tournaments
    assertTournamentFieldsEqual(originalTournament, recreatedTournament);
    assertTournamentExtraFieldsEqual(originalExtra, recreatedExtra);
  }

  @Test
  void testTeamTournamentRoundtrip() {
    // Test specifically the teamTournament flag
    Tournament originalTournament =
        ImmutableTournament.builder()
            .id(10)
            .title("Chess Olympiad 2024")
            .date(new Date(2024, 9, 1))
            .place("Budapest")
            .nation(Nation.HUNGARY)
            .teamTournament(true)
            .rounds(11)
            .build();

    TournamentExtra originalExtra = TournamentExtra.empty();

    // Convert to DTO
    TournamentDto dto = converter.toDto(originalTournament, originalExtra);

    // Verify teamTournament flag is set
    assertTrue(dto.teamTournament());

    // Convert back to entity
    Tournament recreatedTournament = converter.toTournament(dto);

    // Verify teamTournament flag is preserved
    assertTrue(recreatedTournament.teamTournament());
  }

  @Test
  void testVariousTournamentTypesAndTimeControls() {
    // Test different tournament types
    Tournament swiss =
        ImmutableTournament.builder()
            .title("Swiss Tournament")
            .type(TournamentType.SWISS_SYSTEM)
            .build();

    TournamentDto swissDto = converter.toDto(swiss, TournamentExtra.empty());
    assertEquals("swiss", swissDto.type());
    assertEquals(TournamentType.SWISS_SYSTEM, converter.toTournament(swissDto).type());

    // Test different time controls
    Tournament rapidTournament =
        ImmutableTournament.builder()
            .title("Rapid Championship")
            .timeControl(TournamentTimeControl.RAPID)
            .build();

    TournamentDto rapidDto = converter.toDto(rapidTournament, TournamentExtra.empty());
    assertEquals("rapid", rapidDto.timeControl());
    assertEquals(TournamentTimeControl.RAPID, converter.toTournament(rapidDto).timeControl());
  }

  @Test
  void testEmptyStringsHandling() {
    // Test that empty strings in DTO become null in DTO output
    Tournament tournament = ImmutableTournament.builder().title("").place("").build();

    TournamentDto dto = converter.toDto(tournament, TournamentExtra.empty());

    // Empty strings should become null in DTO for optional fields
    assertNull(dto.site());

    // But title should be preserved as empty string (it's required)
    assertEquals("", dto.name());

    // Convert back - empty string in name becomes empty string in title
    Tournament recreated = converter.toTournament(dto);
    assertEquals("", recreated.title());
    assertEquals("", recreated.place());
  }

  @Test
  void testNullNameInDtoBecomesEmptyString() {
    // When converting from DTO with null name, should become empty string
    TournamentDto dto =
        new TournamentDto(
            1L, null, null, null, null, null, null, null, null, null, null, null, null);

    Tournament tournament = converter.toTournament(dto);
    assertEquals("", tournament.title()); // null name becomes empty string
  }

  @Test
  void testUnsetDatesHandling() {
    // Test that unset dates are handled correctly
    Tournament tournament = ImmutableTournament.builder().title("Test").date(Date.unset()).build();

    TournamentExtra extra = ImmutableTournamentExtra.builder().endDate(Date.unset()).build();

    TournamentDto dto = converter.toDto(tournament, extra);

    // Unset dates should become null in DTO
    assertNull(dto.startDate());
    assertNull(dto.endDate());

    // Convert back - null dates become unset dates
    Tournament recreatedTournament = converter.toTournament(dto);
    TournamentExtra recreatedExtra = converter.toTournamentExtra(dto);

    assertTrue(recreatedTournament.date().isUnset());
    assertTrue(recreatedExtra.endDate().isUnset());
  }

  /**
   * Compares tournament fields, excluding id, count, and firstGameId which are managed by the
   * database.
   */
  private void assertTournamentFieldsEqual(Tournament expected, Tournament actual) {
    assertEquals(expected.title(), actual.title(), "Title mismatch");
    assertEquals(expected.date(), actual.date(), "Date mismatch");
    assertEquals(expected.place(), actual.place(), "Place mismatch");
    assertEquals(expected.nation(), actual.nation(), "Nation mismatch");
    assertEquals(expected.category(), actual.category(), "Category mismatch");
    assertEquals(expected.rounds(), actual.rounds(), "Rounds mismatch");
    assertEquals(expected.type(), actual.type(), "Type mismatch");
    assertEquals(expected.timeControl(), actual.timeControl(), "Time control mismatch");
    assertEquals(expected.complete(), actual.complete(), "Complete flag mismatch");
    assertEquals(
        expected.teamTournament(), actual.teamTournament(), "Team tournament flag mismatch");
  }

  /** Compares tournament extra fields. */
  private void assertTournamentExtraFieldsEqual(TournamentExtra expected, TournamentExtra actual) {
    assertEquals(expected.endDate(), actual.endDate(), "End date mismatch");
  }
}
