package se.yarin.morphy.games.annotations;

import org.junit.jupiter.api.Test;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.Annotations;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;
import se.yarin.chess.annotations.CommentaryBeforeMoveAnnotation;
import se.yarin.morphy.entities.Nation;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for instance-based AnnotationConverter methods.
 * Verifies both round-trip and simplified conversion modes.
 */
public class AnnotationConverterInstanceTest {

    @Test
    public void testSimplifiedConverter_plainTextAfterMove() {
        // Given: TextAfterMoveAnnotation with no language
        Annotations annotations = new Annotations();
        annotations.add(ImmutableTextAfterMoveAnnotation.of("This is a comment"));

        // When: Converting to PGN with simplified converter
        AnnotationConverter converter = AnnotationConverter.getSimplifiedPgnConverter();
        converter.convertToPgn(annotations);

        // Then: Should output plain text without tags
        assertEquals(1, annotations.size());
        assertTrue(annotations.get(0) instanceof CommentaryAfterMoveAnnotation);
        assertEquals("This is a comment", ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary());
    }

    @Test
    public void testSimplifiedConverter_textWithLanguage() {
        // Given: TextAfterMoveAnnotation with German language
        Annotations annotations = new Annotations();
        annotations.add(ImmutableTextAfterMoveAnnotation.builder()
                .text("Ein Kommentar")
                .language(Nation.GERMANY)
                .build());

        // When: Converting to PGN with simplified converter
        AnnotationConverter converter = AnnotationConverter.getSimplifiedPgnConverter();
        converter.convertToPgn(annotations);

        // Then: Should output plain text, language info lost
        assertEquals(1, annotations.size());
        assertTrue(annotations.get(0) instanceof CommentaryAfterMoveAnnotation);
        assertEquals("Ein Kommentar", ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary());
    }

    @Test
    public void testSimplifiedConverter_multipleLanguages() {
        // Given: Multiple TextAfterMoveAnnotation with different languages
        Annotations annotations = new Annotations();
        annotations.add(ImmutableTextAfterMoveAnnotation.builder()
                .text("English comment")
                .language(Nation.ENGLAND)
                .build());
        annotations.add(ImmutableTextAfterMoveAnnotation.builder()
                .text("German comment")
                .language(Nation.GERMANY)
                .build());

        // When: Converting to PGN with simplified converter
        AnnotationConverter converter = AnnotationConverter.getSimplifiedPgnConverter();
        converter.convertToPgn(annotations);

        // Then: Should concatenate with space
        assertEquals(1, annotations.size());
        assertTrue(annotations.get(0) instanceof CommentaryAfterMoveAnnotation);
        assertEquals("English comment German comment", ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary());
    }

    @Test
    public void testSimplifiedConverter_textBeforeMove() {
        // Given: TextBeforeMoveAnnotation
        Annotations annotations = new Annotations();
        annotations.add(ImmutableTextBeforeMoveAnnotation.of("Before move comment"));

        // When: Converting to PGN with simplified converter
        AnnotationConverter converter = AnnotationConverter.getSimplifiedPgnConverter();
        converter.convertToPgn(annotations);

        // Then: Should output plain text without [%pre] tags
        assertEquals(1, annotations.size());
        assertTrue(annotations.get(0) instanceof CommentaryBeforeMoveAnnotation);
        assertEquals("Before move comment", ((CommentaryBeforeMoveAnnotation) annotations.get(0)).getCommentary());
    }

