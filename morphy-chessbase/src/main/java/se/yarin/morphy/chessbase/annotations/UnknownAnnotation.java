package se.yarin.morphy.chessbase.annotations;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.chessbase.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

@Value.Immutable
public abstract class UnknownAnnotation extends Annotation
    implements RawAnnotation, StatisticalAnnotation {
  private static final Logger log = LoggerFactory.getLogger(UnknownAnnotation.class);

  @Value.Parameter
  public abstract int annotationType();

  @Value.Parameter
  public abstract byte[] rawData();

  @Override
  public String toString() {
    return String.format(
        "UnknownAnnotation %02x: %s", annotationType(), AnnotationPgnUtil.toHexString(rawData()));
  }

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    if (annotationType() == 0x08) {
      stats.flags.add(GameHeaderFlags.ANNO_TYPE_8);
    }
    if (annotationType() == 0x1A) {
      stats.flags.add(GameHeaderFlags.ANNO_TYPE_1A);
    }
    // Sound and picture annotations of the v1 format, which are hardly used any more and kept as
    // they are. A video annotation (0x20) sets no flag: in Mega Database 2021, 22 of them are on
    // 17 games, none of which has the embedded video flag; only guiding texts have it.
    if (annotationType() == 0x10) {
      stats.flags.add(GameHeaderFlags.EMBEDDED_AUDIO);
    }
    if (annotationType() == 0x11) {
      stats.flags.add(GameHeaderFlags.EMBEDDED_PICTURE);
    }
  }
}
