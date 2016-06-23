package se.yarin.chess.annotations;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import se.yarin.chess.LineEvaluation;
import se.yarin.chess.MoveComment;
import se.yarin.chess.MovePrefix;
import se.yarin.chess.Symbol;

/**
 * An annotation specifying a {@link se.yarin.chess.MoveComment}, a {@link se.yarin.chess.MovePrefix}
 * and/or a {@link se.yarin.chess.LineEvaluation}.
 */
@AllArgsConstructor
public class SymbolAnnotation extends Annotation {
    @Getter private final MoveComment moveComment;
    @Getter private final MovePrefix movePrefix;
    @Getter private final LineEvaluation lineEvaluation;

    public SymbolAnnotation(@NonNull Symbol... symbols) {
        MoveComment comment = MoveComment.NOTHING;
        MovePrefix prefix = MovePrefix.NOTHING;
        LineEvaluation eval = LineEvaluation.NO_EVALUATION;
        for (Symbol symbol : symbols) {
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
    public boolean isEmptyAnnotation() {
        return lineEvaluation == LineEvaluation.NO_EVALUATION &&
                movePrefix == MovePrefix.NOTHING &&
                moveComment == MoveComment.NOTHING;
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
}
