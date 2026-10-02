package se.yarin.morphy.pgn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Player;
import se.yarin.chess.annotations.AnnotationTransformer;
import se.yarin.chess.annotations.Annotations;
import se.yarin.morphy.model.AnnotationDto;
import se.yarin.morphy.model.GameMovesDto;

/**
 * Turns the moves of a game into a {@link GameMovesDto} and back: the moves as plain PGN movetext,
 * and their annotations as {@link AnnotationDto}s, each with the index of its move in the
 * movetext. What {@link #toDto} writes, {@link #fromDto} reads back to the same moves.
 *
 * <p>Moves read from a PGN file have their annotations as comments and NAGs, which may hold
 * annotations PGN itself doesn't have, like {@code [%csl Gd4]}. A codec for such moves has
 * transformers that take those out of the comments, before the annotations become DTOs, and put
 * them back, after the DTOs become annotations.
 */
public final class GameMovesDtoCodec {

  /** Moves with the annotations plain PGN has: comments and NAGs. */
  public static final @NotNull GameMovesDtoCodec PLAIN =
      new GameMovesDtoCodec(AnnotationDtoMapper.PLAIN);

  // Writes the movetext without any annotations, which are passed on next to it
  private static final PgnMoves MOVES_ONLY =
      new PgnMoves((annotations, lastMoveBy) -> annotations.clear(), null);

  private final @NotNull AnnotationDtoMapper mapper;
  private final @Nullable AnnotationTransformer fromPgnTransformer;
  private final @Nullable AnnotationTransformer toPgnTransformer;

  /** @param mapper turns the annotations of a move into DTOs and back */
  public GameMovesDtoCodec(@NotNull AnnotationDtoMapper mapper) {
    this(mapper, null, null);
  }

  /**
   * @param mapper turns the annotations of a move into DTOs and back
   * @param fromPgnTransformer applied to the annotations of a move before they become DTOs, or null
   * @param toPgnTransformer applied to the annotations of a move made from DTOs, or null
   */
  public GameMovesDtoCodec(
      @NotNull AnnotationDtoMapper mapper,
      @Nullable AnnotationTransformer fromPgnTransformer,
      @Nullable AnnotationTransformer toPgnTransformer) {
    this.mapper = mapper;
    this.fromPgnTransformer = fromPgnTransformer;
    this.toPgnTransformer = toPgnTransformer;
  }

  /**
   * The codec for moves read from PGN files: the one a {@link Provider} gives, if there is one,
   * and otherwise {@link #PLAIN}.
   */
  public static @NotNull GameMovesDtoCodec forPgn() {
    return ServiceLoader.load(Provider.class)
        .findFirst()
        .map(Provider::pgnCodec)
        .orElse(PLAIN);
  }

  /**
   * Gives the codec for moves read from PGN files, for a format that keeps more annotations in PGN
   * comments than the plain ones. Found with {@link ServiceLoader}.
   */
  public interface Provider {
    @NotNull
    GameMovesDtoCodec pgnCodec();
  }

  /** The moves as movetext, the FEN of the start position, and the annotations. */
  public @NotNull GameMovesDto toDto(@NotNull GameMovesModel moves) {
    List<AnnotationDto> annotations = new ArrayList<>(toDtos(AnnotationDto.GAME, moves.root()));
    List<GameMovesModel.Node> nodes = moves.getAllNodesPgnOrder();
    for (int i = 0; i < nodes.size(); i++) {
      annotations.addAll(toDtos(i, nodes.get(i)));
    }
    return new GameMovesDto(MOVES_ONLY.toPgn(moves), MOVES_ONLY.toFen(moves), annotations);
  }

  /**
   * Reads the moves and their annotations.
   *
   * @param chess960 whether the game is a Chess960 game, which decides how castling in the start
   *     position is read
   * @throws IllegalArgumentException if the movetext or the FEN can't be read, or an annotation
   *     belongs to a move that isn't there or can't be read
   */
  public @NotNull GameMovesModel fromDto(@NotNull GameMovesDto dto, boolean chess960) {
    GameMovesModel moves =
        PgnMoves.PLAIN.fromPgn(dto.pgn() == null ? "" : dto.pgn(), dto.fen(), chess960);
    if (dto.annotations().isEmpty()) {
      return moves;
    }

    Map<Integer, List<AnnotationDto>> byMove = new TreeMap<>();
    for (AnnotationDto annotation : dto.annotations()) {
      byMove.computeIfAbsent(annotation.move(), m -> new ArrayList<>()).add(annotation);
    }
    List<GameMovesModel.Node> nodes = moves.getAllNodesPgnOrder();
    for (Map.Entry<Integer, List<AnnotationDto>> entry : byMove.entrySet()) {
      int move = entry.getKey();
      if (move < AnnotationDto.GAME || move >= nodes.size()) {
        throw new IllegalArgumentException(
            "Annotation of move " + move + ", but there are only " + nodes.size() + " moves");
      }
      GameMovesModel.Node node = move == AnnotationDto.GAME ? moves.root() : nodes.get(move);
      Annotations annotations = mapper.fromDtos(entry.getValue());
      if (toPgnTransformer != null) {
        toPgnTransformer.transform(annotations, lastMoveBy(node));
      }
      node.getAnnotations().addAll(annotations);
    }
    return moves;
  }

  private @NotNull List<AnnotationDto> toDtos(int move, @NotNull GameMovesModel.Node node) {
    Annotations annotations = node.getAnnotations();
    if (fromPgnTransformer != null && !annotations.isEmpty()) {
      annotations = new Annotations(annotations);
      fromPgnTransformer.transform(annotations, lastMoveBy(node));
    }
    return mapper.toDtos(move, annotations);
  }

  private static @Nullable Player lastMoveBy(@NotNull GameMovesModel.Node node) {
    return node.isRoot() ? null : node.parent().position().playerToMove();
  }
}
