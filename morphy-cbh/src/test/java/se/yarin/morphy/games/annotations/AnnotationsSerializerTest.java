package se.yarin.morphy.games.annotations;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.NAG;
import se.yarin.chess.annotations.Annotation;

import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;
import se.yarin.chess.annotations.NAGAnnotation;
import se.yarin.chess.pgn.PgnExporter;
import se.yarin.chess.pgn.PgnFormatOptions;
import se.yarin.morphy.GameGenerator;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.games.moves.MoveSerializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static se.yarin.chess.Chess.*;

public class AnnotationsSerializerTest {
  private final AnnotationsSerializer annotationsSerializer = new AnnotationsSerializer();

  private void pgnAnnotations(@NotNull GameMovesModel model) {
    AnnotationConverter converter = AnnotationConverter.getSimplifiedPgnConverter();
    model.root().traverseDepthFirst(node -> converter.convertToPgn(node.getAnnotations()));
  }

  @Test
  public void serializeSimpleAnnotations() throws IOException, MorphyInvalidDataException {
    GameMovesModel model = new GameMovesModel();
    model
        .root()
        .addMove(E2, E4)
        .addMove(E7, E5)
        .addMove(G1, F3)
        .addMove(B8, C6)
        .addMove(F1, C4)
        .addMove(G8, F6)
        .addMove(F3, G5)
        .addMove(D7, D5)
        .addMove(E4, D5)
        .addMove(C6, A5)
        .addMove(C4, B3)
        .addMove(D8, D5);
    List<GameMovesModel.Node> nodes = model.getAllNodes();

    ByteBuffer buf = ResourceLoader.loadResource(getClass(), "simpleannotations.annotations.bin");
    annotationsSerializer.deserializeAnnotations(buf, model);

    ByteBuffer after = annotationsSerializer.serializeAnnotations(3, model);
    buf.position(0);
    after.position(0);
    assertEquals(buf, after);

    pgnAnnotations(model);
    assertEquals(0, nodes.get(0).getAnnotations().size());
    assertEquals(new NAGAnnotation(NAG.GOOD_MOVE), nodes.get(1).getAnnotations().get(0));
    assertEquals(new NAGAnnotation(NAG.BAD_MOVE), nodes.get(2).getAnnotations().get(0));
    assertEquals(new CommentaryAfterMoveAnnotation("Capture"), nodes.get(7).getAnnotations().get(0));
  }

  @Test
  public void serializeSimpleAnnotationsWithGame() throws IOException, MorphyInvalidDataException {
    ByteBuffer annoBuf =
        ResourceLoader.loadResource(getClass(), "simpleannotations.annotations.bin");
    ByteBuffer movesBuf = ResourceLoader.loadResource(getClass(), "simpleannotations.moves.bin");
    GameMovesModel model = new MoveSerializer().deserializeMoves(movesBuf);
    annotationsSerializer.deserializeAnnotations(annoBuf, model);

    ByteBuffer after = annotationsSerializer.serializeAnnotations(3, model);
    annoBuf.position(0);
    after.position(0);
    assertEquals(annoBuf, after);

    pgnAnnotations(model);
    assertEquals(
            "1. e4! c5? 2. Nf3 zugzwang d6 only move 3. d4 unclear cxd4 w/ initiative 4. Nxd4 { Capture } Nf6 5. Nc3 { Fianchetto } g6 6. Be3 +- Better is... Bg7 7. f3 O-O 8. Qd2 Nc6 9. O-O-O d5",
            model.toString());
  }

  @Test
  public void serializeCommentsInVariations() throws IOException, MorphyInvalidDataException {
    ByteBuffer annoBuf =
        ResourceLoader.loadResource(getClass(), "commentsinvariations.annotations.bin");
    ByteBuffer movesBuf = ResourceLoader.loadResource(getClass(), "commentsinvariations.moves.bin");
    GameMovesModel model = new MoveSerializer().deserializeMoves(movesBuf);
    annotationsSerializer.deserializeAnnotations(annoBuf, model);

    ByteBuffer after = annotationsSerializer.serializeAnnotations(9, model);
    annoBuf.position(0);
    after.position(0);
    assertEquals(annoBuf, after);

    pgnAnnotations(model);
    assertEquals(
            "{ Pre-game comment } 1. e4 { After first move comment } e5 2. Nf3 Nc6 3. Bb5 ({ Pre-variant comment } 3. Bc4 d6 4. O-O Nf6 { bla bla } 5. Ng5!) 3... a6 4. Ba4 Nf6 5. O-O Be7 (5... Nxe4!? { may lead to open spanish }) 6. Re1 O-O { This is a really long comment that should cause multiple line overflows to be necessary. This is only to test the robustness of the move generator in opencbm. But it should handle it very easily. Otherwise I'm not much of a programmer! } 7. d3 { Game is over }",
            model.toString());
  }

