package se.yarin.morphy.chessbase.convert;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Chess;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.NAG;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.Medal;
import se.yarin.morphy.chessbase.Nation;
import se.yarin.morphy.chessbase.annotations.AnnotationPgnUtil;
import se.yarin.morphy.chessbase.annotations.BlackClockAnnotation;
import se.yarin.morphy.chessbase.annotations.ComputerEvaluationAnnotation;
import se.yarin.morphy.chessbase.annotations.CorrespondenceMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.CriticalPositionAnnotation;
import se.yarin.morphy.chessbase.annotations.EvaluationsAnnotation;
import se.yarin.morphy.chessbase.annotations.GameQuotationAnnotation;
import se.yarin.morphy.chessbase.annotations.GraphicalAnnotationColor;
import se.yarin.morphy.chessbase.annotations.GraphicalArrowsAnnotation;
import se.yarin.morphy.chessbase.annotations.GraphicalSquaresAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableArrow;
import se.yarin.morphy.chessbase.annotations.ImmutableBlackClockAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableComputerEvaluationAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableCorrespondenceMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableCriticalPositionAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableEvaluationsAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableGraphicalArrowsAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableGraphicalSquaresAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableInvalidAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableMedalAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutablePawnStructureAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutablePiecePathAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableSquare;
import se.yarin.morphy.chessbase.annotations.ImmutableSymbolAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableTextAfterMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableTextBeforeMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableTimeSpentAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableTrainingAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableUnknownAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableVariationColorAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableVideoStreamTimeAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableWebLinkAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableWhiteClockAnnotation;
import se.yarin.morphy.chessbase.annotations.InvalidAnnotation;
import se.yarin.morphy.chessbase.annotations.MedalAnnotation;
import se.yarin.morphy.chessbase.annotations.PawnStructureAnnotation;
import se.yarin.morphy.chessbase.annotations.PiecePathAnnotation;
import se.yarin.morphy.chessbase.annotations.SymbolAnnotation;
import se.yarin.morphy.chessbase.annotations.TextAfterMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.TextBeforeMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.TimeSpentAnnotation;
import se.yarin.morphy.chessbase.annotations.TrainingAnnotation;
import se.yarin.morphy.chessbase.annotations.UnknownAnnotation;
import se.yarin.morphy.chessbase.annotations.VariationColorAnnotation;
import se.yarin.morphy.chessbase.annotations.VideoStreamTimeAnnotation;
import se.yarin.morphy.chessbase.annotations.WebLinkAnnotation;
import se.yarin.morphy.chessbase.annotations.WhiteClockAnnotation;
import se.yarin.morphy.model.AnnotationDto;
import se.yarin.morphy.pgn.AnnotationDtoMapper;
import se.yarin.morphy.pgn.PgnMoves;

/**
 * Turns ChessBase annotations into {@link AnnotationDto}s and back, without losing anything: every
 * annotation a ChessBase database can hold has a DTO, and one of a kind that isn't known is kept as
 * a {@link AnnotationDto.Raw}.
 */
public class ChessBaseAnnotationDtoMapper extends AnnotationDtoMapper {

  public static final @NotNull ChessBaseAnnotationDtoMapper INSTANCE =
      new ChessBaseAnnotationDtoMapper();

