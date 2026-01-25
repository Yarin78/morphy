package se.yarin.morphy.cli.games;

import se.yarin.chess.Player;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.AnnotationTransformer;
import se.yarin.chess.annotations.Annotations;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.games.annotations.*;

import java.util.Set;

public class PgnAnnotationFilter implements AnnotationTransformer {

  private final boolean standardOnly;
  private final Set<Nation> languageFilter;

  private static final Set<Class<? extends Annotation>> STANDARD_ANNOTATIONS =
      Set.of(
          ImmutableSymbolAnnotation.class,
          ImmutableTextBeforeMoveAnnotation.class,
          ImmutableTextAfterMoveAnnotation.class,
          ImmutableGraphicalSquaresAnnotation.class,
          ImmutableGraphicalArrowsAnnotation.class,
          ImmutableWhiteClockAnnotation.class,
          ImmutableBlackClockAnnotation.class,
          ImmutableTimeSpentAnnotation.class,
          ImmutableComputerEvaluationAnnotation.class);

  public PgnAnnotationFilter(boolean standardOnly, Set<Nation> languageFilter) {
    this.standardOnly = standardOnly;
    this.languageFilter = languageFilter;
  }

  @Override
  public void transform(Annotations annotations, Player lastMoveBy) {
    annotations.removeIf(this::shouldFilter);
  }

  private boolean shouldFilter(Annotation annotation) {
    if (standardOnly && !STANDARD_ANNOTATIONS.contains(annotation.getClass())) {
      return true;
    }

    if (languageFilter != null && !languageFilter.isEmpty()) {
      if (annotation instanceof TextAfterMoveAnnotation textAnno) {
        return !languageFilter.contains(textAnno.language());
      }
      if (annotation instanceof TextBeforeMoveAnnotation textAnno) {
        return !languageFilter.contains(textAnno.language());
      }
    }

    return false;
  }
}