  @Test
  public void serializeGraphicalAnnotations() throws IOException, MorphyInvalidDataException {
    GameMovesModel model = new GameMovesModel();
    model
        .root()
        .addMove(E2, E4)
        .addMove(E7, E5)
        .addMove(G1, F3)
        .addMove(B8, C6)
        .addMove(F1, C4)
        .addMove(G8, F6)
        .addMove(F3, G5)
        .addMove(D7, D5)
        .addMove(E4, D5)
        .addMove(C6, A5)
        .addMove(C4, B3);
    List<GameMovesModel.Node> nodes = model.getAllNodes();

    ByteBuffer buf =
        ResourceLoader.loadResource(getClass(), "graphicalannotations.annotations.bin");
    annotationsSerializer.deserializeAnnotations(buf, model);

    GraphicalSquaresAnnotation sqAnno =
        nodes.get(2).getAnnotations().getByClass(ImmutableGraphicalSquaresAnnotation.class);
    Collection<GraphicalSquaresAnnotation.Square> sq = sqAnno.squares();
    assertTrue(sq.contains(ImmutableSquare.of(GraphicalAnnotationColor.GREEN, G5)));
    assertTrue(sq.contains(ImmutableSquare.of(GraphicalAnnotationColor.GREEN, H5)));
    assertTrue(sq.contains(ImmutableSquare.of(GraphicalAnnotationColor.YELLOW, B3)));
    assertTrue(sq.contains(ImmutableSquare.of(GraphicalAnnotationColor.YELLOW, C3)));
    assertTrue(sq.contains(ImmutableSquare.of(GraphicalAnnotationColor.YELLOW, E5)));
    assertTrue(sq.contains(ImmutableSquare.of(GraphicalAnnotationColor.RED, E7)));
    assertTrue(sq.contains(ImmutableSquare.of(GraphicalAnnotationColor.RED, F7)));

    GraphicalArrowsAnnotation arrowAnno =
        nodes.get(2).getAnnotations().getByClass(ImmutableGraphicalArrowsAnnotation.class);
    Collection<GraphicalArrowsAnnotation.Arrow> a = arrowAnno.arrows();
    assertTrue(a.contains(ImmutableArrow.of(GraphicalAnnotationColor.GREEN, D8, A5)));
    assertTrue(a.contains(ImmutableArrow.of(GraphicalAnnotationColor.RED, B8, C6)));
    assertTrue(a.contains(ImmutableArrow.of(GraphicalAnnotationColor.YELLOW, E4, E5)));
    assertTrue(a.contains(ImmutableArrow.of(GraphicalAnnotationColor.GREEN, G1, F3)));
    assertTrue(a.contains(ImmutableArrow.of(GraphicalAnnotationColor.YELLOW, D2, D3)));

    ByteBuffer after = annotationsSerializer.serializeAnnotations(13, model);
    buf.position(0);
    after.position(0);
    assertEquals(buf, after);
  }

  @Test
  public void testDeserializeInvalidAnnotation() {
    WebLinkAnnotation before = ImmutableWebLinkAnnotation.of("myuri", "some text");
    ByteBuffer buf = ByteBuffer.allocate(1024);
    AnnotationsSerializer.serializeAnnotation(before, buf);
    buf.flip();
    buf.limit(buf.limit() - 1); // cut last character
    Annotation annotation = AnnotationsSerializer.deserializeAnnotation(buf);
    assertTrue(annotation instanceof InvalidAnnotation);
  }

  @Test
  public void serializeDeserializeGeneratedAnnotations() throws MorphyInvalidDataException {
    GameGenerator gameGenerator = new GameGenerator();
    for (int noMoves = 10; noMoves < 80; noMoves += 3) {
      // Create a random game, make two copies of it
      // Then add random annotations to one copy; ensure the annotations are different
      // Than serialize and deserialize the annotations from the first copy into the second
      // and ensure the annotations now match
      GameMovesModel inputMoves = gameGenerator.getRandomGameMoves(noMoves);
      gameGenerator.addRandomVariationMoves(inputMoves, noMoves * 2);
      GameMovesModel compareMoves = new GameMovesModel(inputMoves);
      gameGenerator.addRandomAnnotations(inputMoves, noMoves / 2);
      assertFalse(annotationsEqual(inputMoves.root(), compareMoves.root()));

      ByteBuffer buf = annotationsSerializer.serializeAnnotations(1, inputMoves);
      annotationsSerializer.deserializeAnnotations(buf, compareMoves);
      assertTrue(annotationsEqual(inputMoves.root(), compareMoves.root()));
    }
  }

  private boolean annotationsEqual(GameMovesModel.Node node1, GameMovesModel.Node node2) {
    if (node1.getAnnotations().size() != node2.getAnnotations().size()) {
      return false;
    }

    for (int i = 0; i < node1.getAnnotations().size(); i++) {
      Annotation a1 = node1.getAnnotations().get(i);
      Annotation a2 = node2.getAnnotations().get(i);
      assertTrue(a1.equals(a2));
    }

    if (node1.children().size() != node2.children().size()) {
      return false;
    }

    for (int i = 0; i < node1.children().size(); i++) {
      if (!annotationsEqual(node1.children().get(i), node2.children().get(i))) {
        return false;
      }
    }

    return true;
  }
}