  @Override
  protected @Nullable AnnotationDto toDto(int move, @NotNull Annotation annotation) {
    return switch (annotation) {
      case TextAfterMoveAnnotation a ->
          new AnnotationDto.TextAfter(move, a.text(), language(a.language()), unknown(a.unknown()));
      case TextBeforeMoveAnnotation a ->
          new AnnotationDto.TextBefore(
              move, a.text(), language(a.language()), unknown(a.unknown()));
      case SymbolAnnotation a -> {
        List<Integer> nags = new ArrayList<>();
        for (NAG nag : List.of(a.moveComment(), a.lineEvaluation(), a.movePrefix())) {
          if (nag != NAG.NONE) {
            nags.add(nag.ordinal());
          }
        }
        yield nags.isEmpty() ? null : new AnnotationDto.Symbols(move, nags);
      }
      case GraphicalSquaresAnnotation a ->
          new AnnotationDto.Squares(
              move,
              a.squares().stream()
                  .map(s -> new AnnotationDto.ColoredSquare(color(s.color()), Chess.sqiToStr(s.sqi())))
                  .toList());
      case GraphicalArrowsAnnotation a ->
          new AnnotationDto.Arrows(
              move,
              a.arrows().stream()
                  .map(
                      r ->
                          new AnnotationDto.ColoredArrow(
                              color(r.color()),
                              Chess.sqiToStr(r.fromSqi()),
                              Chess.sqiToStr(r.toSqi())))
                  .toList());
      case WhiteClockAnnotation a -> new AnnotationDto.WhiteClock(move, a.clockTime());
      case BlackClockAnnotation a -> new AnnotationDto.BlackClock(move, a.clockTime());
      case TimeSpentAnnotation a ->
          new AnnotationDto.TimeSpent(
              move, a.hours(), a.minutes(), a.seconds(), unknown(a.unknownByte()));
      case ComputerEvaluationAnnotation a ->
          new AnnotationDto.Eval(move, a.eval(), a.evalType(), a.ply());
      case EvaluationsAnnotation a ->
          new AnnotationDto.Evaluations(
              move,
              a.evaluations().stream()
                  .map(e -> new AnnotationDto.Evaluation(e.eval(), e.depth(), e.type()))
                  .toList());
      case CriticalPositionAnnotation a ->
          new AnnotationDto.CriticalPosition(move, a.type().name().toLowerCase(Locale.ROOT));
      case MedalAnnotation a ->
          new AnnotationDto.Medals(move, a.medals().stream().map(Medal::name).toList());
      case PawnStructureAnnotation a -> new AnnotationDto.PawnStructure(move, a.type());
      case PiecePathAnnotation a ->
          new AnnotationDto.PiecePath(move, a.type(), Chess.sqiToStr(a.sqi()));
      case VariationColorAnnotation a ->
          new AnnotationDto.VariationColor(
              move,
              String.format("#%02x%02x%02x", a.red(), a.green(), a.blue()),
              a.onlyMoves(),
              a.onlyMainline());
      case VideoStreamTimeAnnotation a -> new AnnotationDto.VideoStreamTime(move, a.time());
      case WebLinkAnnotation a -> new AnnotationDto.WebLink(move, a.url(), a.text());
      case GameQuotationAnnotation a -> quotation(move, a);
      case TrainingAnnotation a -> new AnnotationDto.Training(move, a.rawData());
      case CorrespondenceMoveAnnotation a -> new AnnotationDto.CorrespondenceMove(move, a.rawData());
      case UnknownAnnotation a ->
          new AnnotationDto.Raw(move, a.annotationType(), a.rawData(), false);
      case InvalidAnnotation a -> new AnnotationDto.Raw(move, a.annotationType(), a.rawData(), true);
      default -> super.toDto(move, annotation);
    };
  }

  @Override
  protected @NotNull List<Annotation> fromDto(@NotNull AnnotationDto dto) {
    Annotation annotation =
        switch (dto) {
          case AnnotationDto.TextAfter t ->
              ImmutableTextAfterMoveAnnotation.builder()
                  .text(t.text())
                  .language(nation(t.language()))
                  .unknown(t.unknown() == null ? 0 : t.unknown())
                  .build();
          case AnnotationDto.TextBefore t ->
              ImmutableTextBeforeMoveAnnotation.builder()
                  .text(t.text())
                  .language(nation(t.language()))
                  .unknown(t.unknown() == null ? 0 : t.unknown())
                  .build();
          case AnnotationDto.Symbols s ->
              SymbolAnnotation.of(s.nags().stream().map(AnnotationDtoMapper::nag).toArray(NAG[]::new));
          case AnnotationDto.Squares s ->
              ImmutableGraphicalSquaresAnnotation.of(
                  s.squares().stream()
                      .map(q -> ImmutableSquare.of(color(q.color()), square(q.square())))
                      .toList());
          case AnnotationDto.Arrows a ->
              ImmutableGraphicalArrowsAnnotation.of(
                  a.arrows().stream()
                      .map(
                          r ->
                              ImmutableArrow.of(
                                  color(r.color()), square(r.from()), square(r.to())))
                      .toList());
          case AnnotationDto.WhiteClock c -> ImmutableWhiteClockAnnotation.of(c.centiseconds());
          case AnnotationDto.BlackClock c -> ImmutableBlackClockAnnotation.of(c.centiseconds());
          case AnnotationDto.TimeSpent t ->
              ImmutableTimeSpentAnnotation.of(
                  t.hours(), t.minutes(), t.seconds(), t.unknown() == null ? 0 : t.unknown());
          case AnnotationDto.Eval e ->
              ImmutableComputerEvaluationAnnotation.of(e.eval(), e.evalType(), e.depth());
          case AnnotationDto.Evaluations e ->
              ImmutableEvaluationsAnnotation.of(
                  e.evaluations().stream()
                      .map(v -> new EvaluationsAnnotation.Evaluation(v.eval(), v.depth(), v.evalType()))
                      .toList());
          case AnnotationDto.CriticalPosition c ->
              ImmutableCriticalPositionAnnotation.of(
                  enumValue(CriticalPositionAnnotation.CriticalPositionType.class, c.phase()));
          case AnnotationDto.Medals m -> {
            EnumSet<Medal> medals = EnumSet.noneOf(Medal.class);
            m.medals().forEach(name -> medals.add(enumValue(Medal.class, name)));
            yield ImmutableMedalAnnotation.of(medals);
          }
          case AnnotationDto.PawnStructure p ->
              ImmutablePawnStructureAnnotation.of(p.pawnStructureType());
          case AnnotationDto.PiecePath p ->
              ImmutablePiecePathAnnotation.of(p.pathType(), square(p.square()));
          case AnnotationDto.VariationColor v -> variationColor(v);
          case AnnotationDto.VideoStreamTime v -> ImmutableVideoStreamTimeAnnotation.of(v.time());
          case AnnotationDto.WebLink w -> ImmutableWebLinkAnnotation.of(w.url(), w.text());
          case AnnotationDto.Quotation q -> quotation(q);
          case AnnotationDto.Training t -> ImmutableTrainingAnnotation.of(t.data());
          case AnnotationDto.CorrespondenceMove c ->
              ImmutableCorrespondenceMoveAnnotation.of(c.data());
          case AnnotationDto.Raw r ->
              r.invalid()
                  ? ImmutableInvalidAnnotation.of(r.annotationType(), r.data())
                  : ImmutableUnknownAnnotation.of(r.annotationType(), r.data());
        };
    return List.of(annotation);
  }

