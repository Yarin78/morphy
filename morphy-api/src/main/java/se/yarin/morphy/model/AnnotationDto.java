package se.yarin.morphy.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An annotation of a game, as {@link GameMovesDto} carries it next to the movetext.
 *
 * <p>Every annotation belongs to a move, given by its index in the order the moves appear in the
 * movetext: the first move is 0, and a variation comes right after the move it's an alternative
 * to, before the line it branches from goes on. In {@code 1.e4 c5 (1...c6 2.d4) 2.Nf3} the moves
 * are e4 0, c5 1, c6 2, d4 3 and Nf3 4. An annotation of the game as a whole, before the first
 * move, has the index {@link #GAME}.
 *
 * <p>In JSON the kind of annotation is the {@code type} property, next to the fields of the
 * annotation. Squares are written as {@code "e4"}, colors by their names, like {@code "red"}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AnnotationDto.TextBefore.class, name = "textBefore"),
  @JsonSubTypes.Type(value = AnnotationDto.TextAfter.class, name = "textAfter"),
  @JsonSubTypes.Type(value = AnnotationDto.Symbols.class, name = "symbols"),
  @JsonSubTypes.Type(value = AnnotationDto.Squares.class, name = "squares"),
  @JsonSubTypes.Type(value = AnnotationDto.Arrows.class, name = "arrows"),
  @JsonSubTypes.Type(value = AnnotationDto.WhiteClock.class, name = "whiteClock"),
  @JsonSubTypes.Type(value = AnnotationDto.BlackClock.class, name = "blackClock"),
  @JsonSubTypes.Type(value = AnnotationDto.TimeSpent.class, name = "timeSpent"),
  @JsonSubTypes.Type(value = AnnotationDto.Eval.class, name = "eval"),
  @JsonSubTypes.Type(value = AnnotationDto.Evaluations.class, name = "evaluations"),
  @JsonSubTypes.Type(value = AnnotationDto.CriticalPosition.class, name = "critical"),
  @JsonSubTypes.Type(value = AnnotationDto.Medals.class, name = "medals"),
  @JsonSubTypes.Type(value = AnnotationDto.PawnStructure.class, name = "pawnStructure"),
  @JsonSubTypes.Type(value = AnnotationDto.PiecePath.class, name = "piecePath"),
  @JsonSubTypes.Type(value = AnnotationDto.VariationColor.class, name = "variationColor"),
  @JsonSubTypes.Type(value = AnnotationDto.VideoStreamTime.class, name = "videoStreamTime"),
  @JsonSubTypes.Type(value = AnnotationDto.WebLink.class, name = "webLink"),
  @JsonSubTypes.Type(value = AnnotationDto.Quotation.class, name = "quote"),
  @JsonSubTypes.Type(value = AnnotationDto.Training.class, name = "training"),
  @JsonSubTypes.Type(value = AnnotationDto.CorrespondenceMove.class, name = "correspondence"),
  @JsonSubTypes.Type(value = AnnotationDto.Raw.class, name = "raw"),
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface AnnotationDto {

  /** The index of the game as a whole, before the first move. */
  int GAME = -1;

  /** The index of the move the annotation belongs to, or {@link #GAME}. */
  int move();

  /**
   * Text shown before the move.
   *
   * @param language the IOC code of the language, like {@code "ENG"}, or null if it's not given
   * @param unknown a value of unknown meaning kept by ChessBase, or null if there is none
   */
  record TextBefore(
      int move, @NotNull String text, @Nullable String language, @Nullable Integer unknown)
      implements AnnotationDto {
    public TextBefore(int move, @NotNull String text) {
      this(move, text, null, null);
    }
  }

  /**
   * Text shown after the move.
   *
   * @param language the IOC code of the language, like {@code "ENG"}, or null if it's not given
   * @param unknown a value of unknown meaning kept by ChessBase, or null if there is none
   */
  record TextAfter(
      int move, @NotNull String text, @Nullable String language, @Nullable Integer unknown)
      implements AnnotationDto {
    public TextAfter(int move, @NotNull String text) {
      this(move, text, null, null);
    }
  }

  /**
   * Symbols of the move, like {@code !?} and {@code +-}.
   *
   * @param nags the symbols as the numbers of their PGN NAGs, like 5 for {@code !?}
   */
  record Symbols(int move, @NotNull List<Integer> nags) implements AnnotationDto {
    public Symbols {
      nags = List.copyOf(nags);
    }
  }

  /**
   * A colored square on the board.
   *
   * @param color the name of the color, like {@code "red"}
   * @param square the square, like {@code "e4"}
   */
  record ColoredSquare(@NotNull String color, @NotNull String square) {}

  /** Squares of the board marked in color. */
  record Squares(int move, @NotNull List<ColoredSquare> squares) implements AnnotationDto {
    public Squares {
      squares = List.copyOf(squares);
    }
  }

  /**
   * A colored arrow on the board.
   *
   * @param color the name of the color, like {@code "red"}
   * @param from the square the arrow starts at, like {@code "e2"}
   * @param to the square the arrow points to
   */
  record ColoredArrow(@NotNull String color, @NotNull String from, @NotNull String to) {}

  /** Arrows drawn on the board. */
  record Arrows(int move, @NotNull List<ColoredArrow> arrows) implements AnnotationDto {
    public Arrows {
      arrows = List.copyOf(arrows);
    }
  }

  /** The time left on White's clock after the move, in hundredths of a second. */
  record WhiteClock(int move, int centiseconds) implements AnnotationDto {}

  /** The time left on Black's clock after the move, in hundredths of a second. */
  record BlackClock(int move, int centiseconds) implements AnnotationDto {}

  /**
   * The time spent on the move.
   *
   * @param unknown a value of unknown meaning kept by ChessBase, or null if there is none
   */
  record TimeSpent(int move, int hours, int minutes, int seconds, @Nullable Integer unknown)
      implements AnnotationDto {}

  /**
   * A computer evaluation of the position after the move.
   *
   * @param eval the evaluation in hundredths of a pawn, or the number of moves to mate
   * @param evalType 0 for an evaluation in pawns, 1 for moves to mate; ChessBase has a 3 too, of
   *     unknown meaning
   * @param depth the search depth in plies
   */
  record Eval(int move, int eval, int evalType, int depth) implements AnnotationDto {}

  /**
   * Computer evaluations of the positions of the main line, on the game as a whole: the first is of
   * the position before the first move, then one after each move. There may be fewer than there are
   * moves.
   */
  record Evaluations(int move, @NotNull List<Evaluation> evaluations) implements AnnotationDto {
    public Evaluations {
      evaluations = List.copyOf(evaluations);
    }
  }

  /**
   * The evaluation of a position, of {@link Evaluations}.
   *
   * @param eval the evaluation in hundredths of a pawn from White's side, or the moves to mate
   * @param depth the search depth in plies
   * @param evalType 0 for an evaluation in pawns, 1 for moves to mate, 255 for none; ChessBase has 2
   *     and 32 too, of unknown meaning
   */
  record Evaluation(int eval, int depth, int evalType) {}

  /**
   * The position after the move is a critical one.
   *
   * @param phase {@code "opening"}, {@code "middlegame"}, {@code "endgame"} or {@code "none"}
   */
  record CriticalPosition(int move, @NotNull String phase) implements AnnotationDto {}

  /**
   * Medals, the reasons the game or the move is of interest.
   *
   * @param medals the medals by name, as in {@link GameDto#medals()}
   */
  record Medals(int move, @NotNull List<String> medals) implements AnnotationDto {
    public Medals {
      medals = List.copyOf(medals);
    }
  }

  /** The pawn structure is shown, in a way given by a value of unknown meaning. */
  record PawnStructure(int move, int pawnStructureType) implements AnnotationDto {}

  /**
   * The paths of a piece are shown.
   *
   * @param pathType a value of unknown meaning
   * @param square the square of the piece, like {@code "e4"}
   */
  record PiecePath(int move, int pathType, @NotNull String square) implements AnnotationDto {}

  /**
   * The color the variation starting at the move is shown in.
   *
   * @param color the color as {@code "#rrggbb"}
   * @param onlyMoves whether only the moves are colored, and not the annotations
   * @param onlyMainline whether only the main line of the variation is colored, not its sublines
   */
  record VariationColor(int move, @NotNull String color, boolean onlyMoves, boolean onlyMainline)
      implements AnnotationDto {}

  /** A time in a video stream the move was played at. */
  record VideoStreamTime(int move, int time) implements AnnotationDto {}

  /** A link to a web page. */
  record WebLink(int move, @NotNull String url, @NotNull String text) implements AnnotationDto {}

  /**
   * A game quoted in a comment.
   *
   * @param header the header fields of the quoted game by name, as {@code se.yarin.chess
   *     .GameHeaderModel} has them
   * @param moves the main line of the quoted game as movetext, or null if only the header is
   *     quoted
   * @param fen the start position of the quoted game, or null for the standard one
   * @param unknown a value of unknown meaning kept by ChessBase, or null if there is none
   */
  record Quotation(
      int move,
      @NotNull Map<String, String> header,
      @Nullable String moves,
      @Nullable String fen,
      @Nullable Integer unknown)
      implements AnnotationDto {
    public Quotation {
      header = Collections.unmodifiableMap(new LinkedHashMap<>(header));
    }
  }

  /** A training question, as ChessBase stores it. */
  record Training(int move, byte @NotNull [] data) implements AnnotationDto {}

  /** A correspondence move, as ChessBase stores it. */
  record CorrespondenceMove(int move, byte @NotNull [] data) implements AnnotationDto {}

  /**
   * An annotation of a kind that isn't known, kept as it was stored so it can be written back.
   *
   * @param annotationType the number of the kind in the format it was read from
   * @param invalid whether the annotation is of a known kind, but couldn't be read
   */
  record Raw(int move, int annotationType, byte @NotNull [] data, boolean invalid)
      implements AnnotationDto {}
}
