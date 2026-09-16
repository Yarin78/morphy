package se.yarin.morphy.cli.opening;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Move;
import se.yarin.chess.NAG;
import se.yarin.morphy.Database;
import se.yarin.morphy.games.annotations.ImmutableTextAfterMoveAnnotation;
import se.yarin.morphy.games.annotations.SymbolAnnotation;
import se.yarin.morphy.games.annotations.TextAfterMoveAnnotation;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static se.yarin.chess.Chess.B1;
import static se.yarin.chess.Chess.B5;
import static se.yarin.chess.Chess.B8;
import static se.yarin.chess.Chess.C3;
import static se.yarin.chess.Chess.C4;
import static se.yarin.chess.Chess.C6;
import static se.yarin.chess.Chess.D2;
import static se.yarin.chess.Chess.D4;
import static se.yarin.chess.Chess.D6;
import static se.yarin.chess.Chess.D7;
import static se.yarin.chess.Chess.E2;
import static se.yarin.chess.Chess.E4;
import static se.yarin.chess.Chess.E5;
import static se.yarin.chess.Chess.E7;
import static se.yarin.chess.Chess.F1;
import static se.yarin.chess.Chess.F3;
import static se.yarin.chess.Chess.F6;
import static se.yarin.chess.Chess.G1;
import static se.yarin.chess.Chess.G8;

/**
 * Covers {@link OpeningRepertoireCache#recordGame} and {@link OpeningRepertoireCache#summarize},
 * the aggregation used by {@code morphy summarize-opening}. The repertoire fixture below
 * represents this White line:
 *
 * <pre>
 * 1. e4 e5 2. Nf3 Nc6 3. Bb5      (main line)
 * 1. e4 e5 2. Nf3 Nf6!? 3. Bc4    (a known, flagged sideline)
 * 1. d4 d5                        (a second branch nothing ever plays into)
 * </pre>
 */
public class OpeningRepertoireCacheTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private OpeningRepertoireCache buildRepertoire() throws IOException {
    GameMovesModel moves = new GameMovesModel();
    GameMovesModel.Node afterE4 = moves.root().addMove(E2, E4);
    afterE4.addAnnotation(
        ImmutableTextAfterMoveAnnotation.builder().text("Interesting game!").build());
    GameMovesModel.Node afterE5 = afterE4.addMove(E7, E5);
    GameMovesModel.Node afterNf3 = afterE5.addMove(G1, F3);
    GameMovesModel.Node afterNc6 = afterNf3.addMove(B8, C6);
    GameMovesModel.Node afterNf6 = afterNf3.addMove(G8, F6);
    afterNf6.addAnnotation(SymbolAnnotation.of(NAG.GOOD_MOVE));
    afterNc6.addMove(F1, B5); // leaf: end of the main line
    afterNf6.addMove(F1, C4); // leaf: end of the sideline
    GameMovesModel.Node afterD4 = moves.root().addMove(D2, D4);
    afterD4.addMove(D7, D6); // never reached by any recorded game

