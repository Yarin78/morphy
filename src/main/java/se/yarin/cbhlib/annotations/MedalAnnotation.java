package se.yarin.cbhlib.annotations;

import se.yarin.cbhlib.ByteBufferUtil;
import se.yarin.cbhlib.CBUtil;
import se.yarin.cbhlib.Medal;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.EnumSet;

public class MedalAnnotation extends Annotation {
    private EnumSet<Medal> medals;

    public EnumSet<Medal> getMedals() {
        return medals.clone();
    }

    public MedalAnnotation(EnumSet<Medal> medals) {
        this.medals = medals;
    }

    public MedalAnnotation(Medal first, Medal... rest) {
        this(EnumSet.of(first, rest));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Medal medal : medals) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(medal);
        }

        return "Medals = " + sb.toString();
    }

    public static MedalAnnotation deserialize(ByteBuffer buf) {
        return new MedalAnnotation(CBUtil.decodeMedals(ByteBufferUtil.getIntB(buf)));
    }
}
