package se.yarin.chess.annotations;

import org.jetbrains.annotations.NotNull;

public class CommentaryBeforeMoveAnnotation extends Annotation {
  private final String commentary;

  public CommentaryBeforeMoveAnnotation(@NotNull String commentary) {
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
  public String format(@NotNull String text, boolean ascii) {
    String s = this.commentary;
    if (ascii) {
      s = s.replaceAll("[^\\x20-\\x7E]", "?");
    }
    return "{ " + s + " } " + text;
  }
}
