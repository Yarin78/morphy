package se.yarin.chess.annotations;

import lombok.NonNull;

public class CommentaryBeforeMoveAnnotation extends Annotation {
    private final String commentary;

    public CommentaryBeforeMoveAnnotation(@NonNull String commentary) {
        this.commentary = commentary.trim();
    }

    public String getCommentary() {
        return commentary;
    }

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public String format(@NonNull String text, boolean ascii) {
        String s = this.commentary;
        if (ascii) {
            s = s.replaceAll("[^\\x20-\\x7E]", "?");
        }
        return "{ " + s + " } " + text;
    }
}
