package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.util.ByteBufferUtil;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.chess.NAG;
import se.yarin.chess.NAGType;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.NAGAnnotation;

import java.nio.ByteBuffer;
import java.util.*;

@Value.Immutable
public abstract class SymbolAnnotation extends Annotation implements StatisticalAnnotation {
  @Value.Parameter
  public abstract NAG moveComment();

  @Value.Parameter
  public abstract NAG movePrefix();

  @Value.Parameter
  public abstract NAG lineEvaluation();

  public static SymbolAnnotation of(@NotNull NAG... nags) {
    NAG comment = NAG.NONE;
    NAG prefix = NAG.NONE;
    NAG eval = NAG.NONE;
    for (NAG nag : nags) {
      if (nag == null) {
        continue;
      }
      switch (nag.getType()) {
        case MOVE_COMMENT -> comment = nag;
        case LINE_EVALUATION -> eval = nag;
        case MOVE_PREFIX -> prefix = nag;
        default -> {}
      }
    }
    return ImmutableSymbolAnnotation.of(comment, prefix, eval);
  }

  @Override
  public int priority() {
    return 100;
  }

  @Override
  public String format(@NotNull String text, boolean ascii) {
    // First add move comment
    String symbol = ascii ? moveComment().toASCIIString() : moveComment().toUnicodeString();
    if (symbol.length() <= 2) {
      text += symbol;
    } else {
      text += " " + symbol;
    }

    // Then move prefix
    String pre = ascii ? movePrefix().toASCIIString() : movePrefix().toUnicodeString();
    if (pre.length() > 0) {
      text = pre + " " + text;
    }

    // Then line evaluation
    String eval = ascii ? lineEvaluation().toASCIIString() : lineEvaluation().toUnicodeString();
    if (eval.length() > 0) {
      text += " " + eval;
    }

    return text;
  }

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    stats.noSymbols++;
    stats.flags.add(GameHeaderFlags.SYMBOLS);
  }

  public static class Serializer implements AnnotationSerializer {
    @Override
    public Annotation deserialize(ByteBuffer buf, int length) {
      NAG[] symbols = new NAG[length];
      for (int i = 0; i < length; i++) {
        symbols[i] = NAG.values()[ByteBufferUtil.getUnsignedByte(buf)];
      }
      return ImmutableSymbolAnnotation.of(symbols);
    }

    @Override
    public void serialize(ByteBuffer buf, Annotation annotation) {
      SymbolAnnotation symbolAnnotation = (SymbolAnnotation) annotation;
      int b1 = symbolAnnotation.moveComment().ordinal();
      int b2 = symbolAnnotation.lineEvaluation().ordinal();
      int b3 = symbolAnnotation.movePrefix().ordinal();
      ByteBufferUtil.putByte(buf, b1);
      if (b2 != 0 || b3 != 0) ByteBufferUtil.putByte(buf, b2);
      if (b3 != 0) ByteBufferUtil.putByte(buf, b3);
    }

    @Override
    public int getAnnotationType() {
      return 0x03;
    }

    @Override
    public Class getAnnotationClass() {
      return ImmutableSymbolAnnotation.class;
    }
  }

  /**
   * Converter between SymbolAnnotation and NAGAnnotations.
   * SymbolAnnotation is the storage format (one annotation with up to 3 NAGs),
   * while NAGAnnotations are the generic format (one annotation per NAG).
   */
  public static class NAGConverter {
    private static final Logger log = LoggerFactory.getLogger(NAGConverter.class);

    /**
     * Converts a SymbolAnnotation to a list of NAGAnnotations.
     */
    @NotNull
    public static List<NAGAnnotation> toNAGAnnotations(@NotNull SymbolAnnotation symbolAnnotation) {
      List<NAGAnnotation> annotations = new ArrayList<>();
      if (symbolAnnotation.moveComment() != NAG.NONE) {
        annotations.add(new NAGAnnotation(symbolAnnotation.moveComment()));
      }
      if (symbolAnnotation.lineEvaluation() != NAG.NONE) {
        annotations.add(new NAGAnnotation(symbolAnnotation.lineEvaluation()));
      }
      if (symbolAnnotation.movePrefix() != NAG.NONE) {
        annotations.add(new NAGAnnotation(symbolAnnotation.movePrefix()));
      }
      return annotations;
    }

    /**
     * Converts a list of NAGAnnotations to a SymbolAnnotation.
     * If multiple NAGs of the same type are present, keeps the first and logs a warning.
     */
    @NotNull
    public static Optional<SymbolAnnotation> fromNAGAnnotations(@NotNull List<NAGAnnotation> nagAnnotations) {
      Map<NAGType, NAG> nagsByType = new EnumMap<>(NAGType.class);

      for (NAGAnnotation nagAnnotation : nagAnnotations) {
        NAG nag = nagAnnotation.getNag();
        NAGType type = nag.getType();

        if (type == NAGType.NONE) {
          log.debug("Skipping NAG with NONE type: {}", nag);
          continue;
        }

        if (nagsByType.containsKey(type)) {
          log.warn("Multiple NAGs of type {} found on same node. Keeping {} and dropping {}. " +
                  "ChessBase format only supports one NAG per type.",
                  type, nagsByType.get(type), nag);
        } else {
          nagsByType.put(type, nag);
        }
      }

      if (nagsByType.isEmpty()) {
        return Optional.empty();
      }

      NAG moveComment = nagsByType.getOrDefault(NAGType.MOVE_COMMENT, NAG.NONE);
      NAG lineEvaluation = nagsByType.getOrDefault(NAGType.LINE_EVALUATION, NAG.NONE);
      NAG movePrefix = nagsByType.getOrDefault(NAGType.MOVE_PREFIX, NAG.NONE);

      return Optional.of(ImmutableSymbolAnnotation.of(moveComment, movePrefix, lineEvaluation));
    }
  }
}
