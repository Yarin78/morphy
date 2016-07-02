package se.yarin.chess;

import org.junit.Test;
import se.yarin.chess.annotations.*;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AnnotationTest {

    @Test
    public void testGetSpecificAnnotation() {
        Annotations annotations = new Annotations();

        annotations.add(new CommentaryAfterMoveAnnotation("after"));
        annotations.add(new CommentaryBeforeMoveAnnotation("before"));

        CommentaryAfterMoveAnnotation annotation = annotations.getAnnotation(CommentaryAfterMoveAnnotation.class);
        assertEquals("after", annotation.getCommentary());

        assertNull(annotations.getAnnotation(SymbolAnnotation.class));
    }

    @Test
    public void testAddEmptyAnnotations() {
        Annotations annotations = new Annotations();

        annotations.add(new CommentaryAfterMoveAnnotation(" "));
        annotations.add(new CommentaryBeforeMoveAnnotation(" \n\t  "));
        annotations.add(new SymbolAnnotation(
                LineEvaluation.NO_EVALUATION,
                MovePrefix.NOTHING,
                MoveComment.NOTHING));

        assertEquals(0, annotations.size());
    }

    @Test
    public void testRemoveAnnotation() {
        Annotations annotations = new Annotations();

        annotations.add(new CommentaryAfterMoveAnnotation("after"));
        annotations.add(new SymbolAnnotation(LineEvaluation.EQUAL));

        assertTrue(annotations.remove(SymbolAnnotation.class));
        assertFalse(annotations.remove(SymbolAnnotation.class));
        assertEquals(1, annotations.size());
    }

    @Test
    public void testClearAnnotations() {
        Annotations annotations = new Annotations();

        annotations.add(new CommentaryAfterMoveAnnotation("after"));
        annotations.add(new SymbolAnnotation(LineEvaluation.EQUAL));

        assertEquals(2, annotations.size());
        annotations.clear();
        assertEquals(0, annotations.size());
    }

    @Test
    public void testMergeCommentaryAnnotation() {
        Annotations annotations = new Annotations();

        annotations.add(new CommentaryBeforeMoveAnnotation("old"));
        annotations.add(new CommentaryBeforeMoveAnnotation("new"));

        assertEquals(1, annotations.size());

        CommentaryBeforeMoveAnnotation annotation = annotations.getAnnotation(CommentaryBeforeMoveAnnotation.class);
        assertEquals("new", annotation.getCommentary());
    }

    @Test
    public void testFormatAnnotations() {
        Annotations annotations = new Annotations();

        annotations.add(new CommentaryAfterMoveAnnotation("after move"));
        annotations.add(new CommentaryBeforeMoveAnnotation("before move"));
        annotations.add(new SymbolAnnotation(
                LineEvaluation.WHITE_DECISIVE_ADVANTAGE,
                MovePrefix.EDITORIAL_ANNOTATION,
                MoveComment.GOOD_MOVE));

        assertEquals(3, annotations.size());
        assertEquals("{ before move } RR 10.Bc4! +- { after move }", annotations.format("10.Bc4", true));
    }

    @Test
    public void testImplicitRemoveTextAnnotation() {
        Annotations annotations = new Annotations();

        annotations.add(new CommentaryAfterMoveAnnotation("after move"));
        assertEquals(1, annotations.size());
        annotations.add(new CommentaryAfterMoveAnnotation(""));
        assertEquals(0, annotations.size());
    }

    @Test
    public void testImplicitRemoveSymbolAnnotation() {
        Annotations annotations = new Annotations();

        annotations.add(new SymbolAnnotation(LineEvaluation.EQUAL));
        assertEquals(1, annotations.size());
        annotations.add(new SymbolAnnotation(LineEvaluation.NO_EVALUATION));
        assertEquals(0, annotations.size());
    }

    @Test
    public void testAddAllAnnotations() {
        Annotations anno1 = new Annotations();
        Annotations anno2 = new Annotations();

        anno1.add(new SymbolAnnotation(LineEvaluation.EQUAL));
        anno1.add(new CommentaryAfterMoveAnnotation("after"));

        anno2.add(new CommentaryBeforeMoveAnnotation("test"));
        anno2.add(new SymbolAnnotation(MovePrefix.DIRECTED_AGAINST));

        anno2.addAll(anno1);
        assertEquals(3, anno2.size());
    }

    private static class DummyAnnotation1 extends Annotation {}
    private static class DummyAnnotation2 extends Annotation {}

    @Test
    public void testAddAnnotationsWithSamePriority() {
        Annotations annotations = new Annotations();
        annotations.add(new DummyAnnotation1());
        annotations.add(new DummyAnnotation2());

        assertEquals(2, annotations.size());
    }
}
