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
  public boolean equals(Object obj) {
    return obj instanceof CommentaryBeforeMoveAnnotation other &&
           this.commentary.equals(other.commentary);
  }

  @Override
  public int hashCode() {
    return commentary.hashCode();
  }
}
