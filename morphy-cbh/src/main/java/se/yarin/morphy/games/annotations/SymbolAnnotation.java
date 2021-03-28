package se.yarin.morphy.games.annotations;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import se.yarin.util.ByteBufferUtil;
import se.yarin.cbhlib.games.GameHeaderFlags;
import se.yarin.chess.NAG;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class SymbolAnnotation extends Annotation implements StatisticalAnnotation {
    @Getter private final NAG moveComment;
    @Getter private final NAG movePrefix;
    @Getter private final NAG lineEvaluation;

    public SymbolAnnotation(@NonNull NAG... nags) {
        NAG comment = NAG.NONE;
        NAG prefix = NAG.NONE;
        NAG eval = NAG.NONE;
        for (NAG nag : nags) {
            if (nag == null) {
                continue;
            }
            switch (nag.getType()) {
                case MOVE_COMMENT:
                    comment = nag;
                    break;
                case LINE_EVALUATION:
                    eval = nag;
                    break;
                case MOVE_PREFIX:
                    prefix = nag;
                    break;
            }
        }
        moveComment = comment;
        movePrefix = prefix;
        lineEvaluation = eval;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public String format(@NonNull String text, boolean ascii) {
        // First add move comment
        String symbol = ascii ? moveComment.toASCIIString() : moveComment.toUnicodeString();
        if (symbol.length() <= 2) {
            text += symbol;
        } else {
            text += " " + symbol;
        }

        // Then move prefix
        String pre = ascii ? movePrefix.toASCIIString() : movePrefix.toUnicodeString();
        if (pre.length() > 0) {
            text = pre + " " + text;
        }

        // Then line evaluation
        String eval = ascii ? lineEvaluation.toASCIIString() : lineEvaluation.toUnicodeString();
        if (eval.length() > 0) {
            text += " " + eval;
        }

        return text;
    }

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.noSymbols++;
        stats.flags.add(GameHeaderFlags.SYMBOLS);
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public Annotation deserialize(ByteBuffer buf, int length) {
            NAG[] symbols = new NAG[length];
            for (int i = 0; i < length; i++) {
                symbols[i] = NAG.values()[ByteBufferUtil.getUnsignedByte(buf)];
            }
            return new SymbolAnnotation(symbols);
        }

        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            SymbolAnnotation symbolAnnotation = (SymbolAnnotation) annotation;
            int b1 = symbolAnnotation.getMoveComment().ordinal();
            int b2 = symbolAnnotation.getLineEvaluation().ordinal();
            int b3 = symbolAnnotation.getMovePrefix().ordinal();
            ByteBufferUtil.putByte(buf, b1);
            if (b2 != 0 || b3 !=0) ByteBufferUtil.putByte(buf, b2);
            if (b3 != 0) ByteBufferUtil.putByte(buf, b3);
        }

        @Override
        public int getAnnotationType() {
            return 0x03;
        }

        @Override
        public Class getAnnotationClass() {
            return SymbolAnnotation.class;
        }
    }
}
