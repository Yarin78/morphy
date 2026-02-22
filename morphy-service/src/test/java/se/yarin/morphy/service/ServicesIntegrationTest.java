package se.yarin.morphy.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import se.yarin.chess.Date;
import se.yarin.chess.GameResult;
import se.yarin.chess.NAG;
import se.yarin.morphy.Database;
import se.yarin.morphy.service.annotators.AnnotatorsService;
import se.yarin.morphy.service.annotators.dto.AnnotatorDto;
import se.yarin.morphy.service.databases.DatabaseService;
import se.yarin.morphy.service.games.GamesService;
import se.yarin.morphy.service.games.dto.GameDto;
import se.yarin.morphy.service.games.dto.GameMovesDto;
import se.yarin.morphy.service.players.PlayersService;
import se.yarin.morphy.service.players.dto.PlayerDto;
import se.yarin.morphy.service.search.EntitySearchRequest;
import se.yarin.morphy.service.search.EntitySearchResponse;
import se.yarin.morphy.service.sources.SourcesService;
import se.yarin.morphy.service.sources.dto.SourceDto;
import se.yarin.morphy.service.teams.TeamsService;
import se.yarin.morphy.service.teams.dto.TeamDto;
import se.yarin.morphy.service.tournaments.TournamentsService;
import se.yarin.morphy.service.tournaments.dto.TournamentDto;

/**
 * Comprehensive integration test for all Services.
 *
 * <p>This test verifies that entity relationships and updates work correctly across all services,
 * including entity updates propagating to games and gameCount tracking.
 */
@SpringBootTest
@TestPropertySource(properties = {"app.databases.config="})
class ServicesIntegrationTest {

  @TempDir File tempDir;

  @Autowired private DatabaseService databaseService;
  @Autowired private GamesService gamesService;
  @Autowired private PlayersService playersService;
  @Autowired private TournamentsService tournamentsService;
  @Autowired private TeamsService teamsService;
  @Autowired private SourcesService sourcesService;
  @Autowired private AnnotatorsService annotatorsService;

  private String databaseId;

  @BeforeEach
  void setUp() throws IOException {
    // Create new database for each test
    File dbFile = new File(tempDir, "test.cbh");
    Database.create(dbFile, false).close();

    // Register the database with DatabaseService
    databaseId = "test-db";
    databaseService.registerDatabase(databaseId, "Test Database", dbFile.getAbsolutePath());
  }

  @AfterEach
  void tearDown() throws IOException {
    // Unregister the database after each test
    if (databaseId != null) {
      databaseService.unregisterDatabase(databaseId);
    }
  }

