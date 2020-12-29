package se.yarin.cbhlib.annotations;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.CBUtil;
import se.yarin.cbhlib.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class UnknownAnnotation extends Annotation implements RawAnnotation, StatisticalAnnotation {
    private static final Logger log = LoggerFactory.getLogger(UnknownAnnotation.class);

    @Getter
    private int annotationType;

    @Getter
    private byte[] rawData;

    @Override
    public String toString() {
        return String.format("UnknownAnnotation %02x: %s", annotationType, CBUtil.toHexString(rawData));
    }

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        if (annotationType == 0x08) {
            stats.flags.add(GameHeaderFlags.ANNO_TYPE_8);
        }
        if (annotationType == 0x1A) {
            stats.flags.add(GameHeaderFlags.ANNO_TYPE_1A);
        }
    }
}
