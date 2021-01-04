package se.yarin.cbhlib.annotations;

import org.junit.Test;
import se.yarin.cbhlib.exceptions.ChessBaseInvalidDataException;
import se.yarin.cbhlib.exceptions.ChessBaseUnsupportedException;
import se.yarin.cbhlib.ResourceLoader;
import se.yarin.cbhlib.moves.MovesSerializer;
import se.yarin.cbhlib.util.GameGenerator;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.annotations.Annotation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static se.yarin.chess.Chess.*;

public class AnnotationsSerializerTest {
    @Test
    public void serializeSimpleAnnotations() throws IOException, ChessBaseInvalidDataException {
        GameMovesModel model = new GameMovesModel();
        model.root().addMove(E2, E4).addMove(E7, E5).addMove(G1, F3).addMove(B8, C6)
                .addMove(F1, C4).addMove(G8, F6).addMove(F3, G5).addMove(D7, D5)
                .addMove(E4, D5).addMove(C6, A5).addMove(C4, B3).addMove(D8, D5);
        List<GameMovesModel.Node> nodes = model.getAllNodes();

        ByteBuffer buf = ResourceLoader.loadResource("simpleannotations.annotations.bin");
        AnnotationsSerializer.deserializeAnnotations(buf, model);
        assertEquals(0, nodes.get(0).getAnnotations().size());
        assertEquals("m!", nodes.get(1).getAnnotations().format("m", true));
        assertEquals("m?", nodes.get(2).getAnnotations().format("m", true));
        assertEquals("m { Capture }", nodes.get(7).getAnnotations().format("m", true));

        ByteBuffer after = AnnotationsSerializer.serializeAnnotations(3, model);
        buf.position(0);
        after.position(0);
        assertTrue(buf.equals(after));
    }

    @Test
    public void serializeSimpleAnnotationsWithGame() throws IOException, ChessBaseInvalidDataException, ChessBaseUnsupportedException {
        ByteBuffer annoBuf = ResourceLoader.loadResource("simpleannotations.annotations.bin");
        ByteBuffer movesBuf = ResourceLoader.loadResource("simpleannotations.moves.bin");
        GameMovesModel model = MovesSerializer.deserializeMoves(movesBuf);
        AnnotationsSerializer.deserializeAnnotations(annoBuf, model);
        assertEquals("1.e4! c5? 2.Nf3 zugzwang d6 only move 3.d4 unclear cxd4 w/ initiative 4.Nxd4 { Capture } Nf6 5.Nc3 { Fianchetto } g6 6.Be3 +- Better is... Bg7 7.f3 O-O 8.Qd2 Nc6 9.O-O-O d5", model.toString());

        ByteBuffer after = AnnotationsSerializer.serializeAnnotations(3, model);
        annoBuf.position(0);
        after.position(0);
        assertTrue(annoBuf.equals(after));
    }

    @Test
    public void serializeCommentsInVariations() throws IOException, ChessBaseInvalidDataException, ChessBaseUnsupportedException {
        ByteBuffer annoBuf = ResourceLoader.loadResource("commentsinvariations.annotations.bin");
        ByteBuffer movesBuf = ResourceLoader.loadResource("commentsinvariations.moves.bin");
        GameMovesModel model = MovesSerializer.deserializeMoves(movesBuf);
        AnnotationsSerializer.deserializeAnnotations(annoBuf, model);
        assertEquals("{ Pre-game comment } 1.e4 { After first move comment } e5 2.Nf3 Nc6 3.Bb5 ({ Pre-variant comment } 3.Bc4 d6 4.O-O Nf6 { bla bla } 5.Ng5!) 3...a6 4.Ba4 Nf6 5.O-O Be7 (5...Nxe4!? { may lead to open spanish }) 6.Re1 O-O { This is a really long comment that should cause multiple line overflows to be necessary. This is only to test the robustness of the move generator in opencbm. But it should handle it very easily. Otherwise I'm not much of a programmer! } 7.d3 { Game is over }", model.toString());

        ByteBuffer after = AnnotationsSerializer.serializeAnnotations(9, model);
        annoBuf.position(0);
        after.position(0);
        assertTrue(annoBuf.equals(after));
    }

