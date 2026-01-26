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
  public boolean equals(Object obj) {
    return obj instanceof CommentaryAfterMoveAnnotation other &&
           this.commentary.equals(other.commentary);
  }

  @Override
  public int hashCode() {
    return commentary.hashCode();
  }
}
