package se.yarin.cbhlib.annotations;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.chess.annotations.Annotation;

@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class InvalidAnnotation extends Annotation implements RawAnnotation {
    @Getter
    private int annotationType;

    @Getter
    private byte[] rawData;

    @Override
    public String toString() {
        return String.format("InvalidAnnotation %02x: %s", annotationType, CBUtil.toHexString(rawData));
    }
}
