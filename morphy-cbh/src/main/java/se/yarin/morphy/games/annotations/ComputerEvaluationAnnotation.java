package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.util.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.regex.Pattern;

@Value.Immutable
public abstract class ComputerEvaluationAnnotation extends Annotation {
  @Value.Parameter
  public abstract int eval(); // centipoints

  @Value.Parameter
  public abstract int evalType(); // 0 = ordinary eval, 1 = number of moves to mate, 3 = ??

  @Value.Parameter
  public abstract int ply();

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    switch (evalType()) {
      case 0:
        if (eval() >= 0) sb.append("+");
        sb.append(String.format("%.2f", eval() / 100.0));
        break;
      case 1:
        sb.append(String.format("#%d", eval()));
        break;
      case 3:
        return ""; // There are a few games with this annotation but ChessBase doesn't show anything
      // for them
      default:
        sb.append(String.format("?? %d %d %d", eval(), evalType(), ply()));
    }

    if (ply() > 0) {
      sb.append(String.format("/%d", ply()));
    }

    return sb.toString();
  }

  public static class Serializer implements AnnotationSerializer {
    @Override
    public void serialize(ByteBuffer buf, Annotation annotation) {
      ComputerEvaluationAnnotation cea = (ComputerEvaluationAnnotation) annotation;
      ByteBufferUtil.putShortL(buf, cea.eval());
      ByteBufferUtil.putShortL(buf, cea.evalType());
      ByteBufferUtil.putShortL(buf, cea.ply());
    }

    @Override
    public ComputerEvaluationAnnotation deserialize(ByteBuffer buf, int length) {
      short eval = ByteBufferUtil.getSignedShortL(buf);
      short type = ByteBufferUtil.getSignedShortL(buf);
      short depth = ByteBufferUtil.getSignedShortL(buf);
      return ImmutableComputerEvaluationAnnotation.of(eval, type, depth);
    }

    @Override
    public Class getAnnotationClass() {
      return ImmutableComputerEvaluationAnnotation.class;
    }

    @Override
    public int getAnnotationType() {
      return 0x21;
    }
  }

  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Logger log = LoggerFactory.getLogger(PgnCodec.class);
    private static final Pattern EVAL_PATTERN = Pattern.compile("\\[%eval\\s+([^\\]]+)\\]");

    @Override
    @NotNull
    public Pattern getPattern() {
      return EVAL_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      ComputerEvaluationAnnotation a = (ComputerEvaluationAnnotation) annotation;
      if (a.evalType() == 3) {
        return null; // Skip unknown eval types
      }

      StringBuilder sb = new StringBuilder("[%eval ");
      if (a.evalType() == 1) {
        // Mate distance
        sb.append("#").append(a.eval());
      } else {
        // Centipawns - use Locale.US to ensure period as decimal separator
        double pawns = a.eval() / 100.0;
        if (pawns >= 0) sb.append("+");
        sb.append(String.format(Locale.US, "%.2f", pawns));
      }
      if (a.ply() > 0) {
        sb.append("/").append(a.ply());
      }
      sb.append("]");
      return sb.toString();
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      try {
        // Format: [+/-]N.NN/depth or #N/depth
        String[] parts = data.split("/");
        String evalPart = parts[0];
        int depth = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

        int eval;
        int evalType;

        if (evalPart.startsWith("#")) {
          // Mate distance
          evalType = 1;
          eval = Integer.parseInt(evalPart.substring(1));
        } else {
          // Centipawns
          evalType = 0;
          double pawns = Double.parseDouble(evalPart);
          eval = (int) Math.round(pawns * 100);
        }

        return ImmutableComputerEvaluationAnnotation.of(eval, evalType, depth);
      } catch (Exception e) {
        log.warn("Failed to parse eval: {}", data);
        return null;
      }
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutableComputerEvaluationAnnotation.class;
    }
  }
}
