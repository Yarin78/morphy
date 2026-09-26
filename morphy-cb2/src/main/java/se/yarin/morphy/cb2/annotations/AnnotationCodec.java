package se.yarin.morphy.cb2.annotations;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.NAG;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.cb2.TextEncoding;
import se.yarin.morphy.cb2.storage.ByteStore;
import se.yarin.morphy.chessbase.Medal;
import se.yarin.morphy.chessbase.Nation;
import se.yarin.morphy.chessbase.annotations.BlackClockAnnotation;
import se.yarin.morphy.chessbase.annotations.ComputerEvaluationAnnotation;
import se.yarin.morphy.chessbase.annotations.CriticalPositionAnnotation;
import se.yarin.morphy.chessbase.annotations.GameQuotationAnnotation;
import se.yarin.morphy.chessbase.annotations.GraphicalAnnotationColor;
import se.yarin.morphy.chessbase.annotations.GraphicalArrowsAnnotation;
import se.yarin.morphy.chessbase.annotations.GraphicalSquaresAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableArrow;
import se.yarin.morphy.chessbase.annotations.ImmutableBlackClockAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableComputerEvaluationAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableCriticalPositionAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableGraphicalArrowsAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableGraphicalSquaresAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableMedalAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutablePawnStructureAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutablePiecePathAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableSquare;
import se.yarin.morphy.chessbase.annotations.ImmutableSymbolAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableTextAfterMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableTextBeforeMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableTimeControlAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableTimeSerie;
import se.yarin.morphy.chessbase.annotations.ImmutableTimeSpentAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableTrainingAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableUnknownAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableVariationColorAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableVideoStreamTimeAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableWebLinkAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableWhiteClockAnnotation;
import se.yarin.morphy.chessbase.annotations.MedalAnnotation;
import se.yarin.morphy.chessbase.annotations.PawnStructureAnnotation;
import se.yarin.morphy.chessbase.annotations.PiecePathAnnotation;
import se.yarin.morphy.chessbase.annotations.SymbolAnnotation;
import se.yarin.morphy.chessbase.annotations.TextAfterMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.TextBeforeMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.TimeControlAnnotation;
import se.yarin.morphy.chessbase.annotations.TimeSpentAnnotation;
import se.yarin.morphy.chessbase.annotations.TrainingAnnotation;
import se.yarin.morphy.chessbase.annotations.UnknownAnnotation;
import se.yarin.morphy.chessbase.annotations.VariationColorAnnotation;
import se.yarin.morphy.chessbase.annotations.VideoStreamTimeAnnotation;
import se.yarin.morphy.chessbase.annotations.WebLinkAnnotation;
import se.yarin.morphy.chessbase.annotations.WhiteClockAnnotation;

/**
 * Reads and writes single annotations in the {@code .2cba} format: a {@code short} type followed
 * by data whose length only the type determines. See format/v2/3-annotations.md.
 */
public final class AnnotationCodec {
  private static final Logger log = LoggerFactory.getLogger(AnnotationCodec.class);

  public static final int TEXT_AFTER = 0x02;
  public static final int SYMBOLS = 0x03;
  public static final int SQUARES = 0x04;
  public static final int ARROWS = 0x05;
  public static final int TIME_SPENT = 0x07;
  public static final int TYPE_08 = 0x08;
  public static final int TRAINING = 0x09;
  public static final int QUOTATION = 0x13;
  public static final int PAWN_STRUCTURE = 0x14;
  public static final int PIECE_PATH = 0x15;
  public static final int WHITE_CLOCK = 0x16;
  public static final int BLACK_CLOCK = 0x17;
  public static final int CRITICAL_POSITION = 0x18;
  public static final int WEB_LINK = 0x1c;
  public static final int COMPUTER_EVALUATION = 0x21;
  public static final int MEDALS = 0x22;
  public static final int VARIATION_COLOR = 0x23;
  public static final int TIME_CONTROL = 0x24;
  public static final int VIDEO_STREAM_TIME = 0x25;
  public static final int EVALUATIONS = 0x26;
  public static final int TEXT_BEFORE = 0x82;

