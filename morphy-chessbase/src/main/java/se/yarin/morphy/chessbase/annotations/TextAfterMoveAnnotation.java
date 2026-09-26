package se.yarin.morphy.chessbase.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.chessbase.Nation;
import se.yarin.morphy.chessbase.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;


@Value.Immutable
public abstract class TextAfterMoveAnnotation extends Annotation implements StatisticalAnnotation {

  @Value.Parameter
  @NotNull
  public abstract String text();

  @Value.Default
  @NotNull
  public Nation language() {
    return Nation.NONE;
  }

  @Value.Default
  public int unknown() {
    return 0;
  }
  ; // This value is sometimes 64 in Megabase 2016

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    stats.commentariesLength += text().length();
    stats.flags.add(GameHeaderFlags.COMMENTARY);
  }

}
