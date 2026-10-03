package se.yarin.morphy.cb2.annotations;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.EvaluationsAnnotation;
import se.yarin.morphy.chessbase.annotations.EvaluationsAnnotation.Evaluation;
import se.yarin.morphy.chessbase.annotations.ImmutableEvaluationsAnnotation;
import se.yarin.morphy.chessbase.annotations.UnknownAnnotation;

class EvaluationsCodecTest {

  private static byte[] write(Annotation annotation) {
    ByteBuffer buf = AnnotationCodec.bufferFor(AnnotationCodec.maxSize(annotation));
    assertTrue(AnnotationCodec.write(buf, annotation));
    return Arrays.copyOf(buf.array(), buf.position());
  }

  private static Annotation read(byte[] bytes) throws Exception {
    return AnnotationCodec.read(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN));
  }

  @Test
  void evaluationsAreWrittenInTheirLayoutAndReadBack() throws Exception {
    EvaluationsAnnotation annotation =
        ImmutableEvaluationsAnnotation.of(
            List.of(
                new Evaluation(17, 1, 0), new Evaluation(-16, 20, 0), new Evaluation(0, 0, 0xff)));

    byte[] bytes = write(annotation);

    // The type, 01, the length, the count, then each evaluation: eval, depth, type
    assertArrayEquals(
        new byte[] {0x26, 0, 1, 14, 0, 0, 0, 3, 0, 17, 0, 1, 0, -16, -1, 20, 0, 0, 0, 0, -1},
        bytes);
    assertEquals(annotation, read(bytes));
  }

  @Test
  void evaluationsInAnUnknownLayoutAreKeptAsTheyAre() throws Exception {
    // A length that doesn't fit the count
    byte[] bytes = {0x26, 0, 1, 3, 0, 0, 0, 1, 0, 9};
    UnknownAnnotation annotation = assertInstanceOf(UnknownAnnotation.class, read(bytes));
    assertArrayEquals(Arrays.copyOfRange(bytes, 2, bytes.length), annotation.rawData());
  }
}
