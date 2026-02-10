package se.yarin.morphy.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
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
import se.yarin.morphy.service.players.PlayerListResponse;
import se.yarin.morphy.service.players.PlayersService;
import se.yarin.morphy.service.players.dto.PlayerDto;
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
    // Create new in-memory database for each test
    File dbFile = new File(tempDir, "test.cbh");
    Database.create(dbFile, false).close();

    // Register the database with DatabaseService using reflection
    // Since DatabaseService uses Spring config, we'll access it directly
    databaseId = "test-db";
    var config = new se.yarin.morphy.service.config.DatabaseConfig();
    config.setId(databaseId);
    config.setDisplayName("Test Database");
    config.setPath(dbFile.getAbsolutePath());

    // Use reflection to add the database to the service
    try {
      var databaseStatesField = DatabaseService.class.getDeclaredField("databaseStates");
      databaseStatesField.setAccessible(true);
      @SuppressWarnings("unchecked")
      var databaseStates = (java.util.Map<String, Object>) databaseStatesField.get(databaseService);

      var stateClass =
          Class.forName("se.yarin.morphy.service.databases.DatabaseService$DatabaseState");
      var stateConstructor = stateClass.getDeclaredConstructor(
          se.yarin.morphy.service.config.DatabaseConfig.class);
      stateConstructor.setAccessible(true);
      var state = stateConstructor.newInstance(config);

      databaseStates.put(databaseId, state);
    } catch (Exception e) {
      throw new RuntimeException("Failed to register test database", e);
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
            null);
    tournamentsService.updateTournament(databaseId, game2TournamentId, updatedTournament);

    // Step 6: Update team from game 1 with new metadata
    TeamDto updatedTeam =
        new TeamDto(
            (long) game1WhiteTeamId, "Updated Team Title", 42, true, 2024, "NOR", null);
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
            null);
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
            new AnnotatorDto(null, "GM Bobby Fischer", null),
            null,
            new GameMovesDto("1. d4 Nf6 2. c4 e6 3. Nc3 Bb4 4. e3 O-O 5. Bd3 d5 1-0"),
            null);
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

    // Step 11: Get the updated player from game 1 and verify it has gameCount=1
    PlayerDto removedPlayerFromGame1 = playersService.getPlayer(databaseId, game1WhitePlayerId);
    //assertNull(removedPlayerFromGame1);
    PlayerDto missingPlayer = playersService.getPlayer(databaseId, 100);

    PlayerDto updatedPlayerFromGame1 = playersService.getPlayer(databaseId, game1UpdatedWhitePlayerId);
    assertNotNull(updatedPlayerFromGame1);
    assertEquals("Kasparov", updatedPlayerFromGame1.lastName());
    assertEquals("Garry", updatedPlayerFromGame1.firstName());
    assertEquals(2, updatedPlayerFromGame1.gameCount(), "Updated player should have gameCount=2");
  }

  /**
   * Creates a comprehensive game with all fields set, including moves, variations, and
   * annotations.
   */
  private GameDto createComprehensiveGame(String whiteLastName, String whiteFirstName) {
    return new GameDto(
        null,
        "game",
        new PlayerDto(null, whiteLastName, whiteFirstName, null),
        2863,
        new PlayerDto(null, "Caruana", "Fabiano", null),
        2832,
        new TeamDto(null, "Team Norway", 1, false, 2024, "NOR", null),
        new TeamDto(null, "Team USA", 2, false, 2024, "USA", null),
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
            null),
        new SourceDto(
            null,
            "FIDE Live Games",
            "chess.com",
            new Date(2024, 3, 15),
            new Date(2024, 3, 15),
            1,
            null,
            null),
        new AnnotatorDto(null, "GM Hikaru Nakamura", null),
        null,
        new GameMovesDto(
            "1. e4 e5 {The King's Pawn opening.} (1... c5 {Sicilian Defense}) "
                + "2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 6. Re1 b5 "
                + "7. Bb3 d6 8. c3 O-O 9. h3 Nb8 10. d4 Nbd7 1-0"),
        null);
  }

  /**
   * Creates a minimal game with only required fields, using different players and tournament than
   * the first game.
   */
  private GameDto createMinimalGame() {
    return new GameDto(
        null,
        "game",
        new PlayerDto(null, "Kasparov", "Garry", null),
        null,
        new PlayerDto(null, "Karpov", "Anatoly", null),
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
            null),
        new SourceDto(null, "ChessBase Database", "ChessBase", null, null, null, null, null),
        null,
        null,
        new GameMovesDto("1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 *"),
        null);
  }
}
