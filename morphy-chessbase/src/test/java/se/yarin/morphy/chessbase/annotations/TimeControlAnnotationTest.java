package se.yarin.morphy.chessbase.annotations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.TimeControlAnnotation.TimeSerie;

/**
 * Reading a time control from a PGN comment. The types of the series are those ChessBase gives the
 * same time controls when it reads them from the {@code TimeControl} tag of a PGN file.
 */
class TimeControlAnnotationTest {
  private static final TimeControlAnnotation.PgnCodec CODEC = new TimeControlAnnotation.PgnCodec();

  /** A serie, in seconds. */
  private static TimeSerie serie(int start, int increment, int moves, int type) {
    return ImmutableTimeSerie.of(start * 100, increment * 100, moves, type);
  }

  private static List<TimeSerie> read(String text) {
    Annotation annotation = CODEC.decode(text);
    return ((TimeControlAnnotation) annotation).timeSeries();
  }

  @Test
  void theRestOfTheGameWithNoIncrementIsType0() {
    assertEquals(List.of(serie(300, 0, 1000, 0)), read("5m"));
    assertEquals(List.of(serie(30, 0, 1000, 0)), read("30s"));
  }

  @Test
  void theRestOfTheGameWithAnIncrementIsType3() {
    assertEquals(List.of(serie(180, 2, 1000, 3)), read("(3m+2s)"));
    assertEquals(List.of(serie(60, 1, 1000, 3)), read("(1m+1s)"));
  }

  @Test
  void aStageOfAGivenNumberOfMovesIsType1() {
    assertEquals(List.of(serie(7200, 0, 40, 1)), read("120m/40"));
    assertEquals(
        List.of(serie(5400, 0, 40, 1), serie(1800, 0, 1000, 0)), read("90m/40+30m"));
  }

  @Test
  void aStageWithAnIncrementIsStillType1ButTheRestOfTheGameWithOneIsType3() {
    assertEquals(
        List.of(serie(5400, 30, 40, 1), serie(1800, 30, 1000, 3)),
        read("(90m+30s)/40+(30m+30s)"));
    assertEquals(
        List.of(serie(5400, 30, 40, 1), serie(1800, 30, 20, 1), serie(900, 30, 1000, 3)),
        read("(90m+30s)/40+(30m+30s)/20+(15m+30s)"));
  }

  @Test
  void whatIsWrittenIsReadBackTheSame() {
    for (List<TimeSerie> series :
        List.of(
            List.of(serie(300, 0, 1000, 0)),
            List.of(serie(180, 2, 1000, 3)),
            List.of(serie(5400, 0, 40, 1), serie(1800, 0, 1000, 0)),
            List.of(serie(5400, 30, 40, 1), serie(1800, 30, 1000, 3)))) {
      String written = CODEC.encode(ImmutableTimeControlAnnotation.of(series));
      String text = written.substring("[%tc ".length(), written.length() - 1);
      assertEquals(series, read(text), written);
    }
  }
}
