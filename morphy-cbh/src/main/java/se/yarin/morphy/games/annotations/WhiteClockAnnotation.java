package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.util.ByteBufferUtil;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
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

  public static class Serializer implements AnnotationSerializer {
    @Override
    public void serialize(ByteBuffer buf, Annotation annotation) {
      ByteBufferUtil.putIntB(buf, ((WhiteClockAnnotation) annotation).clockTime());
    }

    @Override
    public WhiteClockAnnotation deserialize(ByteBuffer buf, int length) {
      return ImmutableWhiteClockAnnotation.of(ByteBufferUtil.getIntB(buf));
    }

    @Override
    public Class getAnnotationClass() {
      return ImmutableWhiteClockAnnotation.class;
    }

    @Override
    public int getAnnotationType() {
      return 0x16;
    }
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
