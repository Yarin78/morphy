package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Value.Immutable
public abstract class CriticalPositionAnnotation extends Annotation
    implements StatisticalAnnotation {

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    stats.flags.add(GameHeaderFlags.CRITICAL_POSITION);
  }

  public enum CriticalPositionType {
    NONE,
    OPENING,
    MIDDLEGAME,
    ENDGAME
  }

  @Value.Parameter
  public abstract CriticalPositionType type();

  public static class Serializer implements AnnotationSerializer {
    @Override
    public void serialize(ByteBuffer buf, Annotation annotation) {
      buf.put((byte) ((CriticalPositionAnnotation) annotation).type().ordinal());
    }

    @Override
    public CriticalPositionAnnotation deserialize(ByteBuffer buf, int length) {
      return ImmutableCriticalPositionAnnotation.of(CriticalPositionType.values()[buf.get()]);
    }

    @Override
    public Class getAnnotationClass() {
      return ImmutableCriticalPositionAnnotation.class;
    }

    @Override
    public int getAnnotationType() {
      return 0x18;
    }
  }

  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Logger log = LoggerFactory.getLogger(PgnCodec.class);
    private static final Pattern CRIT_PATTERN = Pattern.compile("\\[%crit\\s+([^\\]]+)\\]");

    private static final Map<String, CriticalPositionType> CRIT_FROM_STRING = new HashMap<>();
    private static final Map<CriticalPositionType, String> CRIT_TO_STRING = new HashMap<>();
    static {
      CRIT_FROM_STRING.put("opening", CriticalPositionType.OPENING);
      CRIT_FROM_STRING.put("middlegame", CriticalPositionType.MIDDLEGAME);
      CRIT_FROM_STRING.put("endgame", CriticalPositionType.ENDGAME);

      CRIT_TO_STRING.put(CriticalPositionType.OPENING, "opening");
      CRIT_TO_STRING.put(CriticalPositionType.MIDDLEGAME, "middlegame");
      CRIT_TO_STRING.put(CriticalPositionType.ENDGAME, "endgame");
    }

    @Override
    @NotNull
    public Pattern getPattern() {
      return CRIT_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      CriticalPositionAnnotation a = (CriticalPositionAnnotation) annotation;
      String typeStr = CRIT_TO_STRING.get(a.type());
      if (typeStr == null) return null;
      return "[%crit " + typeStr + "]";
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      CriticalPositionType type = CRIT_FROM_STRING.get(data.trim().toLowerCase());
      if (type == null) {
        log.warn("Invalid critical position type: {}", data);
        return null;
      }
      return ImmutableCriticalPositionAnnotation.of(type);
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutableCriticalPositionAnnotation.class;
    }
  }
}
