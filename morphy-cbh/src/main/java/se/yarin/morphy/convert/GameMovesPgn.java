package se.yarin.morphy.convert;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.pgn.NagStyle;
import se.yarin.chess.pgn.PgnExporter;
import se.yarin.chess.pgn.PgnFormatException;
import se.yarin.chess.pgn.PgnFormatOptions;
import se.yarin.chess.pgn.PgnParser;
import se.yarin.morphy.games.annotations.AnnotationConverter;

/**
 * The movetext PGN that {@link se.yarin.morphy.model.GameMovesDto} carries, written by {@link
 * GameDtoConverter} and read by {@link GameDtoImporter}. What one writes, the other reads back to
 * the same moves.
 *
 * <p>TODO: the movetext has no SetUp/FEN, so a game from a set-up position (or Chess960) loses its
 * start position through the DTO. Either add a fen field to GameMovesDto or make moves.pgn a full
 * PGN with the SetUp/FEN tags.
 */
final class GameMovesPgn {

  private GameMovesPgn() {}

  /** The moves as single-line movetext, without headers or result. */
  static @NotNull String toPgn(@NotNull GameMovesModel moves) {
    PgnExporter exporter =
        new PgnExporter(
            PgnFormatOptions.DEFAULT, AnnotationConverter.getRoundTripConverter()::convertToPgn);
    return exporter.exportMovesOnly(moves, NagStyle.SUFFIXES);
  }

  /**
   * Reads movetext, from the standard start position.
   *
   * @throws IllegalArgumentException if the movetext can't be read
   */
  static @NotNull GameMovesModel fromPgn(@NotNull String pgn) {
    PgnParser parser = new PgnParser(AnnotationConverter.getRoundTripConverter()::convertToChessBase);
    try {
      return parser.parseMoves(pgn);
    } catch (PgnFormatException e) {
      throw new IllegalArgumentException("Invalid moves: " + e.getMessage(), e);
    }
  }
}
