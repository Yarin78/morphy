package se.yarin.chess.annotations;

import org.jetbrains.annotations.NotNull;

public class CommentaryAfterMoveAnnotation extends Annotation {
    private final String commentary;

    public CommentaryAfterMoveAnnotation(@NotNull String commentary) {
        this.commentary = commentary.trim();
    }

    public String getCommentary() {
        return commentary;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public String format(@NotNull String text, boolean ascii) {
        String s = this.commentary;
        if (ascii) {
            s = s.replaceAll("[^\\x20-\\x7E]", "?");
        }
        return text + " { " + s + " }";
    }
}
