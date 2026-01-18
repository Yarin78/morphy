package se.yarin.morphy.games.annotations;

import org.junit.Test;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.NAG;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.Annotations;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;
import se.yarin.chess.annotations.CommentaryBeforeMoveAnnotation;
import se.yarin.chess.annotations.NAGAnnotation;

import static org.junit.Assert.*;
import static se.yarin.chess.Chess.*;

public class AnnotationConverterTest {

    @Test
    public void testConvertSingleNAGAnnotation() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        node.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));

        // Before conversion: should have NAGAnnotation
        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof NAGAnnotation);

        AnnotationConverter.convertAnnotations(moves);

        // After conversion: should have SymbolAnnotation
        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof SymbolAnnotation);
        SymbolAnnotation symbol = (SymbolAnnotation) node.getAnnotations().get(0);
        assertEquals(NAG.GOOD_MOVE, symbol.moveComment());
        assertEquals(NAG.NONE, symbol.lineEvaluation());
        assertEquals(NAG.NONE, symbol.movePrefix());
    }

    @Test
    public void testConvertMultipleNAGsDifferentTypes() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        node.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));           // MOVE_COMMENT
        node.addAnnotation(new NAGAnnotation(NAG.WHITE_SLIGHT_ADVANTAGE)); // LINE_EVALUATION
        node.addAnnotation(new NAGAnnotation(NAG.WITH_THE_IDEA));       // MOVE_PREFIX

        AnnotationConverter.convertAnnotations(moves);

        // After conversion: should have one SymbolAnnotation with all three NAGs
        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof SymbolAnnotation);
        SymbolAnnotation symbol = (SymbolAnnotation) node.getAnnotations().get(0);
        assertEquals(NAG.GOOD_MOVE, symbol.moveComment());
        assertEquals(NAG.WHITE_SLIGHT_ADVANTAGE, symbol.lineEvaluation());
        assertEquals(NAG.WITH_THE_IDEA, symbol.movePrefix());
    }

    @Test
    public void testConvertMultipleNAGsSameType() {
        // When multiple NAGs of the same type exist, only the first should be kept
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        node.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));      // First MOVE_COMMENT
        node.addAnnotation(new NAGAnnotation(NAG.DUBIOUS_MOVE));   // Second MOVE_COMMENT (should be dropped)

        AnnotationConverter.convertAnnotations(moves);

        // Should keep only the first NAG
        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof SymbolAnnotation);
        SymbolAnnotation symbol = (SymbolAnnotation) node.getAnnotations().get(0);
        assertEquals(NAG.GOOD_MOVE, symbol.moveComment());  // First one kept
        assertEquals(NAG.NONE, symbol.lineEvaluation());
        assertEquals(NAG.NONE, symbol.movePrefix());
    }

    @Test
    public void testConvertCommentaryAfterMove() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        node.addAnnotation(new CommentaryAfterMoveAnnotation("This is a great move!"));

        // Before conversion
        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof CommentaryAfterMoveAnnotation);

        AnnotationConverter.convertAnnotations(moves);

        // After conversion: should have TextAfterMoveAnnotation
        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof TextAfterMoveAnnotation);
        TextAfterMoveAnnotation text = (TextAfterMoveAnnotation) node.getAnnotations().get(0);
        assertEquals("This is a great move!", text.text());
    }

    @Test
    public void testConvertCommentaryBeforeMove() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        node.addAnnotation(new CommentaryBeforeMoveAnnotation("Black should be careful here"));

        // Before conversion
        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof CommentaryBeforeMoveAnnotation);

        AnnotationConverter.convertAnnotations(moves);

        // After conversion: should have TextBeforeMoveAnnotation
        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof TextBeforeMoveAnnotation);
        TextBeforeMoveAnnotation text = (TextBeforeMoveAnnotation) node.getAnnotations().get(0);
        assertEquals("Black should be careful here", text.text());
    }

    @Test
    public void testConvertMixedGenericAndStorageAnnotations() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);

        // Add generic annotations
        node.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));
        node.addAnnotation(new CommentaryAfterMoveAnnotation("Best move"));

        // Add storage annotation that should not be converted
        // GraphicalSquaresAnnotation is a storage annotation
        node.addAnnotation(ImmutableSymbolAnnotation.of(NAG.DUBIOUS_MOVE, NAG.NONE, NAG.NONE));

        AnnotationConverter.convertAnnotations(moves);

        // Should have 3 annotations: SymbolAnnotation (from NAG), TextAfterMoveAnnotation, and SymbolAnnotation (pre-existing)
        assertEquals(3, node.getAnnotations().size());

        int symbolCount = 0;
        int textCount = 0;

        for (Annotation annotation : node.getAnnotations()) {
            if (annotation instanceof SymbolAnnotation) symbolCount++;
            if (annotation instanceof TextAfterMoveAnnotation) textCount++;
        }

        assertEquals("Should have 2 SymbolAnnotations", 2, symbolCount);
        assertEquals("Should have 1 TextAfterMoveAnnotation", 1, textCount);
    }

    @Test
    public void testConvertEmptyGame() {
        GameMovesModel moves = new GameMovesModel();

        // Should not throw exception
        AnnotationConverter.convertAnnotations(moves);

        assertEquals(0, moves.countAnnotations());
    }

    @Test
    public void testConvertGameWithNoAnnotations() {
        GameMovesModel moves = new GameMovesModel();
        moves.root().addMove(E2, E4);
        moves.root().mainNode().addMove(E7, E5);

        // Should not throw exception
        AnnotationConverter.convertAnnotations(moves);

        assertEquals(0, moves.countAnnotations());
    }

    @Test
    public void testConvertGameWithVariations() {
        GameMovesModel moves = new GameMovesModel();

        // Main line
        GameMovesModel.Node e4 = moves.root().addMove(E2, E4);
        e4.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));
        e4.addAnnotation(new CommentaryAfterMoveAnnotation("King's pawn opening"));

        GameMovesModel.Node e5 = e4.addMove(E7, E5);
        e5.addAnnotation(new NAGAnnotation(NAG.DUBIOUS_MOVE));

        // Variation
        GameMovesModel.Node c5 = e4.addMove(C7, C5);
        c5.addAnnotation(new NAGAnnotation(NAG.VERY_GOOD_MOVE));
        c5.addAnnotation(new CommentaryAfterMoveAnnotation("Sicilian Defense"));

        AnnotationConverter.convertAnnotations(moves);

        // Check main line annotations
        assertEquals(2, e4.getAnnotations().size());
        assertTrue(e4.getAnnotations().get(0) instanceof SymbolAnnotation);
        assertTrue(e4.getAnnotations().get(1) instanceof TextAfterMoveAnnotation);

        assertEquals(1, e5.getAnnotations().size());
        assertTrue(e5.getAnnotations().get(0) instanceof SymbolAnnotation);

        // Check variation annotations
        assertEquals(2, c5.getAnnotations().size());
        assertTrue(c5.getAnnotations().get(0) instanceof SymbolAnnotation);
        assertTrue(c5.getAnnotations().get(1) instanceof TextAfterMoveAnnotation);
    }

    @Test
    public void testConvertPreservesAllAnnotations() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);

        // Add different annotation types
        node.addAnnotation(new CommentaryBeforeMoveAnnotation("Before"));
        node.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));
        node.addAnnotation(new CommentaryAfterMoveAnnotation("After"));

        AnnotationConverter.convertAnnotations(moves);

        // All annotations should be present (order may vary)
        assertEquals(3, node.getAnnotations().size());

        boolean hasTextBefore = false;
        boolean hasSymbol = false;
        boolean hasTextAfter = false;

        for (Annotation annotation : node.getAnnotations()) {
            if (annotation instanceof TextBeforeMoveAnnotation) hasTextBefore = true;
            if (annotation instanceof SymbolAnnotation) hasSymbol = true;
            if (annotation instanceof TextAfterMoveAnnotation) hasTextAfter = true;
        }

        assertTrue("Should have TextBeforeMoveAnnotation", hasTextBefore);
        assertTrue("Should have SymbolAnnotation", hasSymbol);
        assertTrue("Should have TextAfterMoveAnnotation", hasTextAfter);
    }

    @Test
    public void testConvertOnlyConvertsGenericAnnotations() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);

        // Add storage annotations that should NOT be converted
        SymbolAnnotation symbol = ImmutableSymbolAnnotation.of(NAG.GOOD_MOVE, NAG.NONE, NAG.NONE);
        TextAfterMoveAnnotation textAfter = ImmutableTextAfterMoveAnnotation.of("Already converted");
        TextBeforeMoveAnnotation textBefore = ImmutableTextBeforeMoveAnnotation.of("Already converted");

        node.addAnnotation(symbol);
        node.addAnnotation(textAfter);
        node.addAnnotation(textBefore);

        AnnotationConverter.convertAnnotations(moves);

        // Should still have 3 annotations, unchanged
        assertEquals(3, node.getAnnotations().size());
        assertTrue(node.getAnnotations().contains(symbol));
        assertTrue(node.getAnnotations().contains(textAfter));
        assertTrue(node.getAnnotations().contains(textBefore));
    }

    @Test
    public void testConvertCommentaryWithWhitespace() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);

        // CommentaryAfterMoveAnnotation trims whitespace
        node.addAnnotation(new CommentaryAfterMoveAnnotation("  Whitespace test  "));

        AnnotationConverter.convertAnnotations(moves);

        TextAfterMoveAnnotation text = (TextAfterMoveAnnotation) node.getAnnotations().get(0);
        assertEquals("Whitespace test", text.text()); // Should be trimmed
    }

    @Test
    public void testConvertIdempotency() {
        // Converting twice should have no additional effect
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        node.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));
        node.addAnnotation(new CommentaryAfterMoveAnnotation("Test"));

        // First conversion
        AnnotationConverter.convertAnnotations(moves);
        assertEquals(2, node.getAnnotations().size());

        Annotations firstResult = new Annotations();
        firstResult.addAll(node.getAnnotations());

        // Second conversion should have no effect
        AnnotationConverter.convertAnnotations(moves);
        assertEquals(2, node.getAnnotations().size());

        // Annotations should be identical
        for (int i = 0; i < firstResult.size(); i++) {
            assertEquals(firstResult.get(i), node.getAnnotations().get(i));
        }
    }
}