  @Test
  void testCompleteServicesIntegration() {
    // Step 2: Add first game with all fields set, moves, variations, annotations
    GameDto game1 = createComprehensiveGame("Carlsen", "Magnus");
    game1 = gamesService.addGame(databaseId, game1);
    assertNotNull(game1.id());
    int game1Id = game1.id().intValue();

    // Step 3: Add second game with minimum fields, different players/tournament
    GameDto game2 = createMinimalGame();
    game2 = gamesService.addGame(databaseId, game2);
    assertNotNull(game2.id());
    int game2Id = game2.id().intValue();

    // Capture original IDs for later verification
    int game1WhitePlayerId = game1.whitePlayer().id().intValue();
    int game1BlackPlayerId = game1.blackPlayer().id().intValue();
    int game1WhiteTeamId = game1.whiteTeam().id().intValue();
    int game2TournamentId = game2.tournament().id().intValue();
    int game2SourceId = game2.source().id().intValue();

    // Step 4: Update one of the players from game 1 by changing the game
    GameDto game1updated = createComprehensiveGame("Kasparov", "Garry");
    GameDto updatedGame1 = gamesService.replaceGame(databaseId, game1Id, game1updated);
    int game1UpdatedWhitePlayerId = updatedGame1.whitePlayer().id().intValue();

    // Step 5: Update metadata in tournament of game 2
    TournamentDto updatedTournament =
        new TournamentDto(
            (long) game2TournamentId,
            "Updated Tournament Name",
            game2.tournament().startDate(),
            game2.tournament().endDate(),
            "New Location",
            "GER", // Valid IOC country code
            20,
            10,
            "swiss",
            "classical",
            true,
            false,
            null, null, null);
    tournamentsService.updateTournament(databaseId, game2TournamentId, updatedTournament);

    // Step 6: Update team from game 1 with new metadata
    TeamDto updatedTeam =
        new TeamDto(
            (long) game1WhiteTeamId, "Updated Team Title", 42, true, 2024, "NOR", null, null);
    teamsService.updateTeam(databaseId, game1WhiteTeamId, updatedTeam);

    // Step 7: Update source from game 2 with more data
    SourceDto updatedSource =
        new SourceDto(
            (long) game2SourceId,
            "Updated Source", // Max 25 chars
            "New Publisher", // Max 16 chars
            new Date(2024, 6, 15),
            new Date(2024, 7, 1),
            2,
            "HIGH", // Must be enum value: UNSET, HIGH, MEDIUM, LOW
            null, null);
    sourcesService.updateSource(databaseId, game2SourceId, updatedSource);

    // Step 8: Update game 2 - change moves slightly and add annotator
    GameDto game2Updated =
        new GameDto(
            null,
            "game",
            game2.whitePlayer(),
            2700,
            game2.blackPlayer(),
            2680,
            null,
            null,
            GameResult.WHITE_WINS,
            new Date(2024, 3, 20),
            "E20",
            2,
            null,
            NAG.WHITE_MODERATE_ADVANTAGE,
            game2.tournament(),
            game2.source(),
            new AnnotatorDto(null, "GM Bobby Fischer", null, null),
            null,
            new GameMovesDto("1. d4 Nf6 2. c4 e6 3. Nc3 Bb4 4. e3 O-O 5. Bd3 d5 1-0"),
            null, null, null);
    gamesService.replaceGame(databaseId, game2Id, game2Updated);

    // Step 9: Get first game and verify all fields match as expected
    // One of the players and one of the teams should have changed
    GameDto retrievedGame1 = gamesService.getGame(databaseId, game1Id, true, true);

    assertNotNull(retrievedGame1);
    assertEquals(game1Id, retrievedGame1.id().intValue());

    // Verify white player was updated
    assertEquals("Kasparov", retrievedGame1.whitePlayer().lastName());
    assertEquals("Garry", retrievedGame1.whitePlayer().firstName());

    // Verify white team was updated
    assertEquals("Updated Team Title", retrievedGame1.whiteTeam().title());
    assertEquals(42, retrievedGame1.whiteTeam().teamNumber());
    assertEquals(true, retrievedGame1.whiteTeam().season());
    assertEquals(2024, retrievedGame1.whiteTeam().year());

    // Verify other fields remain unchanged
    assertEquals(game1.result(), retrievedGame1.result());
    assertEquals(game1.date(), retrievedGame1.date());

    // Step 10: Get second game and verify tournament, source, annotator, and moves changed
    GameDto retrievedGame2 = gamesService.getGame(databaseId, game2Id, true, true);

    assertNotNull(retrievedGame2);
    assertEquals(game2Id, retrievedGame2.id().intValue());

    // Verify tournament was updated
    assertEquals("Updated Tournament Name", retrievedGame2.tournament().name());
    assertEquals("New Location", retrievedGame2.tournament().site());
    assertEquals("GER", retrievedGame2.tournament().country());
    assertEquals(20, retrievedGame2.tournament().category());
    assertEquals(10, retrievedGame2.tournament().rounds());
    assertEquals("swiss", retrievedGame2.tournament().type());

    // Verify source was updated
    assertEquals("Updated Source", retrievedGame2.source().title());
    assertEquals("New Publisher", retrievedGame2.source().publisher());
    assertEquals(new Date(2024, 6, 15), retrievedGame2.source().publication());
    assertEquals(new Date(2024, 7, 1), retrievedGame2.source().date());
    assertEquals(2, retrievedGame2.source().version());
    assertEquals("HIGH", retrievedGame2.source().quality());

    // Verify annotator was added
    assertNotNull(retrievedGame2.annotator());
    assertEquals("GM Bobby Fischer", retrievedGame2.annotator().name());

    // Verify moves were updated
    assertNotNull(retrievedGame2.moves());
    assertTrue(
        retrievedGame2.moves().pgn().contains("1. d4 Nf6 2. c4"),
        "Moves should be updated");

    // Verify ELO ratings were updated
    assertEquals(2700, retrievedGame2.whiteElo());
    assertEquals(2680, retrievedGame2.blackElo());

    // Step 11: Get the updated player from game 1 and verify it has gameCount=2
    PlayerDto updatedPlayerFromGame1 =
        playersService.getPlayer(databaseId, game1UpdatedWhitePlayerId);
    assertNotNull(updatedPlayerFromGame1);
    assertEquals("Kasparov", updatedPlayerFromGame1.lastName());
    assertEquals("Garry", updatedPlayerFromGame1.firstName());
    assertEquals(2, updatedPlayerFromGame1.gameCount(), "Updated player should have gameCount=2");

    // Step 12: Verify that getting a missing player (out of range) returns null
    PlayerDto missingPlayer = playersService.getPlayer(databaseId, 100);
    assertNull(missingPlayer, "Getting a missing player should return null");

    // Step 13: Verify that an "unused" player id also returns null
    PlayerDto unusedPlayer = playersService.getPlayer(databaseId, game1WhitePlayerId);
    assertNull(
        unusedPlayer,
        "Getting an unused player should return null");
  }

