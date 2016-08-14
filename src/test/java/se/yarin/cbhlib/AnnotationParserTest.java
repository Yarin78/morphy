package se.yarin.cbhlib;

import org.junit.Test;
import se.yarin.cbhlib.annotations.GraphicalAnnotationColor;
import se.yarin.cbhlib.annotations.GraphicalArrowsAnnotation;
import se.yarin.cbhlib.annotations.GraphicalSquaresAnnotation;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.annotations.Annotations;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static se.yarin.chess.Chess.*;

public class AnnotationParserTest {
    @Test
    public void parseSimpleAnnotations() throws IOException, ChessBaseInvalidDataException {
        ByteBuffer buf = ResourceLoader.loadResource("simpleannotations.annotations.bin");
        Map<Integer, Annotations> anno = AnnotationParser.parseGameAnnotations(buf);
        assertFalse(anno.containsKey(0));
        assertEquals("m!", anno.get(1).format("m", true));
        assertEquals("m?", anno.get(2).format("m", true));
        assertEquals("m { Capture }", anno.get(7).format("m", true));
    }

    @Test
    public void parseSimpleAnnotationsWithGame() throws IOException, ChessBaseInvalidDataException, ChessBaseUnsupportedException {
        ByteBuffer annoBuf = ResourceLoader.loadResource("simpleannotations.annotations.bin");
        ByteBuffer movesBuf = ResourceLoader.loadResource("simpleannotations.moves.bin");
        GameMovesModel model = MovesParser.parseMoveData(movesBuf);
        AnnotationParser.parseGameAnnotations(annoBuf, model);
        assertEquals("1.e4! c5? 2.Nf3 zugzwang d6 only move 3.d4 unclear cxd4 w/ initiative 4.Nxd4 { Capture } Nf6 5.Nc3 { Fianchetto } g6 6.Be3 +- Better is... Bg7 7.f3 O-O 8.Qd2 Nc6 9.O-O-O d5", model.toString());
    }

    @Test
    public void parseCommentsInVariations() throws IOException, ChessBaseInvalidDataException, ChessBaseUnsupportedException {
        ByteBuffer annoBuf = ResourceLoader.loadResource("commentsinvariations.annotations.bin");
        ByteBuffer movesBuf = ResourceLoader.loadResource("commentsinvariations.moves.bin");
        GameMovesModel model = MovesParser.parseMoveData(movesBuf);
        AnnotationParser.parseGameAnnotations(annoBuf, model);
        assertEquals("{ Pre-game comment } 1.e4 { After first move comment } e5 2.Nf3 Nc6 3.Bb5 ({ Pre-variant comment } 3.Bc4 d6 4.O-O Nf6 { bla bla } 5.Ng5!) 3...a6 4.Ba4 Nf6 5.O-O Be7 (5...Nxe4!? { may lead to open spanish }) 6.Re1 O-O { This is a really long comment that should cause multiple line overflows to be necessary. This is only to test the robustness of the move generator in opencbm. But it should handle it very easily. Otherwise I'm not much of a programmer! } 7.d3 { Game is over }", model.toString());
    }

    @Test
    public void parseGraphicalAnnotations() throws IOException, ChessBaseInvalidDataException {
        ByteBuffer buf = ResourceLoader.loadResource("graphicalannotations.annotations.bin");
        Map<Integer, Annotations> anno = AnnotationParser.parseGameAnnotations(buf);

        GraphicalSquaresAnnotation sqAnno = anno.get(2).getAnnotation(GraphicalSquaresAnnotation.class);
        Collection<GraphicalSquaresAnnotation.Square> sq = sqAnno.getSquares();
        assertTrue(sq.contains(new GraphicalSquaresAnnotation.Square(GraphicalAnnotationColor.GREEN, G5)));
        assertTrue(sq.contains(new GraphicalSquaresAnnotation.Square(GraphicalAnnotationColor.GREEN, H5)));
        assertTrue(sq.contains(new GraphicalSquaresAnnotation.Square(GraphicalAnnotationColor.YELLOW, B3)));
        assertTrue(sq.contains(new GraphicalSquaresAnnotation.Square(GraphicalAnnotationColor.YELLOW, C3)));
        assertTrue(sq.contains(new GraphicalSquaresAnnotation.Square(GraphicalAnnotationColor.YELLOW, E5)));
        assertTrue(sq.contains(new GraphicalSquaresAnnotation.Square(GraphicalAnnotationColor.RED, E7)));
        assertTrue(sq.contains(new GraphicalSquaresAnnotation.Square(GraphicalAnnotationColor.RED, F7)));

        GraphicalArrowsAnnotation arrowAnno = anno.get(2).getAnnotation(GraphicalArrowsAnnotation.class);
        Collection<GraphicalArrowsAnnotation.Arrow> a = arrowAnno.getArrows();
        assertTrue(a.contains(new GraphicalArrowsAnnotation.Arrow(GraphicalAnnotationColor.GREEN, D8, A5)));
        assertTrue(a.contains(new GraphicalArrowsAnnotation.Arrow(GraphicalAnnotationColor.RED, B8, C6)));
        assertTrue(a.contains(new GraphicalArrowsAnnotation.Arrow(GraphicalAnnotationColor.YELLOW, E4, E5)));
        assertTrue(a.contains(new GraphicalArrowsAnnotation.Arrow(GraphicalAnnotationColor.GREEN, G1, F3)));
        assertTrue(a.contains(new GraphicalArrowsAnnotation.Arrow(GraphicalAnnotationColor.YELLOW, D2, D3)));
    }
}
