package se.yarin.morphy.chessbase.convert;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.GameMovesModel;
import se.yarin.morphy.chessbase.annotations.AnnotationConverter;
import se.yarin.morphy.model.GameMovesDto;
import se.yarin.morphy.pgn.GameMovesDtoCodec;

/**
 * The moves of a game with ChessBase annotations as {@link GameMovesDto} carries them, written by
 * the converters of the ChessBase formats and read by {@link GameDtoImporter}. What one writes, the
 * other reads back to the same moves.
 */
public final class GameMovesDtos {

  /** The moves of a ChessBase game, with every annotation passed on as it is. */
  public static final @NotNull GameMovesDtoCodec CODEC =
      new GameMovesDtoCodec(ChessBaseAnnotationDtoMapper.INSTANCE);

  /**
   * The moves of a game in a PGN file, with ChessBase annotations kept in its comments, like
   * {@code [%csl Gd4]}, taken out of them and put back.
   */
  public static final @NotNull GameMovesDtoCodec PGN =
      new GameMovesDtoCodec(
          ChessBaseAnnotationDtoMapper.INSTANCE,
          AnnotationConverter.getRoundTripConverter()::convertToChessBase,
          AnnotationConverter.getRoundTripConverter()::convertToPgn);

  private GameMovesDtos() {}

  /** The moves as movetext, the FEN of the start position, and the annotations. */
  public static @NotNull GameMovesDto toDto(@NotNull GameMovesModel moves) {
    return CODEC.toDto(moves);
  }

  /**
   * Reads the moves and their annotations.
   *
   * @param chess960 whether the game is a Chess960 game
   * @throws IllegalArgumentException if the moves or an annotation can't be read
   */
  public static @NotNull GameMovesModel fromDto(@NotNull GameMovesDto dto, boolean chess960) {
    return CODEC.fromDto(dto, chess960);
  }

  /** Makes PGN databases keep ChessBase annotations in their comments. */
  public static final class PgnProvider implements GameMovesDtoCodec.Provider {
    @Override
    public @NotNull GameMovesDtoCodec pgnCodec() {
      return PGN;
    }
  }
}