  // The language of a text annotation is a small number of its own, not a nation code
  private static final Map<Integer, Nation> LANGUAGES =
      Map.of(
          0, Nation.ENGLAND,
          1, Nation.GERMANY,
          2, Nation.FRANCE,
          3, Nation.SPAIN,
          4, Nation.ITALY,
          5, Nation.NETHERLANDS,
          6, Nation.PORTUGAL,
          7, Nation.NONE,
          12, Nation.POLAND,
          18, Nation.GREECE);

  /** Thrown when an annotation can't be read, making the rest of its record unreadable too. */
  public static final class UnreadableAnnotationException extends Exception {
    public UnreadableAnnotationException(String message) {
      super(message);
    }
  }

  private AnnotationCodec() {}

  /**
   * Reads one annotation, the type included.
   *
   * @param buf positioned at the type; left after the annotation
   * @return the annotation
   * @throws UnreadableAnnotationException if the type is unknown or the data doesn't fit it
   */
  public static @NotNull Annotation read(@NotNull ByteBuffer buf)
      throws UnreadableAnnotationException {
    int start = buf.position();
    int type = buf.getShort() & 0xFFFF;
    try {
      return switch (type) {
        case TEXT_AFTER, TEXT_BEFORE -> readText(buf, type);
        case SYMBOLS -> readSymbols(buf);
        case SQUARES -> readSquares(buf);
        case ARROWS -> readArrows(buf);
        case TIME_SPENT -> {
          int unknown = buf.get() & 0xFF, s = buf.get() & 0xFF, m = buf.get() & 0xFF;
          int h = buf.get() & 0xFF;
          yield ImmutableTimeSpentAnnotation.of(h, m, s, unknown);
        }
        case TYPE_08 -> ImmutableUnknownAnnotation.of(type, bytes(buf, 4));
        case TRAINING -> ImmutableTrainingAnnotation.of(bytes(buf, trainingLength(buf)));
        case QUOTATION -> QuotationCodec.read(buf);
        case PAWN_STRUCTURE -> ImmutablePawnStructureAnnotation.of(buf.get() & 0xFF);
        case PIECE_PATH -> readPiecePath(buf, start);
        case WHITE_CLOCK -> ImmutableWhiteClockAnnotation.of(buf.getInt());
        case BLACK_CLOCK -> ImmutableBlackClockAnnotation.of(buf.getInt());
        case CRITICAL_POSITION -> readCriticalPosition(buf, start);
        case WEB_LINK -> readWebLink(buf, start);
        case COMPUTER_EVALUATION ->
            ImmutableComputerEvaluationAnnotation.of(buf.getShort(), buf.getShort(), buf.getShort());
        case MEDALS -> ImmutableMedalAnnotation.of(Medal.decode(buf.getInt()));
        case VARIATION_COLOR -> {
          int flag = buf.get();
          int b = buf.get() & 0xFF, g = buf.get() & 0xFF, r = buf.get() & 0xFF;
          yield ImmutableVariationColorAnnotation.of(r, g, b, (flag & 2) != 0, (flag & 1) != 0);
        }
        case TIME_CONTROL -> readTimeControl(buf, start);
        case VIDEO_STREAM_TIME -> ImmutableVideoStreamTimeAnnotation.of(buf.getInt());
        case EVALUATIONS -> {
          int length = 5 + buf.getInt(buf.position() + 1);
          yield ImmutableUnknownAnnotation.of(type, bytes(buf, length));
        }
        default ->
            throw new UnreadableAnnotationException(
                String.format("Unknown annotation type %02x", type));
      };
    } catch (BufferUnderflowException | IndexOutOfBoundsException | IllegalArgumentException e) {
      throw new UnreadableAnnotationException(
          String.format("Annotation of type %02x doesn't fit its record: %s", type, e));
    }
  }

  /** Reads an annotation this codec only knows the length of, keeping its data as it is. */
  private static Annotation unknown(ByteBuffer buf, int start) {
    int type = buf.getShort(start) & 0xFFFF;
    byte[] data = new byte[buf.position() - start - 2];
    buf.get(start + 2, data);
    return ImmutableUnknownAnnotation.of(type, data);
  }

  private static byte[] bytes(ByteBuffer buf, int length) {
    if (length < 0) {
      throw new IllegalArgumentException("Negative length " + length);
    }
    byte[] data = new byte[length];
    buf.get(data);
    return data;
  }

