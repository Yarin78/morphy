package se.yarin.morphy.service.games.dto;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.pgn.PgnExporter;
import se.yarin.chess.pgn.PgnParser;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseWriteTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.GameAdapter;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.TournamentType;
import se.yarin.morphy.games.annotations.AnnotationConverter;
import se.yarin.morphy.service.tournaments.dto.TournamentDtoConverter;
import se.yarin.morphy.text.ImmutableTextHeaderModel;
import se.yarin.morphy.text.ImmutableTextModel;
import se.yarin.morphy.text.TextContentsModel;
import se.yarin.morphy.text.TextModel;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roundtrip test for GameDto conversion. This is a bit messy
 * since the GameDtoConverter and GameDtoImporter are asymmetric.
 *
 * <p>We actually do PGN -> GameModel -> Game (db store) → GameDto → GameModel
 * and compare the two GameModels.
 */
class GameDtoRoundtripTest {

  @TempDir File tempDir;

  private Database database;
  private GameDtoConverter converter;
  private GameDtoImporter importer;
  private PgnParser pgnParser;

  @BeforeEach
  void setUp() throws IOException {
    File dbPath = new File(tempDir, "test.cbh");
    database = Database.create(dbPath);
    converter = new GameDtoConverter(new TournamentDtoConverter());
    importer = new GameDtoImporter();
    pgnParser = new PgnParser((AnnotationConverter.getRoundTripConverter())::convertToChessBase);
  }

  @AfterEach
  void tearDown() throws IOException {
    if (database != null) {
      database.close();
    }
  }

  @Test
  void testCompleteGameRoundtrip() throws Exception {
    // 1. Create a comprehensive test game with all fields set
    GameModel originalModel = createComprehensiveGameModel();

    int gameId;
    try (DatabaseWriteTransaction txn = new DatabaseWriteTransaction(database)) {
      Game game = txn.addGame(originalModel);
      gameId = game.id();
      txn.commit();
    }

    // 2. Read the game from the database and convert to GameDto
    Game originalGame = database.getGame(gameId);
    GameDto dto =
        converter.toDto(
            originalGame,
            /*includeMoves*/ true,
            /*includeText*/ false,
            /*includeEventDetails*/ true,
            /*includeSourceDetails*/ true,
            /*includeTeamDetails*/ true);

    // 3. Convert GameDto → GameModel
    GameModel recreatedModel = importer.toGameModel(dto);

    // 4. Compare the models
    assertGameModelsEqual(originalModel, recreatedModel);
  }

  @Test
  void testGuidingTextRoundtrip() throws Exception {
    // 1. Create a test text entry
    TextModel originalTextModel = createComprehensiveTextModel();

    int textId;
    try (DatabaseWriteTransaction txn = new DatabaseWriteTransaction(database)) {
      Game text = txn.addText(originalTextModel);
      textId = text.id();
      txn.commit();
    }

    // 2. Read the text from the database and convert to GameDto
    Game originalText = database.getGame(textId);
    GameDto dto =
        converter.toDto(
            originalText,
            /*includeMoves*/ false,
            /*includeText*/ true,
            /*includeEventDetails*/ true,
            /*includeSourceDetails*/ true,
            /*includeTeamDetails*/ false);

    // 3. Convert GameDto → TextModel
    TextModel recreatedTextModel = importer.toTextModel(dto);

    // 4. Compare the models
    assertTextModelsEqual(originalTextModel, recreatedTextModel);
  }

  @Test
  void testMinimalGameRoundtrip() throws Exception {
    // Test a game with only mandatory fields
    GameModel originalModel = createMinimalGameModel();

    int gameId;
    try (DatabaseWriteTransaction txn = new DatabaseWriteTransaction(database)) {
      Game game = txn.addGame(originalModel);
      gameId = game.id();
      txn.commit();
    }

    Game originalGame = database.getGame(gameId);

    GameDto dto = converter.toDto(originalGame, true, false, true, true, false);
    GameModel recreatedModel = importer.toGameModel(dto);

    assertGameModelsEqual(originalModel, recreatedModel);
  }

  /**
   * Creates a comprehensive GameModel with all supported fields set.
   *
   * @return a GameModel with comprehensive data
   */
  private GameModel createComprehensiveGameModel() throws Exception {
    // Use PGN to create a game with moves and variations
    String pgn =
        """
        [Event "Candidates Tournament 2024"]
        [Site "Toronto, CAN"]
        [Date "2024.03.15"]
        [EventDate "2024.03.01"]
        [Round "7"]
        [White "Magnus Carlsen"]
        [Black "Fabiano Caruana"]
        [Result "1-0"]
        [WhiteElo "2863"]
        [BlackElo "2832"]
        [WhiteTeam "Team Norway"]
        [BlackTeam "Team USA"]
        [ECO "C42"]
        [Annotator "GM Hikaru Nakamura"]
        [Source "chess.com"]
        [SourceTitle "FIDE Live Games"]
        [SourceDate "2024.03.15"]
        [EventType "swiss (blitz)"]
        [EventCategory "22"]
        [EventRounds "14"]
        [EventEndDate "2024.03.25"]
        [GameTag "World Championship Qualifier"]

        1. e4 e5 {The Ruy Lopez opening.} (1... c5 {Sicilian Defense})
        2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 6. Re1 b5
        7. Bb3 d6 8. c3 O-O 9. h3 Nb8 10. d4 Nbd7 1-0
        """;

    GameModel model = pgnParser.parseGame(pgn);

    // Add additional fields that PGN doesn't support
    model.header().setSubRound(1);
    model.header().setLineEvaluation(NAG.WHITE_MODERATE_ADVANTAGE);

    return model;
  }

