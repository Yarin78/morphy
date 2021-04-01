package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import se.yarin.morphy.util.CBUtil;
import se.yarin.chess.annotations.Annotation;

@Value.Immutable
public abstract class InvalidAnnotation extends Annotation implements RawAnnotation {
    @Value.Parameter
    public abstract int annotationType();

    @Value.Parameter
    public abstract byte[] rawData();

    @Override
    public String toString() {
        return String.format("InvalidAnnotation %02x: %s", annotationType(), CBUtil.toHexString(rawData()));
    }
}
