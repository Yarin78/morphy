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

    CommentaryAfterMoveAnnotation annotation =
        annotations.getByClass(CommentaryAfterMoveAnnotation.class);
    assertEquals("after", annotation.getCommentary());

    assertNull(annotations.getByClass(NAGAnnotation.class));
  }

  @Test
  public void testRemoveByClassAnnotation() {
    Annotations annotations = new Annotations();

    annotations.add(new CommentaryAfterMoveAnnotation("after"));
    annotations.add(new NAGAnnotation(NAG.EQUAL));

    assertTrue(annotations.removeByClass(NAGAnnotation.class));
    assertFalse(annotations.removeByClass(NAGAnnotation.class));
    assertEquals(1, annotations.size());
  }

  @Test
  public void testClearAnnotations() {
    Annotations annotations = new Annotations();

    annotations.add(new CommentaryAfterMoveAnnotation("after"));
    annotations.add(new NAGAnnotation(NAG.EQUAL));

    assertEquals(2, annotations.size());
    annotations.clear();
    assertEquals(0, annotations.size());
  }

  @Test
  public void testReplaceCommentaryAnnotation() {
    Annotations annotations = new Annotations();

    annotations.add(new CommentaryBeforeMoveAnnotation("old"));
    annotations.replace(new CommentaryBeforeMoveAnnotation("new"));

    assertEquals(1, annotations.size());

    CommentaryBeforeMoveAnnotation annotation =
        annotations.getByClass(CommentaryBeforeMoveAnnotation.class);
    assertEquals("new", annotation.getCommentary());
  }

  @Test
  public void testFormatAnnotations() {
    Annotations annotations = new Annotations();

    annotations.add(new CommentaryAfterMoveAnnotation("after move"));
    annotations.add(new CommentaryBeforeMoveAnnotation("before move"));
    annotations.add(new NAGAnnotation(NAG.WHITE_DECISIVE_ADVANTAGE));
    annotations.add(new NAGAnnotation(NAG.EDITORIAL_COMMENT));
    annotations.add(new NAGAnnotation(NAG.GOOD_MOVE));

    assertEquals(5, annotations.size());
    assertEquals(
        "{ before move } RR 10.Bc4! +- { after move }", annotations.format("10.Bc4", true));
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