  /**
   * Creates a minimal GameModel with only mandatory fields.
   *
   * @return a minimal GameModel
   */
  private GameModel createMinimalGameModel() throws Exception {
    String pgn =
        """
        [Event "?"]
        [Site "?"]
        [Date "????.??.??"]
        [Round "?"]
        [White "Player One"]
        [Black "Player Two"]
        [Result "*"]

        *
        """;

    return pgnParser.parseGame(pgn);
  }

  /**
   * Creates a comprehensive TextModel for testing text roundtrips.
   *
   * @return a TextModel with comprehensive data
   */
  private TextModel createComprehensiveTextModel() {
    var header =
        ImmutableTextHeaderModel.builder()
            .tournament("Opening Theory Seminar 2024")
            .tournamentDate(new Date(2024, 5, 10))
            .annotator("GM John Nunn")
            .source("ChessBase Magazine")
            .round(3)
            .subRound(2)
            .build();

    TextContentsModel contents = new TextContentsModel();
    contents.setContents(
        "<h1>Introduction to the Ruy Lopez</h1>"
            + "<p>The Ruy Lopez is one of the oldest and most classical of chess openings.</p>"
            + "<p>Named after Spanish bishop Ruy López de Segura, it has been analyzed for centuries.</p>");

    return ImmutableTextModel.builder().header(header).contents(contents).build();
  }

  /**
   * Compares two GameModels for equality, checking all relevant fields.
   *
   * @param expected the expected GameModel
   * @param actual the actual GameModel
   */
  private void assertGameModelsEqual(GameModel expected, GameModel actual) {
    GameHeaderModel expectedHeader = expected.header();
    GameHeaderModel actualHeader = actual.header();

    // Player information
    assertEquals(expectedHeader.getWhite(), actualHeader.getWhite(), "White player name");
    assertEquals(expectedHeader.getBlack(), actualHeader.getBlack(), "Black player name");
    assertEquals(expectedHeader.getWhiteElo(), actualHeader.getWhiteElo(), "White ELO");
    assertEquals(expectedHeader.getBlackElo(), actualHeader.getBlackElo(), "Black ELO");
    assertEquals(expectedHeader.getWhiteTeam(), actualHeader.getWhiteTeam(), "White team");
    assertEquals(expectedHeader.getBlackTeam(), actualHeader.getBlackTeam(), "Black team");

    // Game metadata
    assertEquals(expectedHeader.getResult(), actualHeader.getResult(), "Result");
    assertEquals(expectedHeader.getDate(), actualHeader.getDate(), "Date");
    assertEquals(expectedHeader.getEco(), actualHeader.getEco(), "ECO");
    assertEquals(expectedHeader.getRound(), actualHeader.getRound(), "Round");
    assertEquals(expectedHeader.getSubRound(), actualHeader.getSubRound(), "SubRound");

    assertEquals(expectedHeader.getLineEvaluation(), actualHeader.getLineEvaluation(), "Line evaluation");

    // Event information
    assertEquals(expectedHeader.getEvent(), actualHeader.getEvent(), "Event name");
    assertEquals(expectedHeader.getEventDate(), actualHeader.getEventDate(), "Event date");
    assertEquals(
        expectedHeader.getEventEndDate(), actualHeader.getEventEndDate(), "Event end date");
    assertEquals(expectedHeader.getEventSite(), actualHeader.getEventSite(), "Event site");
    assertEquals(expectedHeader.getEventCountry(), actualHeader.getEventCountry(), "Event country");
    assertEquals(
        expectedHeader.getEventCategory(), actualHeader.getEventCategory(), "Event category");
    assertEquals(expectedHeader.getEventRounds(), actualHeader.getEventRounds(), "Event rounds");
    assertEquals(expectedHeader.getEventType(), actualHeader.getEventType(), "Event type");
    assertEquals(expectedHeader.getEventTimeControl(), actualHeader.getEventTimeControl(), "Event time control");

    // Source information
    assertEquals(
        expectedHeader.getSourceTitle(), actualHeader.getSourceTitle(), "Source title");
    assertEquals(expectedHeader.getSource(), actualHeader.getSource(), "Source publisher");
    assertEquals(expectedHeader.getSourceDate(), actualHeader.getSourceDate(), "Source date");

    // Annotator
    assertEquals(expectedHeader.getAnnotator(), actualHeader.getAnnotator(), "Annotator");

    // Game tag
    assertEquals(expectedHeader.getGameTag(), actualHeader.getGameTag(), "Game tag");

    // Compare moves using PGN export
    PgnExporter exporter = new PgnExporter();
    String expectedPgn = exporter.exportMovesOnly(expected.moves());
    String actualPgn = exporter.exportMovesOnly(actual.moves());

    assertEquals(expectedPgn, actualPgn, "PGN differs");
  }

  /**
   * Compares two TextModels for equality.
   *
   * @param expected the expected TextModel
   * @param actual the actual TextModel
   */
  private void assertTextModelsEqual(TextModel expected, TextModel actual) {
    // Compare headers
    assertEquals(
        expected.header().tournament(), actual.header().tournament(), "Tournament name");
    assertEquals(
        expected.header().tournamentDate(),
        actual.header().tournamentDate(),
        "Tournament date");
    assertEquals(expected.header().annotator(), actual.header().annotator(), "Annotator");
    assertEquals(expected.header().source(), actual.header().source(), "Source");
    assertEquals(expected.header().round(), actual.header().round(), "Round");
    assertEquals(expected.header().subRound(), actual.header().subRound(), "SubRound");

    // Compare contents
    assertEquals(
        expected.contents().getContents(), actual.contents().getContents(), "Text contents");
  }
}