  // ── Values ───────────────────────────────────────────────────────────────

  private static @Nullable String language(@NotNull Nation nation) {
    return nation == Nation.NONE ? null : nation.getIocCode();
  }

  private static @NotNull Nation nation(@Nullable String language) {
    if (language == null) {
      return Nation.NONE;
    }
    Nation nation = Nation.fromIOC(language);
    if (nation == Nation.NONE) {
      throw new IllegalArgumentException("Unknown language: " + language);
    }
    return nation;
  }

  private static @Nullable Integer unknown(int value) {
    return value == 0 ? null : value;
  }

  private static @NotNull String color(@NotNull GraphicalAnnotationColor color) {
    return color.name().toLowerCase(Locale.ROOT);
  }

  private static @NotNull GraphicalAnnotationColor color(@NotNull String name) {
    return enumValue(GraphicalAnnotationColor.class, name);
  }

  private static int square(@NotNull String square) {
    int sqi = square.length() == 2 ? Chess.strToSqi(square) : -1;
    if (sqi < 0) {
      throw new IllegalArgumentException("Invalid square: " + square);
    }
    return sqi;
  }

  private static <E extends Enum<E>> @NotNull E enumValue(
      @NotNull Class<E> enumClass, @NotNull String name) {
    try {
      return Enum.valueOf(enumClass, name.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Invalid " + enumClass.getSimpleName() + ": " + name, e);
    }
  }

  private static @NotNull VariationColorAnnotation variationColor(
      @NotNull AnnotationDto.VariationColor v) {
    if (!v.color().matches("#[0-9a-fA-F]{6}")) {
      throw new IllegalArgumentException("Invalid variation color: " + v.color());
    }
    int rgb = Integer.parseInt(v.color().substring(1), 16);
    return ImmutableVariationColorAnnotation.of(
        (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, v.onlyMoves(), v.onlyMainline());
  }

  // ── Quotations ───────────────────────────────────────────────────────────

  private static @NotNull AnnotationDto.Quotation quotation(
      int move, @NotNull GameQuotationAnnotation a) {
    Map<String, String> header = new LinkedHashMap<>();
    a.header()
        .getAllFields()
        .forEach(
            (name, value) -> {
              String s = AnnotationPgnUtil.serializeHeaderValue(value);
              if (!s.isEmpty()) {
                header.put(name, s);
              }
            });
    String moves = null;
    String fen = null;
    if (a.hasGame()) {
      GameMovesModel model = a.getGameModel().moves();
      moves = PgnMoves.PLAIN.toPgn(model);
      fen = PgnMoves.PLAIN.toFen(model);
    }
    return new AnnotationDto.Quotation(move, header, moves, fen, unknown(a.unknown()));
  }

  private static @NotNull GameQuotationAnnotation quotation(@NotNull AnnotationDto.Quotation q) {
    GameHeaderModel header = new GameHeaderModel();
    q.header()
        .forEach(
            (name, value) -> {
              Object v = AnnotationPgnUtil.deserializeHeaderValue(name, value);
              if (v != null) {
                header.setField(name, v);
              }
            });
    int unknown = q.unknown() == null ? 0 : q.unknown();
    if (q.moves() == null) {
      return new GameQuotationAnnotation(header, unknown);
    }
    GameMovesModel moves = PgnMoves.PLAIN.fromPgn(q.moves(), q.fen(), false);
    return new GameQuotationAnnotation(header, unknown, () -> moves, null);
  }
}