  private static Annotation readText(ByteBuffer buf, int type) {
    int unknown = buf.getShort();
    int language = buf.getShort();
    String text = TextEncoding.decode(bytes(buf, buf.getInt()));
    Nation nation = LANGUAGES.get(language);
    if (nation == null) {
      log.warn("Unknown language {} in a text annotation", language);
      nation = Nation.NONE;
    }
    return type == TEXT_AFTER
        ? ImmutableTextAfterMoveAnnotation.builder().text(text).language(nation).unknown(unknown).build()
        : ImmutableTextBeforeMoveAnnotation.builder().text(text).language(nation).unknown(unknown).build();
  }

  private static Annotation readSymbols(ByteBuffer buf) {
    int move = buf.get() & 0xFF, evaluation = buf.get() & 0xFF, prefix = buf.get() & 0xFF;
    NAG[] nags = NAG.values();
    if (move >= nags.length || evaluation >= nags.length || prefix >= nags.length) {
      buf.position(buf.position() - 3);
      return ImmutableUnknownAnnotation.of(SYMBOLS, bytes(buf, 3));
    }
    return ImmutableSymbolAnnotation.of(nags[move], nags[prefix], nags[evaluation]);
  }

  private static Annotation readSquares(ByteBuffer buf) {
    int length = buf.getInt();
    int start = buf.position();
    List<GraphicalSquaresAnnotation.Square> squares = new ArrayList<>();
    boolean valid = length % 2 == 0;
    for (int i = 0; valid && i < length / 2; i++) {
      int color = buf.get() & 0xFF;
      int sqi = (buf.get() & 0xFF) - 1;
      valid = sqi >= 0 && sqi < 64 && color <= GraphicalAnnotationColor.maxColor();
      if (valid) {
        squares.add(ImmutableSquare.of(GraphicalAnnotationColor.fromInt(color), sqi));
      }
    }
    if (!valid) {
      buf.position(start - 4);
      return ImmutableUnknownAnnotation.of(SQUARES, bytes(buf, 4 + length));
    }
    return ImmutableGraphicalSquaresAnnotation.of(squares);
  }

  private static Annotation readArrows(ByteBuffer buf) {
    int length = buf.getInt();
    int start = buf.position();
    List<GraphicalArrowsAnnotation.Arrow> arrows = new ArrayList<>();
    boolean valid = length % 3 == 0;
    for (int i = 0; valid && i < length / 3; i++) {
      int color = buf.get() & 0xFF;
      int from = (buf.get() & 0xFF) - 1;
      int to = (buf.get() & 0xFF) - 1;
      valid =
          from >= 0 && from < 64 && to >= 0 && to < 64 && color <= GraphicalAnnotationColor.maxColor();
      if (valid) {
        arrows.add(ImmutableArrow.of(GraphicalAnnotationColor.fromInt(color), from, to));
      }
    }
    if (!valid) {
      buf.position(start - 4);
      return ImmutableUnknownAnnotation.of(ARROWS, bytes(buf, 4 + length));
    }
    return ImmutableGraphicalArrowsAnnotation.of(arrows);
  }

  private static Annotation readPiecePath(ByteBuffer buf, int start) {
    int length = buf.getInt();
    if (length != 2) {
      buf.position(buf.position() + length);
      return unknown(buf, start);
    }
    int type = buf.get() & 0xFF;
    int sqi = (buf.get() & 0xFF) - 1;
    return ImmutablePiecePathAnnotation.of(type, sqi);
  }

  private static Annotation readCriticalPosition(ByteBuffer buf, int start) {
    int type = buf.get() & 0xFF;
    CriticalPositionAnnotation.CriticalPositionType[] types =
        CriticalPositionAnnotation.CriticalPositionType.values();
    if (type >= types.length) {
      return unknown(buf, start);
    }
    return ImmutableCriticalPositionAnnotation.of(types[type]);
  }

  private static Annotation readWebLink(ByteBuffer buf, int start) {
    int marker = buf.get();
    String url = TextEncoding.decode(bytes(buf, buf.getInt()));
    String caption = TextEncoding.decode(bytes(buf, buf.getInt()));
    if (marker != 1) {
      return unknown(buf, start);
    }
    return ImmutableWebLinkAnnotation.of(url, caption);
  }