  @Test
  void testUpdateTournamentWithDuplicateKey() {
    // Create two tournaments with different keys
    TournamentDto tournament1 =
        new TournamentDto(
            null,
            "World Cup",
            new Date(2021, 1, 1),
            new Date(2021, 1, 31),
            "Berlin",
            "GER",
            null,
            null,
            null,
            null,
            null,
            null,
            null, null, null);

    TournamentDto tournament2 =
        new TournamentDto(
            null,
            "World Cup",
            new Date(2021, 1, 1),
            new Date(2021, 1, 31),
            "Moscow",
            "RUS",
            null,
            null,
            null,
            null,
            null,
            null,
            null, null, null);

    // Create games with these tournaments to make them persist
    GameDto game1 = createMinimalGameWithTournament(tournament1);
    game1 = gamesService.addGame(databaseId, game1);
    int tournament1Id = game1.tournament().id().intValue();

    GameDto game2 = createMinimalGameWithTournament(tournament2);
    game2 = gamesService.addGame(databaseId, game2);
    int tournament2Id = game2.tournament().id().intValue();

    // Try to update tournament2's place to match tournament1's place
    // This should fail because tournament1 already has year=2021, title="World Cup", place="Berlin"
    TournamentDto duplicateUpdate =
        new TournamentDto(
            (long) tournament2Id,
            "World Cup",
            new Date(2021, 1, 1),
            new Date(2021, 1, 31),
            "Berlin", // Same as tournament1
            "RUS",
            null,
            null,
            null,
            null,
            null,
            null,
            null, null, null);

    assertThrows(
        IllegalArgumentException.class,
        () -> tournamentsService.updateTournament(databaseId, tournament2Id, duplicateUpdate),
        "Should throw IllegalArgumentException when updating to a duplicate key");
  }

  @Test
  void testUpdateTournamentWithUniqueKey() {
    // Create a tournament
    TournamentDto tournament =
        new TournamentDto(
            null,
            "World Cup",
            new Date(2021, 1, 1),
            new Date(2021, 1, 31),
            "Berlin",
            "GER",
            null,
            null,
            null,
            null,
            null,
            null,
            null, null, null);

    // Create game with this tournament
    GameDto game = createMinimalGameWithTournament(tournament);
    game = gamesService.addGame(databaseId, game);
    int tournamentId = game.tournament().id().intValue();

    // Update to a different unique key - should succeed
    TournamentDto uniqueUpdate =
        new TournamentDto(
            (long) tournamentId,
            "World Cup",
            new Date(2021, 1, 1),
            new Date(2021, 1, 31),
            "Paris", // Different place
            "FRA",
            null,
            null,
            null,
            null,
            null,
            null,
            null, null, null);

    // Should not throw
    assertDoesNotThrow(
        () -> tournamentsService.updateTournament(databaseId, tournamentId, uniqueUpdate));

    // Verify the update was successful
    TournamentDto retrieved = tournamentsService.getTournament(databaseId, tournamentId);
    assertNotNull(retrieved);
    assertEquals("Paris", retrieved.site());
  }

