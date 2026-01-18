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

    // ========== Tests for Generic → Storage Conversion ==========

    @Test
    public void testConvertSingleNAGToStorage() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        node.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));

        // Before conversion: should have NAGAnnotation
        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof NAGAnnotation);

        AnnotationConverter.convertToStorageAnnotations(moves);

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

        AnnotationConverter.convertToStorageAnnotations(moves);

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

        AnnotationConverter.convertToStorageAnnotations(moves);

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

        AnnotationConverter.convertToStorageAnnotations(moves);

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

        AnnotationConverter.convertToStorageAnnotations(moves);

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

        AnnotationConverter.convertToStorageAnnotations(moves);

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
        AnnotationConverter.convertToStorageAnnotations(moves);

        assertEquals(0, moves.countAnnotations());
    }

    @Test
    public void testConvertGameWithNoAnnotations() {
        GameMovesModel moves = new GameMovesModel();
        moves.root().addMove(E2, E4);
        moves.root().mainNode().addMove(E7, E5);

        // Should not throw exception
        AnnotationConverter.convertToStorageAnnotations(moves);

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

        AnnotationConverter.convertToStorageAnnotations(moves);

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

        AnnotationConverter.convertToStorageAnnotations(moves);

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

        AnnotationConverter.convertToStorageAnnotations(moves);

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

        AnnotationConverter.convertToStorageAnnotations(moves);

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
        AnnotationConverter.convertToStorageAnnotations(moves);
        assertEquals(2, node.getAnnotations().size());

        Annotations firstResult = new Annotations();
        firstResult.addAll(node.getAnnotations());

        // Second conversion should have no effect
        AnnotationConverter.convertToStorageAnnotations(moves);
        assertEquals(2, node.getAnnotations().size());

        // Annotations should be identical
        for (int i = 0; i < firstResult.size(); i++) {
            assertEquals(firstResult.get(i), node.getAnnotations().get(i));
        }
    }

    // ========== Tests for Storage → Generic Conversion ==========

    @Test
    public void testConvertSymbolAnnotationToGeneric() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        
        // Add a SymbolAnnotation with one NAG
        node.addAnnotation(ImmutableSymbolAnnotation.of(NAG.GOOD_MOVE, NAG.NONE, NAG.NONE));

        // Before conversion: should have SymbolAnnotation
        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof SymbolAnnotation);

        AnnotationConverter.convertToGenericAnnotations(moves);

        // After conversion: should have NAGAnnotation
        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof NAGAnnotation);
        NAGAnnotation nag = (NAGAnnotation) node.getAnnotations().get(0);
        assertEquals(NAG.GOOD_MOVE, nag.getNag());
    }

    @Test
    public void testConvertSymbolAnnotationWithMultipleNAGsToGeneric() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        
        // Add a SymbolAnnotation with all three NAG types
        node.addAnnotation(ImmutableSymbolAnnotation.of(NAG.GOOD_MOVE, NAG.WITH_THE_IDEA, NAG.WHITE_SLIGHT_ADVANTAGE));

        AnnotationConverter.convertToGenericAnnotations(moves);

        // After conversion: should have 3 NAGAnnotations
        assertEquals(3, node.getAnnotations().size());
        
        boolean hasGoodMove = false;
        boolean hasWithTheIdea = false;
        boolean hasWhiteAdvantage = false;
        
        for (Annotation annotation : node.getAnnotations()) {
            assertTrue(annotation instanceof NAGAnnotation);
            NAG nag = ((NAGAnnotation) annotation).getNag();
            if (nag == NAG.GOOD_MOVE) hasGoodMove = true;
            if (nag == NAG.WITH_THE_IDEA) hasWithTheIdea = true;
            if (nag == NAG.WHITE_SLIGHT_ADVANTAGE) hasWhiteAdvantage = true;
        }
        
        assertTrue("Should have GOOD_MOVE", hasGoodMove);
        assertTrue("Should have WITH_THE_IDEA", hasWithTheIdea);
        assertTrue("Should have WHITE_SLIGHT_ADVANTAGE", hasWhiteAdvantage);
    }

    @Test
    public void testConvertTextAfterMoveToGeneric() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        node.addAnnotation(ImmutableTextAfterMoveAnnotation.of("Great move"));

        // Before conversion
        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof TextAfterMoveAnnotation);

        AnnotationConverter.convertToGenericAnnotations(moves);

        // After conversion: should have CommentaryAfterMoveAnnotation
        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof CommentaryAfterMoveAnnotation);
        CommentaryAfterMoveAnnotation commentary = (CommentaryAfterMoveAnnotation) node.getAnnotations().get(0);
        assertEquals("Great move", commentary.getCommentary());
    }

    @Test
    public void testConvertTextBeforeMoveToGeneric() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        node.addAnnotation(ImmutableTextBeforeMoveAnnotation.of("Consider this"));

        // Before conversion
        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof TextBeforeMoveAnnotation);

        AnnotationConverter.convertToGenericAnnotations(moves);

        // After conversion: should have CommentaryBeforeMoveAnnotation
        assertEquals(1, node.getAnnotations().size());
        assertTrue(node.getAnnotations().get(0) instanceof CommentaryBeforeMoveAnnotation);
        CommentaryBeforeMoveAnnotation commentary = (CommentaryBeforeMoveAnnotation) node.getAnnotations().get(0);
        assertEquals("Consider this", commentary.getCommentary());
    }

    @Test
    public void testConvertMixedStorageToGeneric() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);

        // Add storage annotations
        node.addAnnotation(ImmutableSymbolAnnotation.of(NAG.GOOD_MOVE, NAG.NONE, NAG.WHITE_SLIGHT_ADVANTAGE));
        node.addAnnotation(ImmutableTextAfterMoveAnnotation.of("Best opening"));

        AnnotationConverter.convertToGenericAnnotations(moves);

        // Should have 3 annotations: 2 NAGs + 1 commentary
        assertEquals(3, node.getAnnotations().size());

        int nagCount = 0;
        int commentaryCount = 0;

        for (Annotation annotation : node.getAnnotations()) {
            if (annotation instanceof NAGAnnotation) nagCount++;
            if (annotation instanceof CommentaryAfterMoveAnnotation) commentaryCount++;
        }

        assertEquals("Should have 2 NAG annotations", 2, nagCount);
        assertEquals("Should have 1 commentary annotation", 1, commentaryCount);
    }

    @Test
    public void testRoundTripGenericToStorageToGeneric() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);

        // Start with generic annotations
        node.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));
        node.addAnnotation(new NAGAnnotation(NAG.WHITE_SLIGHT_ADVANTAGE));
        node.addAnnotation(new CommentaryAfterMoveAnnotation("Excellent choice"));

        // Convert to storage
        AnnotationConverter.convertToStorageAnnotations(moves);
        assertEquals(2, node.getAnnotations().size()); // SymbolAnnotation + TextAfterMoveAnnotation

        // Convert back to generic
        AnnotationConverter.convertToGenericAnnotations(moves);
        assertEquals(3, node.getAnnotations().size()); // 2 NAGs + 1 commentary

        // Verify we got back the same annotations
        boolean hasGoodMove = false;
        boolean hasWhiteAdvantage = false;
        boolean hasCommentary = false;

        for (Annotation annotation : node.getAnnotations()) {
            if (annotation instanceof NAGAnnotation) {
                NAG nag = ((NAGAnnotation) annotation).getNag();
                if (nag == NAG.GOOD_MOVE) hasGoodMove = true;
                if (nag == NAG.WHITE_SLIGHT_ADVANTAGE) hasWhiteAdvantage = true;
            }
            if (annotation instanceof CommentaryAfterMoveAnnotation) {
                assertEquals("Excellent choice", ((CommentaryAfterMoveAnnotation) annotation).getCommentary());
                hasCommentary = true;
            }
        }

        assertTrue(hasGoodMove);
        assertTrue(hasWhiteAdvantage);
        assertTrue(hasCommentary);
    }

    @Test
    public void testConvertToGenericWithVariations() {
        GameMovesModel moves = new GameMovesModel();

        // Main line
        GameMovesModel.Node e4 = moves.root().addMove(E2, E4);
        e4.addAnnotation(ImmutableSymbolAnnotation.of(NAG.GOOD_MOVE, NAG.NONE, NAG.NONE));
        e4.addAnnotation(ImmutableTextAfterMoveAnnotation.of("King's pawn"));

        GameMovesModel.Node e5 = e4.addMove(E7, E5);
        e5.addAnnotation(ImmutableSymbolAnnotation.of(NAG.DUBIOUS_MOVE, NAG.NONE, NAG.NONE));

        // Variation
        GameMovesModel.Node c5 = e4.addMove(C7, C5);
        c5.addAnnotation(ImmutableSymbolAnnotation.of(NAG.VERY_GOOD_MOVE, NAG.NONE, NAG.NONE));
        c5.addAnnotation(ImmutableTextAfterMoveAnnotation.of("Sicilian"));

        AnnotationConverter.convertToGenericAnnotations(moves);

        // Check main line
        assertEquals(2, e4.getAnnotations().size());
        assertTrue(e4.getAnnotations().get(0) instanceof NAGAnnotation);
        assertTrue(e4.getAnnotations().get(1) instanceof CommentaryAfterMoveAnnotation);

        assertEquals(1, e5.getAnnotations().size());
        assertTrue(e5.getAnnotations().get(0) instanceof NAGAnnotation);

        // Check variation
        assertEquals(2, c5.getAnnotations().size());
        assertTrue(c5.getAnnotations().get(0) instanceof NAGAnnotation);
        assertTrue(c5.getAnnotations().get(1) instanceof CommentaryAfterMoveAnnotation);
    }

    @Test
    public void testConvertToGenericLeavesOtherAnnotationsUntouched() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);

        // Add storage annotations that should be converted
        SymbolAnnotation symbol = ImmutableSymbolAnnotation.of(NAG.GOOD_MOVE, NAG.NONE, NAG.NONE);
        TextAfterMoveAnnotation textAfter = ImmutableTextAfterMoveAnnotation.of("Test");
        
        node.addAnnotation(symbol);
        node.addAnnotation(textAfter);

        // Add a storage annotation that has no generic equivalent (should be left alone)
        GraphicalSquaresAnnotation graphical = ImmutableGraphicalSquaresAnnotation.of(
            java.util.Arrays.asList(
                ImmutableSquare.of(GraphicalAnnotationColor.GREEN, 0x10)
            )
        );
        node.addAnnotation(graphical);

        AnnotationConverter.convertToGenericAnnotations(moves);

        // Should have: NAGAnnotation + CommentaryAfterMoveAnnotation + GraphicalSquaresAnnotation
        assertEquals(3, node.getAnnotations().size());

        boolean hasNAG = false;
        boolean hasCommentary = false;
        boolean hasGraphical = false;

        for (Annotation annotation : node.getAnnotations()) {
            if (annotation instanceof NAGAnnotation) hasNAG = true;
            if (annotation instanceof CommentaryAfterMoveAnnotation) hasCommentary = true;
            if (annotation instanceof GraphicalSquaresAnnotation) hasGraphical = true;
        }

        assertTrue("Should have NAG", hasNAG);
        assertTrue("Should have commentary", hasCommentary);
        assertTrue("Should have graphical annotation unchanged", hasGraphical);
    }

    @Test
    public void testConvertToGenericEmptyGame() {
        GameMovesModel moves = new GameMovesModel();

        // Should not throw exception
        AnnotationConverter.convertToGenericAnnotations(moves);

        assertEquals(0, moves.countAnnotations());
    }

    @Test
    public void testConvertToGenericSymbolAnnotationWithOnlyNONE() {
        GameMovesModel moves = new GameMovesModel();
        GameMovesModel.Node node = moves.root().addMove(E2, E4);
        
        // Add a SymbolAnnotation with all NONE NAGs
        node.addAnnotation(ImmutableSymbolAnnotation.of(NAG.NONE, NAG.NONE, NAG.NONE));

        AnnotationConverter.convertToGenericAnnotations(moves);

        // Should have no annotations (all were NONE)
        assertEquals(0, node.getAnnotations().size());
    }
}