  private static Annotation readTimeControl(ByteBuffer buf, int start) {
    int marker = buf.get();
    List<TimeControlAnnotation.TimeSerie> series = new ArrayList<>();
    boolean gap = false;
    for (int i = 0; i < 3; i++) {
      int initial = buf.getInt(), increment = buf.getInt();
      int moves = buf.getShort() & 0xFFFF, kind = buf.get() & 0xFF;
      if (initial == 0 && increment == 0 && moves == 0 && kind == 0) {
        gap = true;
      } else {
        // An unused serie between used ones couldn't be written back where it was
        if (gap) {
          marker = -1;
        }
        series.add(ImmutableTimeSerie.of(initial, increment, moves, kind));
      }
    }
    int trailing = buf.getInt();
    if (marker != 1 || trailing != 0) {
      return unknown(buf, start);
    }
    return ImmutableTimeControlAnnotation.of(series);
  }

  private static int trainingLength(ByteBuffer buf) {
    int start = buf.position();
    if (buf.get(start + 2) != 1) {
      throw new IllegalArgumentException("Training annotation of an unknown layout");
    }
    int end = start + 12;
    for (int i = 0; i < 4; i++) {
      end = itemsEnd(buf, end);
    }
    int solutions = buf.get(end) & 0xFF;
    end++;
    for (int i = 0; i < solutions; i++) {
      end = itemsEnd(buf, end + 4);
    }
    return end - start;
  }

  private static int itemsEnd(ByteBuffer buf, int pos) {
    int count = buf.getShort(pos) & 0xFFFF;
    pos += 2;
    for (int i = 0; i < count; i++) {
      pos += 6 + buf.getInt(pos + 2);
    }
    return pos;
  }

  /**
   * Writes one annotation, the type included.
   *
   * @return false if the annotation has no v2 form, in which case nothing is written
   */
  public static boolean write(@NotNull ByteBuffer buf, @NotNull Annotation annotation) {
    Integer type = typeOf(annotation);
    if (type == null) {
      return false;
    }
    buf.putShort(type.shortValue());
    switch (annotation) {
      case TextAfterMoveAnnotation a -> writeText(buf, a.unknown(), a.language(), a.text());
      case TextBeforeMoveAnnotation a -> writeText(buf, a.unknown(), a.language(), a.text());
      case SymbolAnnotation a ->
          buf.put((byte) a.moveComment().ordinal())
              .put((byte) a.lineEvaluation().ordinal())
              .put((byte) a.movePrefix().ordinal());
      case GraphicalSquaresAnnotation a -> {
        buf.putInt(2 * a.squares().size());
        for (GraphicalSquaresAnnotation.Square s : a.squares()) {
          buf.put((byte) s.color().getColorId()).put((byte) (s.sqi() + 1));
        }
      }
      case GraphicalArrowsAnnotation a -> {
        buf.putInt(3 * a.arrows().size());
        for (GraphicalArrowsAnnotation.Arrow arrow : a.arrows()) {
          buf.put((byte) arrow.color().getColorId())
              .put((byte) (arrow.fromSqi() + 1))
              .put((byte) (arrow.toSqi() + 1));
        }
      }
      case TimeSpentAnnotation a ->
          buf.put((byte) a.unknownByte())
              .put((byte) a.seconds())
              .put((byte) a.minutes())
              .put((byte) a.hours());
      case TrainingAnnotation a -> buf.put(a.rawData());
      case GameQuotationAnnotation a -> QuotationCodec.write(buf, a);
      case PawnStructureAnnotation a -> buf.put((byte) a.type());
      case PiecePathAnnotation a -> buf.putInt(2).put((byte) a.type()).put((byte) (a.sqi() + 1));
      case WhiteClockAnnotation a -> buf.putInt(a.clockTime());
      case BlackClockAnnotation a -> buf.putInt(a.clockTime());
      case CriticalPositionAnnotation a -> buf.put((byte) a.type().ordinal());
      case WebLinkAnnotation a -> {
        buf.put((byte) 1);
        putString(buf, a.url());
        putString(buf, a.text());
      }
      case ComputerEvaluationAnnotation a ->
          buf.putShort((short) a.eval()).putShort((short) a.evalType()).putShort((short) a.ply());
      case MedalAnnotation a -> buf.putInt(Medal.encode(a.medals()));
      case VariationColorAnnotation a ->
          buf.put((byte) ((a.onlyMainline() ? 1 : 0) | (a.onlyMoves() ? 2 : 0)))
              .put((byte) a.blue())
              .put((byte) a.green())
              .put((byte) a.red());
      case TimeControlAnnotation a -> {
        buf.put((byte) 1);
        for (int i = 0; i < 3; i++) {
          if (i < a.timeSeries().size()) {
            TimeControlAnnotation.TimeSerie s = a.timeSeries().get(i);
            buf.putInt(s.start()).putInt(s.increment()).putShort((short) s.moves());
            buf.put((byte) s.type());
          } else {
            buf.putInt(0).putInt(0).putShort((short) 0).put((byte) 0);
          }
        }
        buf.putInt(0);
      }
      case VideoStreamTimeAnnotation a -> buf.putInt(a.time());
      case UnknownAnnotation a -> buf.put(a.rawData());
      default -> throw new IllegalStateException("Unexpected annotation " + annotation);
    }
    return true;
  }