    @Test
    public void serializeGraphicalAnnotations() throws IOException, ChessBaseInvalidDataException {
        GameMovesModel model = new GameMovesModel();
        model.root().addMove(E2, E4).addMove(E7, E5).addMove(G1, F3).addMove(B8, C6)
                .addMove(F1, C4).addMove(G8, F6).addMove(F3, G5).addMove(D7, D5)
                .addMove(E4, D5).addMove(C6, A5).addMove(C4, B3);
        List<GameMovesModel.Node> nodes = model.getAllNodes();

        ByteBuffer buf = ResourceLoader.loadResource("graphicalannotations.annotations.bin");
        AnnotationsSerializer.deserializeAnnotations(buf, model);

        GraphicalSquaresAnnotation sqAnno = nodes.get(2).getAnnotations().getByClass(GraphicalSquaresAnnotation.class);
        Collection<GraphicalSquaresAnnotation.Square> sq = sqAnno.getSquares();
        assertTrue(sq.contains(new GraphicalSquaresAnnotation.Square(GraphicalAnnotationColor.GREEN, G5)));
        assertTrue(sq.contains(new GraphicalSquaresAnnotation.Square(GraphicalAnnotationColor.GREEN, H5)));
        assertTrue(sq.contains(new GraphicalSquaresAnnotation.Square(GraphicalAnnotationColor.YELLOW, B3)));
        assertTrue(sq.contains(new GraphicalSquaresAnnotation.Square(GraphicalAnnotationColor.YELLOW, C3)));
        assertTrue(sq.contains(new GraphicalSquaresAnnotation.Square(GraphicalAnnotationColor.YELLOW, E5)));
        assertTrue(sq.contains(new GraphicalSquaresAnnotation.Square(GraphicalAnnotationColor.RED, E7)));
        assertTrue(sq.contains(new GraphicalSquaresAnnotation.Square(GraphicalAnnotationColor.RED, F7)));

        GraphicalArrowsAnnotation arrowAnno = nodes.get(2).getAnnotations().getByClass(GraphicalArrowsAnnotation.class);
        Collection<GraphicalArrowsAnnotation.Arrow> a = arrowAnno.getArrows();
        assertTrue(a.contains(new GraphicalArrowsAnnotation.Arrow(GraphicalAnnotationColor.GREEN, D8, A5)));
        assertTrue(a.contains(new GraphicalArrowsAnnotation.Arrow(GraphicalAnnotationColor.RED, B8, C6)));
        assertTrue(a.contains(new GraphicalArrowsAnnotation.Arrow(GraphicalAnnotationColor.YELLOW, E4, E5)));
        assertTrue(a.contains(new GraphicalArrowsAnnotation.Arrow(GraphicalAnnotationColor.GREEN, G1, F3)));
        assertTrue(a.contains(new GraphicalArrowsAnnotation.Arrow(GraphicalAnnotationColor.YELLOW, D2, D3)));

        ByteBuffer after = AnnotationsSerializer.serializeAnnotations(13, model);
        buf.position(0);
        after.position(0);
        assertTrue(buf.equals(after));
    }

    @Test
    public void testDeserializeInvalidAnnotation() {
        WebLinkAnnotation before = new WebLinkAnnotation("myuri", "some text");
        ByteBuffer buf = ByteBuffer.allocate(1024);
        AnnotationsSerializer.serializeAnnotation(before, buf);
        buf.flip();
        buf.limit(buf.limit() - 1); // cut last character
        Annotation annotation = AnnotationsSerializer.deserializeAnnotation(buf);
        assertTrue(annotation instanceof InvalidAnnotation);
    }

    @Test
    public void serializeDeserializeGeneratedAnnotations() throws ChessBaseInvalidDataException, ChessBaseUnsupportedException {
        GameGenerator gameGenerator = new GameGenerator();
        for (int noMoves = 10; noMoves < 80; noMoves+=3) {
            // Create a random game, make two copies of it
            // Then add random annotations to one copy; ensure the annotations are different
            // Than serialize and deserialize the annotations from the first copy into the second
            // and ensure the annotations now match
            GameMovesModel inputMoves = gameGenerator.getRandomGameMoves(noMoves);
            gameGenerator.addRandomVariationMoves(inputMoves, noMoves*2);
            GameMovesModel compareMoves = new GameMovesModel(inputMoves);
            gameGenerator.addRandomAnnotations(inputMoves, noMoves / 2);
            assertFalse(annotationsEqual(inputMoves.root(), compareMoves.root()));

            ByteBuffer buf = AnnotationsSerializer.serializeAnnotations(1, inputMoves);
            AnnotationsSerializer.deserializeAnnotations(buf, compareMoves);
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