  @Test
  void testUpdateTournamentSameKey() {
    // Create a tournament
    TournamentDto tournament =
        new TournamentDto(
            null,
            "World Cup",
            new Date(2021, 1, 1),
            new Date(2021, 1, 31),
            "Berlin",
            "GER",
            null,
            null,
            null,
            null,
            null,
            null,
            null, null, null);

    // Create game with this tournament
    GameDto game = createMinimalGameWithTournament(tournament);
    game = gamesService.addGame(databaseId, game);
    int tournamentId = game.tournament().id().intValue();

    // Update without changing the key (only change non-key fields)
    TournamentDto sameKeyUpdate =
        new TournamentDto(
            (long) tournamentId,
            "World Cup", // Same key fields
            new Date(2021, 1, 1),
            new Date(2021, 1, 31),
            "Berlin",
            "GER",
            20, // Changed non-key field
            10, // Changed non-key field
            "swiss",
            "classical",
            true,
            false,
            null, null, null);

    // Should not throw
    assertDoesNotThrow(
        () -> tournamentsService.updateTournament(databaseId, tournamentId, sameKeyUpdate));

    // Verify the update was successful
    TournamentDto retrieved = tournamentsService.getTournament(databaseId, tournamentId);
    assertNotNull(retrieved);
    assertEquals(20, retrieved.category());
    assertEquals(10, retrieved.rounds());
  }

  @Test
  void testUpdatePlayerWithDuplicateKey() {
    // Create two games with different players
    GameDto game1 = createMinimalGameWithPlayer("Carlsen", "Magnus");
    game1 = gamesService.addGame(databaseId, game1);
    int player1Id = game1.whitePlayer().id().intValue();

    GameDto game2 = createMinimalGameWithPlayer("Kasparov", "Garry");
    game2 = gamesService.addGame(databaseId, game2);
    int player2Id = game2.whitePlayer().id().intValue();

    // Try to update player2 to match player1's name
    PlayerDto duplicateUpdate = new PlayerDto((long) player2Id, "Carlsen", "Magnus", null, null);

    assertThrows(
        IllegalArgumentException.class,
        () -> playersService.updatePlayer(databaseId, player2Id, duplicateUpdate),
        "Should throw IllegalArgumentException when updating to a duplicate key");
  }

  @Test
  void testUpdateAnnotatorWithDuplicateKey() {
    // Create two games with different annotators
    GameDto game1 = createMinimalGameWithAnnotator("GM Hikaru Nakamura");
    game1 = gamesService.addGame(databaseId, game1);
    int annotator1Id = game1.annotator().id().intValue();

    GameDto game2 = createMinimalGameWithAnnotator("GM Bobby Fischer");
    game2 = gamesService.addGame(databaseId, game2);
    int annotator2Id = game2.annotator().id().intValue();

    // Try to update annotator2 to match annotator1's name
    AnnotatorDto duplicateUpdate = new AnnotatorDto((long) annotator2Id, "GM Hikaru Nakamura", null, null);

    assertThrows(
        IllegalArgumentException.class,
        () -> annotatorsService.updateAnnotator(databaseId, annotator2Id, duplicateUpdate),
        "Should throw IllegalArgumentException when updating to a duplicate key");
  }

  @Test
  void testSearchTournamentsByName() {
    // Add two games with different tournaments
    gamesService.addGame(databaseId, createComprehensiveGame("Carlsen", "Magnus"));
    gamesService.addGame(databaseId, createMinimalGame());

    // Search for "Candidates" - should match "Candidates Tournament 2024"
    EntitySearchResponse<TournamentDto> response =
        tournamentsService.searchTournaments(
            databaseId, new EntitySearchRequest("Candidates", null, null, null, null, null, null));

    assertEquals(1, response.count());
    assertTrue(response.items().get(0).name().contains("Candidates"));
    assertEquals(1, response.totalCount().intValue());
    assertNotNull(response.metadata());
  }

