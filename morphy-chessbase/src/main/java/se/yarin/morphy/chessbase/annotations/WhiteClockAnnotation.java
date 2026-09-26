package se.yarin.morphy.chessbase.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.chessbase.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.util.regex.Pattern;

@Value.Immutable
public abstract class WhiteClockAnnotation extends Annotation implements StatisticalAnnotation {

  @Value.Parameter
  public abstract int clockTime();

  @Override
  public String toString() {
    int hours = clockTime() / 100 / 3600;
    int minutes = (clockTime() / 100 / 60) % 60;
    int seconds = (clockTime() / 100) % 60;
    return String.format("WhiteClock = %02d:%02d:%02d", hours, minutes, seconds);
  }

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    stats.flags.add(GameHeaderFlags.WHITE_CLOCK);
  }


  /**
   * PgnCodec for WhiteClockAnnotation.
   * Encodes as [%clk H:MM:SS] and decodes from [%clkw H:MM:SS].
   * The generic [%clk ...] pattern is handled separately with context-aware decoding.
   */
  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Pattern CLKW_PATTERN = Pattern.compile("\\[%clkw\\s+([^\\]]+)\\]");

    @Override
    @NotNull
    public Pattern getPattern() {
      return CLKW_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      WhiteClockAnnotation a = (WhiteClockAnnotation) annotation;
      return "[%clk " + AnnotationPgnUtil.formatCentisecondsAsTime(a.clockTime()) + "]";
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      int time = AnnotationPgnUtil.parseTimeToCentiseconds(data.trim());
      return ImmutableWhiteClockAnnotation.of(time);
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutableWhiteClockAnnotation.class;
    }
  }
}
