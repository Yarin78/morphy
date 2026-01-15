package se.yarin.chess.annotations;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.NAG;

/**
 * An annotation specifying a {@link se.yarin.chess.NAG}.
 */
public class NAGAnnotation extends Annotation {

    private final NAG nag;

    public NAG getNag() {
        return nag;
    }

    public NAGAnnotation(@NotNull NAG nag) {
        this.nag = nag;
    }

    @Override
    public int priority() {
        switch (nag.getType()) {
            case MOVE_COMMENT:
                return 100;
            case LINE_EVALUATION:
                return 98;
            case MOVE_PREFIX:
                return 99;
        }
        return 0;
    }

    @Override
    public String format(@NotNull String text, boolean ascii) {
        switch (nag.getType()) {
            case MOVE_COMMENT:
                String symbol = ascii ? nag.toASCIIString() : nag.toUnicodeString();
                if (symbol.length() <= 2) {
                    text += symbol;
                } else {
                    text += " " + symbol;
                }
                return text;
            case LINE_EVALUATION:
                String eval = ascii ? nag.toASCIIString() : nag.toUnicodeString();
                if (eval.length() > 0) {
                    text += " " + eval;
                }
                return text;
            case MOVE_PREFIX:
                String pre = ascii ? nag.toASCIIString() : nag.toUnicodeString();
                if (pre.length() > 0) {
                    text = pre + " " + text;
                }
                return text;
        }

        return text;
    }
}