  /** The v2 type of an annotation, or null if it has no v2 form. */
  public static @Nullable Integer typeOf(@NotNull Annotation annotation) {
    return switch (annotation) {
      case TextAfterMoveAnnotation a -> TEXT_AFTER;
      case TextBeforeMoveAnnotation a -> TEXT_BEFORE;
      case SymbolAnnotation a -> SYMBOLS;
      case GraphicalSquaresAnnotation a -> SQUARES;
      case GraphicalArrowsAnnotation a -> ARROWS;
      case TimeSpentAnnotation a -> TIME_SPENT;
      case TrainingAnnotation a -> TRAINING;
      case GameQuotationAnnotation a -> QuotationCodec.canWrite(a) ? QUOTATION : null;
      case PawnStructureAnnotation a -> PAWN_STRUCTURE;
      case PiecePathAnnotation a -> PIECE_PATH;
      case WhiteClockAnnotation a -> WHITE_CLOCK;
      case BlackClockAnnotation a -> BLACK_CLOCK;
      case CriticalPositionAnnotation a -> CRITICAL_POSITION;
      case WebLinkAnnotation a -> WEB_LINK;
      case ComputerEvaluationAnnotation a -> COMPUTER_EVALUATION;
      case MedalAnnotation a -> MEDALS;
      case VariationColorAnnotation a -> VARIATION_COLOR;
      case TimeControlAnnotation a -> TIME_CONTROL;
      case VideoStreamTimeAnnotation a -> VIDEO_STREAM_TIME;
      case UnknownAnnotation a -> isV2UnknownType(a.annotationType()) ? a.annotationType() : null;
      default -> null;
    };
  }

  // The types kept as unknown annotations whose data is written back as it was read
  private static boolean isV2UnknownType(int type) {
    return switch (type) {
      case TYPE_08, EVALUATIONS, SYMBOLS, SQUARES, ARROWS, PIECE_PATH, CRITICAL_POSITION, WEB_LINK,
          TIME_CONTROL ->
          true;
      default -> false;
    };
  }

  private static void writeText(ByteBuffer buf, int unknown, Nation language, String text) {
    buf.putShort((short) unknown);
    buf.putShort((short) languageCode(language));
    putString(buf, text);
  }

  private static int languageCode(Nation language) {
    for (Map.Entry<Integer, Nation> entry : LANGUAGES.entrySet()) {
      if (entry.getValue() == language) {
        return entry.getKey();
      }
    }
    return 7; // any language
  }

  private static void putString(ByteBuffer buf, String text) {
    byte[] bytes = TextEncoding.encode(text);
    buf.putInt(bytes.length);
    buf.put(bytes);
  }

  /** An upper bound of the bytes an annotation takes, for sizing buffers. */
  public static int maxSize(@NotNull Annotation annotation) {
    return switch (annotation) {
      case TextAfterMoveAnnotation a -> 10 + 3 * a.text().length();
      case TextBeforeMoveAnnotation a -> 10 + 3 * a.text().length();
      case WebLinkAnnotation a -> 16 + 3 * (a.url().length() + a.text().length());
      case TrainingAnnotation a -> 2 + a.rawData().length;
      case UnknownAnnotation a -> 2 + a.rawData().length;
      case GameQuotationAnnotation a -> 2 + QuotationCodec.size(a);
      case GraphicalSquaresAnnotation a -> 6 + 2 * a.squares().size();
      case GraphicalArrowsAnnotation a -> 6 + 3 * a.arrows().size();
      default -> 64;
    };
  }

  /** A little-endian buffer large enough for these annotations. */
  static @NotNull ByteBuffer bufferFor(int size) {
    return ByteStore.allocate(size);
  }
}
