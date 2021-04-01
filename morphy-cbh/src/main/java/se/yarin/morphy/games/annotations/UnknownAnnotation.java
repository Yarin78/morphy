package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.util.CBUtil;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

@Value.Immutable
public abstract class UnknownAnnotation extends Annotation implements RawAnnotation, StatisticalAnnotation {
    private static final Logger log = LoggerFactory.getLogger(UnknownAnnotation.class);

    @Value.Parameter
    public abstract int annotationType();

    @Value.Parameter
    public abstract byte[] rawData();

    @Override
    public String toString() {
        return String.format("UnknownAnnotation %02x: %s", annotationType(), CBUtil.toHexString(rawData()));
    }

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        if (annotationType() == 0x08) {
            stats.flags.add(GameHeaderFlags.ANNO_TYPE_8);
        }
        if (annotationType() == 0x1A) {
            stats.flags.add(GameHeaderFlags.ANNO_TYPE_1A);
        }
    }
}
