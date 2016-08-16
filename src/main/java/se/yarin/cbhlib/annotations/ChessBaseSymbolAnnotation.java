package se.yarin.cbhlib.annotations;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import se.yarin.cbhlib.AnnotationSerializer;
import se.yarin.cbhlib.ByteBufferUtil;
import se.yarin.cbhlib.CBUtil;
import se.yarin.chess.LineEvaluation;
import se.yarin.chess.MoveComment;
import se.yarin.chess.MovePrefix;
import se.yarin.chess.Symbol;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

// TODO: Should be NAGs annotation
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ChessBaseSymbolAnnotation extends Annotation {
    @Getter private final MoveComment moveComment;
    @Getter private final MovePrefix movePrefix;
    @Getter private final LineEvaluation lineEvaluation;

    public ChessBaseSymbolAnnotation(@NonNull Symbol... symbols) {
        MoveComment comment = MoveComment.NOTHING;
        MovePrefix prefix = MovePrefix.NOTHING;
        LineEvaluation eval = LineEvaluation.NO_EVALUATION;
        for (Symbol symbol : symbols) {
            if (symbol == null) {
                continue;
            }
            if (symbol instanceof MoveComment) {
                comment = (MoveComment) symbol;
            }
            if (symbol instanceof MovePrefix) {
                prefix = (MovePrefix) symbol;
            }
            if (symbol instanceof LineEvaluation) {
                eval = (LineEvaluation) symbol;
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

    public static class Serializer implements AnnotationSerializer {
        @Override
        public Annotation deserialize(ByteBuffer buf, int length) {
            Symbol[] symbols = new Symbol[length];
            for (int i = 0; i < length; i++) {
                symbols[i] = CBUtil.decodeSymbol(buf.get());
            }
            return new ChessBaseSymbolAnnotation(symbols);
        }

        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            ChessBaseSymbolAnnotation symbolAnnotation = (ChessBaseSymbolAnnotation) annotation;
            int b1 = CBUtil.encodeSymbol(symbolAnnotation.getMoveComment());
            int b2 = CBUtil.encodeSymbol(symbolAnnotation.getLineEvaluation());
            int b3 = CBUtil.encodeSymbol(symbolAnnotation.getMovePrefix());
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
            return ChessBaseSymbolAnnotation.class;
        }
    }


}