  @Test
  void testSearchTournamentsEmptyFilter() {
    // Add two games to create two tournaments
    gamesService.addGame(databaseId, createComprehensiveGame("Carlsen", "Magnus"));
    gamesService.addGame(databaseId, createMinimalGame());

    // Empty filter should return all tournaments with games
    EntitySearchResponse<TournamentDto> response =
        tournamentsService.searchTournaments(
            databaseId, new EntitySearchRequest(null, null, null, null, null, null, null));

    assertEquals(2, response.count());
  }

  @Test
  void testSearchTournamentsPagination() {
    gamesService.addGame(databaseId, createComprehensiveGame("Carlsen", "Magnus"));
    gamesService.addGame(databaseId, createMinimalGame());

    // Get first page (limit=1)
    EntitySearchResponse<TournamentDto> page1 =
        tournamentsService.searchTournaments(
            databaseId, new EntitySearchRequest(null, 0, 1, null, null, null, null));
    assertEquals(1, page1.count());
    assertEquals(2, page1.totalCount().intValue());

    // Get second page
    EntitySearchResponse<TournamentDto> page2 =
        tournamentsService.searchTournaments(
            databaseId, new EntitySearchRequest(null, 1, 1, null, null, null, null));
    assertEquals(1, page2.count());

    // Different tournaments on each page
    assertNotEquals(page1.items().get(0).id(), page2.items().get(0).id());
  }

  @Test
  void testSearchPlayersByName() {
    gamesService.addGame(databaseId, createComprehensiveGame("Carlsen", "Magnus"));
    gamesService.addGame(databaseId, createMinimalGame());

    // Search for "Car" prefix - should match Carlsen and Caruana
    EntitySearchResponse<PlayerDto> response =
        playersService.searchPlayers(
            databaseId, new EntitySearchRequest("Car", null, null, null, null, null, null));

    assertEquals(2, response.count());
  }

  @Test
  void testSearchPlayersSorted() {
    gamesService.addGame(databaseId, createComprehensiveGame("Carlsen", "Magnus"));
    gamesService.addGame(databaseId, createMinimalGame());

    // Search all players sorted by name ascending
    EntitySearchResponse<PlayerDto> ascResponse =
        playersService.searchPlayers(
            databaseId, new EntitySearchRequest(null, null, null, "name", null, null, null));

    // Search all players sorted by name descending
    EntitySearchResponse<PlayerDto> descResponse =
        playersService.searchPlayers(
            databaseId, new EntitySearchRequest(null, null, null, "-name", null, null, null));

    assertTrue(ascResponse.count() > 1);
    assertEquals(ascResponse.count(), descResponse.count());

    // First item in ascending should be last in descending
    assertEquals(
        ascResponse.items().get(0).lastName(),
        descResponse.items().get(descResponse.count() - 1).lastName());
  }

  @Test
  void testSearchPlayersNoResults() {
    gamesService.addGame(databaseId, createComprehensiveGame("Carlsen", "Magnus"));

    // Search for non-existent player
    EntitySearchResponse<PlayerDto> response =
        playersService.searchPlayers(
            databaseId, new EntitySearchRequest("Zzzzz", null, null, null, null, null, null));

    assertEquals(0, response.count());
    assertEquals(0, response.totalCount().intValue());
  }

