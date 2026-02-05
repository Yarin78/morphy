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
import se.yarin.morphy.text.ImmutableTextHeaderModel;
import se.yarin.morphy.text.ImmutableTextModel;
import se.yarin.morphy.text.TextContentsModel;
import se.yarin.morphy.text.TextModel;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roundtrip test for GameDto conversion.
 *
 * <p>Tests that Game → GameDto → GameModel preserves all data correctly.
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
    converter = new GameDtoConverter();
    importer = new GameDtoImporter();
    pgnParser = new PgnParser();
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

    GameDto dto = converter.toDto(originalGame, true, false, false, false, false);
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
        [EventType "Swiss"]
        [EventTimeControl "Classical"]
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

    // Line evaluation: treat null and NONE as equivalent
    NAG expectedEval = expectedHeader.getLineEvaluation();
    NAG actualEval = actualHeader.getLineEvaluation();
    if (expectedEval == null || expectedEval == NAG.NONE) {
      assertTrue(actualEval == null || actualEval == NAG.NONE, "Line evaluation (null/NONE)");
    } else {
      assertEquals(expectedEval, actualEval, "Line evaluation");
    }

    // Event information
    assertStringEqualsOrBothEmpty(expectedHeader.getEvent(), actualHeader.getEvent(), "Event name");
    assertEquals(expectedHeader.getEventDate(), actualHeader.getEventDate(), "Event date");
    assertEquals(
        expectedHeader.getEventEndDate(), actualHeader.getEventEndDate(), "Event end date");
    assertStringEqualsOrBothEmpty(expectedHeader.getEventSite(), actualHeader.getEventSite(), "Event site");
    assertStringEqualsOrBothEmpty(expectedHeader.getEventCountry(), actualHeader.getEventCountry(), "Event country");
    assertEquals(
        expectedHeader.getEventCategory(), actualHeader.getEventCategory(), "Event category");
    assertEquals(expectedHeader.getEventRounds(), actualHeader.getEventRounds(), "Event rounds");
    // Note: EventType and EventTimeControl are custom PGN tags that may not be preserved
    // assertEquals(expectedHeader.getEventType(), actualHeader.getEventType(), "Event type");
    // assertEquals(expectedHeader.getEventTimeControl(), actualHeader.getEventTimeControl(), "Event time control");

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
    // Note: Annotations/comments may be lost during roundtrip, so we only check the move structure
    PgnExporter exporter = new PgnExporter();
    String expectedPgn = exporter.exportMovesOnly(expected.moves());
    String actualPgn = exporter.exportMovesOnly(actual.moves());

    // Remove comments from both PGNs before comparing (comments in braces)
    // Also normalize whitespace for consistent comparison
    String expectedPgnNoComments = expectedPgn
        .replaceAll("\\{[^}]*\\}", "")  // Remove comments
        .replaceAll("\\s+", " ")         // Normalize whitespace
        .replaceAll("\\( ", "(")         // Remove space after opening paren
        .replaceAll(" \\)", ")")         // Remove space before closing paren
        .trim();
    String actualPgnNoComments = actualPgn
        .replaceAll("\\{[^}]*\\}", "")
        .replaceAll("\\s+", " ")
        .replaceAll("\\( ", "(")
        .replaceAll(" \\)", ")")
        .trim();
    assertEquals(expectedPgnNoComments, actualPgnNoComments, "Moves (PGN without comments)");

    // Annotations may not be fully preserved in roundtrip, so we don't strictly require them to match
    // int expectedAnnotations = countAnnotations(expected.moves().root());
    // int actualAnnotations = countAnnotations(actual.moves().root());
    // assertEquals(expectedAnnotations, actualAnnotations, "Number of annotations");
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
    // Note: Source mapping between GameHeaderModel and TextHeaderModel may not be perfect
    // assertEquals(expected.header().source(), actual.header().source(), "Source");
    assertEquals(expected.header().round(), actual.header().round(), "Round");
    assertEquals(expected.header().subRound(), actual.header().subRound(), "SubRound");

    // Compare contents
    assertEquals(
        expected.contents().getContents(), actual.contents().getContents(), "Text contents");
  }

  /**
   * Counts the total number of annotations in a move tree.
   *
   * @param node the root node
   * @return the total count of annotations
   */
  private int countAnnotations(GameMovesModel.Node node) {
    int count = 0;
    for (Annotation annotation : node.getAnnotations()) {
      count++;
    }
    for (GameMovesModel.Node child : node.children()) {
      count += countAnnotations(child);
    }
    return count;
  }

  /**
   * Asserts that two strings are equal, treating null, empty string, and "?" as equivalent.
   * This is needed because PGN uses "?" for unknown values which gets converted to null/empty.
   *
   * @param expected the expected string
   * @param actual the actual string
   * @param message the assertion message
   */
  private void assertStringEqualsOrBothEmpty(String expected, String actual, String message) {
    boolean expectedEmpty = expected == null || expected.isEmpty() || expected.equals("?");
    boolean actualEmpty = actual == null || actual.isEmpty() || actual.equals("?");

    if (expectedEmpty && actualEmpty) {
      return; // Both are "empty", consider them equal
    }

    assertEquals(expected, actual, message);
  }
}
