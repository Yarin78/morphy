package se.yarin.morphy.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The moves of a game.
 *
 * @param pgn the moves as PGN movetext, without headers, comments or NAGs
 * @param fen the start position as FEN, or null for the standard start position. The castling
 *     field is KQkq as in regular chess, also in Chess960 (see {@link GameDto#variant()}), where the
 *     start squares of the castling kings and rooks follow from the position.
 * @param annotations the annotations of the moves, each with the index of its move in the
 *     movetext (see {@link AnnotationDto}), in order of the moves
 */
public record GameMovesDto(
    @Nullable String pgn, @Nullable String fen, @NotNull List<AnnotationDto> annotations) {

  public GameMovesDto {
    annotations = List.copyOf(annotations);
  }

  /** Read from JSON, where moves without annotations may leave them out. */
  @JsonCreator
  public static GameMovesDto fromJson(
      @JsonProperty("pgn") @Nullable String pgn,
      @JsonProperty("fen") @Nullable String fen,
      @JsonProperty("annotations") @Nullable List<AnnotationDto> annotations) {
    return new GameMovesDto(pgn, fen, annotations == null ? List.of() : annotations);
  }

  /** Moves without annotations. */
  public GameMovesDto(@Nullable String pgn, @Nullable String fen) {
    this(pgn, fen, List.of());
  }

  /** Moves from the standard start position, without annotations. */
  public GameMovesDto(@Nullable String pgn) {
    this(pgn, null, List.of());
  }
}
