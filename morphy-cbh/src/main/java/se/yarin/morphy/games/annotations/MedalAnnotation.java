package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.games.Medal;
import se.yarin.util.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Value.Immutable
public abstract class MedalAnnotation extends Annotation implements StatisticalAnnotation {

  @Value.Parameter
  public abstract EnumSet<Medal> medals();

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Medal medal : medals()) {
      if (sb.length() > 0) sb.append(", ");
      sb.append(medal);
    }

    return "Medals = " + sb.toString();
  }

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    stats.medals.addAll(medals());
  }

  public static class Serializer implements AnnotationSerializer {
    @Override
    public void serialize(ByteBuffer buf, Annotation annotation) {
      ByteBufferUtil.putIntB(buf, Medal.encode(((MedalAnnotation) annotation).medals()));
    }

    @Override
    public MedalAnnotation deserialize(ByteBuffer buf, int length) {
      return ImmutableMedalAnnotation.of(Medal.decode(ByteBufferUtil.getIntB(buf)));
    }

    @Override
    public Class getAnnotationClass() {
      return ImmutableMedalAnnotation.class;
    }

    @Override
    public int getAnnotationType() {
      return 0x22;
    }
  }

  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Pattern MEDAL_PATTERN = Pattern.compile("\\[%medal\\s+([^\\]]+)\\]");

    private static final Map<String, Medal> MEDAL_FROM_STRING = new HashMap<>();
    private static final Map<Medal, String> MEDAL_TO_STRING = new HashMap<>();
    static {
      MEDAL_FROM_STRING.put("best", Medal.BEST_GAME);
      MEDAL_FROM_STRING.put("decided", Medal.DECIDED_TOURNAMENT);
      MEDAL_FROM_STRING.put("model", Medal.MODEL_GAME);
      MEDAL_FROM_STRING.put("novelty", Medal.NOVELTY);
      MEDAL_FROM_STRING.put("pawn", Medal.PAWN_STRUCTURE);
      MEDAL_FROM_STRING.put("strategy", Medal.STRATEGY);
      MEDAL_FROM_STRING.put("tactics", Medal.TACTICS);
      MEDAL_FROM_STRING.put("attack", Medal.WITH_ATTACK);
      MEDAL_FROM_STRING.put("sacrifice", Medal.SACRIFICE);
      MEDAL_FROM_STRING.put("defense", Medal.DEFENSE);
      MEDAL_FROM_STRING.put("material", Medal.MATERIAL);
      MEDAL_FROM_STRING.put("piece", Medal.PIECE_PLAY);
      MEDAL_FROM_STRING.put("endgame", Medal.ENDGAME);
      MEDAL_FROM_STRING.put("tactblunder", Medal.TACTICAL_BLUNDER);
      MEDAL_FROM_STRING.put("stratblunder", Medal.STRATEGICAL_BLUNDER);
      MEDAL_FROM_STRING.put("user", Medal.USER);

      for (Map.Entry<String, Medal> entry : MEDAL_FROM_STRING.entrySet()) {
        MEDAL_TO_STRING.put(entry.getValue(), entry.getKey());
      }
    }

    @Override
    @NotNull
    public Pattern getPattern() {
      return MEDAL_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      MedalAnnotation a = (MedalAnnotation) annotation;
      if (a.medals().isEmpty()) return null;
      String medals = a.medals().stream()
              .map(MEDAL_TO_STRING::get)
              .filter(Objects::nonNull)
              .collect(Collectors.joining(","));
      if (medals.isEmpty()) return null;
      return "[%medal " + medals + "]";
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      EnumSet<Medal> medals = EnumSet.noneOf(Medal.class);
      for (String medalStr : data.split(",")) {
        Medal medal = MEDAL_FROM_STRING.get(medalStr.trim().toLowerCase());
        if (medal != null) {
          medals.add(medal);
        }
      }
      if (medals.isEmpty()) return null;
      return ImmutableMedalAnnotation.of(medals);
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutableMedalAnnotation.class;
    }
  }
}
