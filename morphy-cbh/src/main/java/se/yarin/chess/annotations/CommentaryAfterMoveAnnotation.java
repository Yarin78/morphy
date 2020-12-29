package se.yarin.chess.annotations;

import lombok.NonNull;

public class CommentaryAfterMoveAnnotation extends Annotation {
    private final String commentary;

    public CommentaryAfterMoveAnnotation(@NonNull String commentary) {
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
    public String format(@NonNull String text, boolean ascii) {
        String s = this.commentary;
        if (ascii) {
            s = s.replaceAll("[^\\x20-\\x7E]", "?");
        }
        return text + " { " + s + " }";
    }
}
