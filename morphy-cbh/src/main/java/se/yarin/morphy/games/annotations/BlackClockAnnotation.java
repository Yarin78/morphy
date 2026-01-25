package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.util.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.regex.Pattern;

@Value.Immutable
public abstract class BlackClockAnnotation extends Annotation implements StatisticalAnnotation {
  @Value.Parameter
  abstract int clockTime();

  @Override
  public String toString() {
    int hours = clockTime() / 100 / 3600;
    int minutes = (clockTime() / 100 / 60) % 60;
    int seconds = (clockTime() / 100) % 60;
    return String.format("BlackClock = %02d:%02d:%02d", hours, minutes, seconds);
  }

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    stats.flags.add(GameHeaderFlags.BLACK_CLOCK);
  }

  public static class Serializer implements AnnotationSerializer {
    @Override
    public void serialize(ByteBuffer buf, Annotation annotation) {
      ByteBufferUtil.putIntB(buf, ((BlackClockAnnotation) annotation).clockTime());
    }

    @Override
    public BlackClockAnnotation deserialize(ByteBuffer buf, int length) {
      return ImmutableBlackClockAnnotation.of(ByteBufferUtil.getIntB(buf));
    }

    @Override
    public Class getAnnotationClass() {
      return ImmutableBlackClockAnnotation.class;
    }

    @Override
    public int getAnnotationType() {
      return 0x17;
    }
  }

  /**
   * PgnCodec for BlackClockAnnotation.
   * Encodes as [%clk H:MM:SS] and decodes from [%clkb H:MM:SS].
   * The generic [%clk ...] pattern is handled separately with context-aware decoding.
   */
  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Pattern CLKB_PATTERN = Pattern.compile("\\[%clkb\\s+([^\\]]+)\\]");

    @Override
    @NotNull
    public Pattern getPattern() {
      return CLKB_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      BlackClockAnnotation a = (BlackClockAnnotation) annotation;
      return "[%clk " + AnnotationPgnUtil.formatCentisecondsAsTime(a.clockTime()) + "]";
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      int time = AnnotationPgnUtil.parseTimeToCentiseconds(data.trim());
      return ImmutableBlackClockAnnotation.of(time);
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutableBlackClockAnnotation.class;
    }
  }
}
