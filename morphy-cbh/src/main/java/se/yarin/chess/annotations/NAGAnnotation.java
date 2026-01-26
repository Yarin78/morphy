package se.yarin.chess.annotations;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.NAG;

/** An annotation specifying a {@link se.yarin.chess.NAG}. */
public class NAGAnnotation extends Annotation {

  private final NAG nag;

  public NAG getNag() {
    return nag;
  }

  public NAGAnnotation(@NotNull NAG nag) {
    this.nag = nag;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof NAGAnnotation otherNag && this.getNag().equals(otherNag.getNag());
  }

  @Override
  public int hashCode() {
    return nag.hashCode();
  }
}