    @Test
    public void testSimplifiedConverter_graphicalAnnotationsStillEncoded() {
        // Given: GraphicalSquaresAnnotation
        Annotations annotations = new Annotations();
        annotations.add(ImmutableGraphicalSquaresAnnotation.of(
                java.util.Arrays.asList(
                        ImmutableSquare.of(GraphicalAnnotationColor.GREEN, se.yarin.chess.Chess.strToSqi("e4"))
                )
        ));

        // When: Converting to PGN with simplified converter
        AnnotationConverter converter = AnnotationConverter.getSimplifiedPgnConverter();
        converter.convertToPgn(annotations);

        // Then: Should still encode as [%csl ...]
        assertEquals(1, annotations.size());
        assertTrue(annotations.get(0) instanceof CommentaryAfterMoveAnnotation);
        assertTrue(((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary().contains("[%csl"));
    }

    @Test
    public void testSimplifiedConverter_clockAnnotationsStillEncoded() {
        // Given: WhiteClockAnnotation
        Annotations annotations = new Annotations();
        annotations.add(ImmutableWhiteClockAnnotation.of(360000)); // 1:00:00

        // When: Converting to PGN with simplified converter
        AnnotationConverter converter = AnnotationConverter.getSimplifiedPgnConverter();
        converter.convertToPgn(annotations);

        // Then: Should still encode as [%clk ...]
        assertEquals(1, annotations.size());
        assertTrue(annotations.get(0) instanceof CommentaryAfterMoveAnnotation);
        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%clk"));
    }

    @Test
    public void testSimplifiedConverter_mixedAnnotations() {
        // Given: Mix of text, graphical, and clock annotations
        Annotations annotations = new Annotations();
        annotations.add(ImmutableGraphicalSquaresAnnotation.of(
                java.util.Arrays.asList(
                        ImmutableSquare.of(GraphicalAnnotationColor.GREEN, se.yarin.chess.Chess.strToSqi("e4"))
                )
        ));
        annotations.add(ImmutableTextAfterMoveAnnotation.of("Good move"));
        annotations.add(ImmutableWhiteClockAnnotation.of(360000));

        // When: Converting to PGN with simplified converter
        AnnotationConverter converter = AnnotationConverter.getSimplifiedPgnConverter();
        converter.convertToPgn(annotations);

        // Then: Should encode graphical/clock, plain text for comment
        assertEquals(1, annotations.size());
        assertTrue(annotations.get(0) instanceof CommentaryAfterMoveAnnotation);
        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%csl"));
        assertTrue(commentary.contains("[%clk"));
        assertTrue(commentary.contains("Good move"));
        assertFalse(commentary.contains("[%post:"));
    }

    @Test
    public void testRoundTripConverter_preservesLanguageTags() {
        // Given: TextAfterMoveAnnotation with German language
        Annotations annotations = new Annotations();
        annotations.add(ImmutableTextAfterMoveAnnotation.builder()
                .text("Ein Kommentar")
                .language(Nation.GERMANY)
                .build());

        // When: Converting to PGN with round-trip converter
        AnnotationConverter converter = AnnotationConverter.getRoundTripConverter();
        converter.convertToPgn(annotations);

        // Then: Should preserve language tag
        assertEquals(1, annotations.size());
        assertTrue(annotations.get(0) instanceof CommentaryAfterMoveAnnotation);
        String commentary = ((CommentaryAfterMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%post:GER"));
        assertTrue(commentary.contains("Ein Kommentar"));
    }

    @Test
    public void testRoundTripConverter_preservesPreTags() {
        // Given: TextBeforeMoveAnnotation
        Annotations annotations = new Annotations();
        annotations.add(ImmutableTextBeforeMoveAnnotation.of("Before move"));

        // When: Converting to PGN with round-trip converter
        AnnotationConverter converter = AnnotationConverter.getRoundTripConverter();
        converter.convertToPgn(annotations);

        // Then: Should preserve [%pre] tag
        assertEquals(1, annotations.size());
        assertTrue(annotations.get(0) instanceof CommentaryBeforeMoveAnnotation);
        String commentary = ((CommentaryBeforeMoveAnnotation) annotations.get(0)).getCommentary();
        assertTrue(commentary.contains("[%pre"));
        assertTrue(commentary.contains("Before move"));
    }

    @Test
    public void testSimplifiedConverter_convertToChessBase() {
        // Given: PGN comment with plain text
        Annotations annotations = new Annotations();
        annotations.add(new CommentaryAfterMoveAnnotation("This is a comment [%csl Ge4]"));

        // When: Converting to ChessBase with simplified converter
        AnnotationConverter converter = AnnotationConverter.getSimplifiedPgnConverter();
        converter.convertToChessBase(annotations);

        // Then: Should extract graphical annotation and text
        assertTrue(annotations.size() >= 2);
        boolean hasText = false;
        boolean hasGraphical = false;
        for (Annotation annotation : annotations) {
            if (annotation instanceof TextAfterMoveAnnotation) {
                hasText = true;
                assertEquals("This is a comment", ((TextAfterMoveAnnotation) annotation).text());
            } else if (annotation instanceof GraphicalSquaresAnnotation) {
                hasGraphical = true;
            }
        }
        assertTrue(hasText, "Should have text annotation");
        assertTrue(hasGraphical, "Should have graphical annotation");
    }
}
