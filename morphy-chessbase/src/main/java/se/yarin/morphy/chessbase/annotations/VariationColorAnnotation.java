package se.yarin.morphy.chessbase.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.annotations.Annotation;

import java.util.regex.Pattern;

@Value.Immutable
public abstract class VariationColorAnnotation extends Annotation {
  @Value.Parameter
  public abstract int red();

  @Value.Parameter
  public abstract int green();

  @Value.Parameter
  public abstract int blue();

  @Value.Parameter
  public abstract boolean onlyMoves();

  @Value.Parameter
  public abstract boolean onlyMainline();

  @Override
  public String toString() {
    return String.format(
        "Variation color = #%02X%02X%02X (%s, %s)",
        red(),
        green(),
        blue(),
        onlyMoves() ? "Moves only" : "Moves and annotations",
        onlyMainline() ? "Mainline only" : "Include sublines");
  }


  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Logger log = LoggerFactory.getLogger(PgnCodec.class);
    private static final Pattern VARCOLOR_PATTERN = Pattern.compile("\\[%varcolor\\s+([^\\]]+)\\]");

    @Override
    @NotNull
    public Pattern getPattern() {
      return VARCOLOR_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      VariationColorAnnotation a = (VariationColorAnnotation) annotation;
      StringBuilder sb = new StringBuilder("[%varcolor ");
      sb.append(String.format("#%02X%02X%02X", a.red(), a.green(), a.blue()));
      if (a.onlyMoves() || a.onlyMainline()) {
        sb.append(" ");
        if (a.onlyMoves()) sb.append("M");
        if (a.onlyMainline()) sb.append("L");
      }
      sb.append("]");
      return sb.toString();
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      try {
        String[] parts = data.split("\\s+");
        String colorPart = parts[0];

        // Parse #RRGGBB color
        if (!colorPart.startsWith("#") || colorPart.length() != 7) {
          log.warn("Invalid color format in varcolor annotation: {}", colorPart);
          return null;
        }
        int red = Integer.parseInt(colorPart.substring(1, 3), 16);
        int green = Integer.parseInt(colorPart.substring(3, 5), 16);
        int blue = Integer.parseInt(colorPart.substring(5, 7), 16);

        boolean onlyMoves = false;
        boolean onlyMainline = false;

        if (parts.length > 1) {
          String flags = parts[1];
          onlyMoves = flags.contains("M");
          onlyMainline = flags.contains("L");
        }

        return ImmutableVariationColorAnnotation.of(red, green, blue, onlyMoves, onlyMainline);
      } catch (Exception e) {
        log.warn("Failed to parse varcolor: {}", data);
        return null;
      }
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutableVariationColorAnnotation.class;
    }
  }
}