  /**
   * Creates a comprehensive game with all fields set, including moves, variations, and
   * annotations.
   */
  private GameDto createComprehensiveGame(String whiteLastName, String whiteFirstName) {
    return new GameDto(
        null,
        "game",
        new PlayerDto(null, whiteLastName, whiteFirstName, null, null),
        2863,
        new PlayerDto(null, "Caruana", "Fabiano", null, null),
        2832,
        new TeamDto(null, "Team Norway", 1, false, 2024, "NOR", null, null),
        new TeamDto(null, "Team USA", 2, false, 2024, "USA", null, null),
        GameResult.WHITE_WINS,
        new Date(2024, 3, 15),
        "C42",
        7,
        1,
        NAG.WHITE_MODERATE_ADVANTAGE,
        new TournamentDto(
            null,
            "Candidates Tournament 2024",
            new Date(2024, 3, 1),
            new Date(2024, 3, 25),
            "Toronto",
            "CAN",
            22,
            14,
            "tournament",
            "classical",
            true,
            false,
            null, null, null),
        new SourceDto(
            null,
            "FIDE Live Games",
            "chess.com",
            new Date(2024, 3, 15),
            new Date(2024, 3, 15),
            1,
            null,
            null, null),
        new AnnotatorDto(null, "GM Hikaru Nakamura", null, null),
        null,
        new GameMovesDto(
            "1. e4 e5 {The King's Pawn opening.} (1... c5 {Sicilian Defense}) "
                + "2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 6. Re1 b5 "
                + "7. Bb3 d6 8. c3 O-O 9. h3 Nb8 10. d4 Nbd7 1-0"),
        null, null, null);
  }

  /**
   * Creates a minimal game with only required fields, using different players and tournament than
   * the first game.
   */
  private GameDto createMinimalGame() {
    return new GameDto(
        null,
        "game",
        new PlayerDto(null, "Kasparov", "Garry", null, null),
        null,
        new PlayerDto(null, "Karpov", "Anatoly", null, null),
        null,
        null,
        null,
        GameResult.DRAW,
        new Date(2024, 5, 10),
        null,
        null,
        null,
        null,
        new TournamentDto(
            null,
            "World Championship 1984",
            new Date(1984, 9, 10),
            new Date(1985, 2, 15),
            "Moscow",
            "URS",
            null,
            null,
            null,
            null,
            null,
            null,
            null, null, null),
        new SourceDto(null, "ChessBase Database", "ChessBase", null, null, null, null, null, null),
        null,
        null,
        new GameMovesDto("1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 *"),
        null, null, null);
  }

  /** Creates a minimal game with a specific tournament. */
  private GameDto createMinimalGameWithTournament(TournamentDto tournament) {
    return new GameDto(
        null,
        "game",
        new PlayerDto(null, "Doe", "John", null, null),
        null,
        new PlayerDto(null, "Doe", "Jane", null, null),
        null,
        null,
        null,
        GameResult.DRAW,
        new Date(2024, 1, 1),
        null,
        null,
        null,
        null,
        tournament,
        new SourceDto(null, "Test Source", "Test", null, null, null, null, null, null),
        null,
        null,
        new GameMovesDto("1. e4 e5 *"),
        null, null, null);
  }

  /** Creates a minimal game with a specific white player. */
  private GameDto createMinimalGameWithPlayer(String lastName, String firstName) {
    return new GameDto(
        null,
        "game",
        new PlayerDto(null, lastName, firstName, null, null),
        null,
        new PlayerDto(null, "Opponent", "Test", null, null),
        null,
        null,
        null,
        GameResult.DRAW,
        new Date(2024, 1, 1),
        null,
        null,
        null,
        null,
        new TournamentDto(null, "Test Event", new Date(2024, 1, 1), null, "Test City", null, null, null, null, null, null, null, null, null, null),
        new SourceDto(null, "Test Source", "Test", null, null, null, null, null, null),
        null,
        null,
        new GameMovesDto("1. e4 e5 *"),
        null, null, null);
  }

  /** Creates a minimal game with a specific annotator. */
  private GameDto createMinimalGameWithAnnotator(String annotatorName) {
    return new GameDto(
        null,
        "game",
        new PlayerDto(null, "Player", "Test", null, null),
        null,
        new PlayerDto(null, "Opponent", "Test", null, null),
        null,
        null,
        null,
        GameResult.DRAW,
        new Date(2024, 1, 1),
        null,
        null,
        null,
        null,
        new TournamentDto(null, "Test Event", new Date(2024, 1, 1), null, "Test City", null, null, null, null, null, null, null, null, null, null),
        new SourceDto(null, "Test Source", "Test", null, null, null, null, null, null),
        new AnnotatorDto(null, annotatorName, null, null),
        null,
        new GameMovesDto("1. e4 e5 *"),
        null, null, null);
  }
}