    GameHeaderModel header = new GameHeaderModel();
    header.setWhite("Repertoire");
    header.setBlack("Book");
    File file = folder.newFile("white-test.cbh");
    try (Database db = Database.create(file, true)) {
      db.addGame(new GameModel(header, moves));
    }
    return OpeningRepertoireCache.load(file);
  }

  private static GameMovesModel playedGame(int... squares) {
    GameMovesModel moves = new GameMovesModel();
    GameMovesModel.Node node = moves.root();
    for (int i = 0; i < squares.length; i += 2) {
      node = node.addMove(squares[i], squares[i + 1]);
    }
    return moves;
  }

  private static void assertMove(int fromSqi, int toSqi, Move move) {
    assertEquals(fromSqi, move.fromSqi());
    assertEquals(toSqi, move.toSqi());
  }

  @Test
  public void summarizeAggregatesPlayedGames() throws IOException {
    OpeningRepertoireCache repertoire = buildRepertoire();
    OpeningRepertoireCache.Entry entry = repertoire.entries().get(0);

    // A: follows the main line to the very end.
    repertoire.recordGame(playedGame(E2, E4, E7, E5, G1, F3, B8, C6, F1, B5), entry);
    // B: follows the known sideline to its end.
    repertoire.recordGame(playedGame(E2, E4, E7, E5, G1, F3, G8, F6, F1, C4), entry);
    // C: a bad move of mine (Nc3 instead of Nf3).
    repertoire.recordGame(playedGame(E2, E4, E7, E5, B1, C3), entry);
    // D: an opponent move that's not in the repertoire at all (d6 instead of Nc6/Nf6).
    repertoire.recordGame(playedGame(E2, E4, E7, E5, G1, F3, D7, D6), entry);

    GameModel summary = repertoire.summarize(entry, false).orElseThrow();
    assertEquals(repertoire.formatTag(entry), summary.header().getGameTag());
    // White/Black/Event must match the entry's own game in the repertoire database.
    assertEquals("Repertoire", summary.header().getWhite());
    assertEquals("Book", summary.header().getBlack());
    assertEquals("", summary.header().getEvent());

    GameMovesModel.Node root = summary.moves().root();
    assertEquals(
        entry.title() + " (4 games recorded)",
        root.getAnnotations().getByClass(TextAfterMoveAnnotation.class).text());
    assertEquals(2, root.children().size());

    // The e4 branch: pre-existing text comment must be gone.
    GameMovesModel.Node afterE4 = root.children().get(0);
    assertMove(E2, E4, afterE4.lastMove());
    assertNull(afterE4.getAnnotations().getByClass(TextAfterMoveAnnotation.class));

    // The never-played d4 branch: kept as a single reference leaf, pruned beyond that.
    GameMovesModel.Node afterD4 = root.children().get(1);
    assertMove(D2, D4, afterD4.lastMove());
    assertFalse(afterD4.hasMoves());
    assertTrue(afterD4.getAnnotations().isEmpty());

    GameMovesModel.Node afterE5 = afterE4.mainNode();
    GameMovesModel.Node afterNf3 = afterE5.mainNode();

    // Position after 1.e4 e5 2.Nf3: main child, known sideline, and one new bad-opponent-move
    // variation.
    assertEquals(3, afterNf3.children().size());
    GameMovesModel.Node afterNc6 = afterNf3.children().get(0);
    assertMove(B8, C6, afterNc6.lastMove());
    GameMovesModel.Node afterNf6 = afterNf3.children().get(1);
    assertMove(G8, F6, afterNf6.lastMove());
    GameMovesModel.Node afterD6 = afterNf3.children().get(2);
    assertMove(D7, D6, afterD6.lastMove());

    // The repertoire's own "!" on the sideline must survive.
    SymbolAnnotation sideline = afterNf6.getAnnotations().getByClass(SymbolAnnotation.class);
    assertEquals(NAG.GOOD_MOVE, sideline.moveComment());

    // The opponent deviation: annotated, but not marked as a bad move of mine.
    TextAfterMoveAnnotation d6Comment = afterD6.getAnnotations().getByClass(TextAfterMoveAnnotation.class);
    assertEquals("Not in repertoire, Played 1 time", d6Comment.text());
    assertNull(afterD6.getAnnotations().getByClass(SymbolAnnotation.class));

    // Position after 1.e4 e5: main child plus one new bad-move-of-mine variation.
    assertEquals(2, afterE5.children().size());
    GameMovesModel.Node afterNc3 = afterE5.children().get(1);
    assertMove(B1, C3, afterNc3.lastMove());
    SymbolAnnotation nc3Symbol = afterNc3.getAnnotations().getByClass(SymbolAnnotation.class);
    assertEquals(NAG.BAD_MOVE, nc3Symbol.moveComment());
    TextAfterMoveAnnotation nc3Comment = afterNc3.getAnnotations().getByClass(TextAfterMoveAnnotation.class);
    assertEquals("Played 1 time", nc3Comment.text());

    // Both leaves at the end of the prepared book were each reached once.
    GameMovesModel.Node afterBb5 = afterNc6.mainNode();
    assertEquals(
        "Reached end of line 1 time",
        afterBb5.getAnnotations().getByClass(TextAfterMoveAnnotation.class).text());
    GameMovesModel.Node afterBc4 = afterNf6.mainNode();
    assertEquals(
        "Reached end of line 1 time",
        afterBc4.getAnnotations().getByClass(TextAfterMoveAnnotation.class).text());
  }

  @Test
  public void annotateAllMovesAlsoCountsMatchingContinuations() throws IOException {
    OpeningRepertoireCache repertoire = buildRepertoire();
    OpeningRepertoireCache.Entry entry = repertoire.entries().get(0);

    repertoire.recordGame(playedGame(E2, E4, E7, E5, G1, F3, B8, C6, F1, B5), entry);
    repertoire.recordGame(playedGame(E2, E4, E7, E5, G1, F3, G8, F6, F1, C4), entry);

    GameModel summary = repertoire.summarize(entry, true).orElseThrow();
    GameMovesModel.Node afterE4 = summary.moves().root().mainNode();
    assertEquals(
        "Played 2 times", afterE4.getAnnotations().getByClass(TextAfterMoveAnnotation.class).text());

    GameMovesModel.Node afterNf3 = afterE4.mainNode().mainNode();
    List<GameMovesModel.Node> children = afterNf3.children();
    assertEquals(
        "Played 1 time",
        children.get(0).getAnnotations().getByClass(TextAfterMoveAnnotation.class).text());
    assertEquals(
        "Played 1 time",
        children.get(1).getAnnotations().getByClass(TextAfterMoveAnnotation.class).text());
  }

  @Test
  public void summarizeIsEmptyWhenNothingRecorded() throws IOException {
    OpeningRepertoireCache repertoire = buildRepertoire();
    OpeningRepertoireCache.Entry entry = repertoire.entries().get(0);
    assertFalse(repertoire.summarize(entry, false).isPresent());
  }
}
