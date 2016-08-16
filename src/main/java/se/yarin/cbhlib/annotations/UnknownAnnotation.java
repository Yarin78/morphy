package se.yarin.cbhlib.annotations;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.CBUtil;
import se.yarin.chess.annotations.Annotation;

@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class UnknownAnnotation extends Annotation implements RawAnnotation {
    private static final Logger log = LoggerFactory.getLogger(UnknownAnnotation.class);

    @Getter
    private int annotationType;

    @Getter
    private byte[] rawData;

    @Override
    public String toString() {
        return String.format("UnknownAnnotation %02x: %s", annotationType, CBUtil.toHexString(rawData));
    }
}
