package se.yarin.morphy.chessbase.convert;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.annotations.Annotations;
import se.yarin.morphy.chessbase.annotations.ImmutableTimeControlAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableTimeSerie;
import se.yarin.morphy.chessbase.annotations.TimeControlAnnotation;
import se.yarin.morphy.model.TimeControlDto;

/**
 * A game's time control, which ChessBase keeps as a {@link TimeControlAnnotation} on the game as a
 * whole, before the first move, and {@link se.yarin.morphy.model.GameDto} has as a field of its own
 * rather than in the movetext. Times are whole seconds, as in the annotation's PGN.
 */
public final class GameTimeControl {

  /** The number of moves of a series that lasts the rest of the game. */
  private static final int REST_OF_GAME = 1000;

  private GameTimeControl() {}

  /** The time control of a game's moves; null if they have none. */
  public static @Nullable TimeControlDto of(@NotNull GameMovesModel moves) {
    TimeControlAnnotation annotation =
        moves.root().getAnnotations().getByClass(TimeControlAnnotation.class);
    if (annotation == null || annotation.timeSeries().isEmpty()) {
      return null;
    }
    List<TimeControlDto.Period> periods = new ArrayList<>();
    for (TimeControlAnnotation.TimeSerie serie : annotation.timeSeries()) {
      periods.add(
          new TimeControlDto.Period(
              serie.start() / 100,
              serie.increment() / 100,
              serie.moves() > 0 && serie.moves() < REST_OF_GAME ? serie.moves() : null));
    }
    return new TimeControlDto(periods);
  }

  /** The moves without their time control, as a copy if they have one. */
  public static @NotNull GameMovesModel without(@NotNull GameMovesModel moves) {
    if (moves.root().getAnnotations().getByClass(TimeControlAnnotation.class) == null) {
      return moves;
    }
    GameMovesModel copy = new GameMovesModel(moves);
    copy.root().getAnnotations().removeByClass(ImmutableTimeControlAnnotation.class);
    return copy;
  }

  /** Gives the moves a time control, replacing the one they have. */
  public static void set(@NotNull GameMovesModel moves, @NotNull TimeControlDto timeControl) {
    Annotations annotations = moves.root().getAnnotations();
    annotations.removeByClass(ImmutableTimeControlAnnotation.class);
    if (timeControl.periods().isEmpty()) {
      return;
    }
    List<TimeControlAnnotation.TimeSerie> series = new ArrayList<>();
    for (TimeControlDto.Period period : timeControl.periods()) {
      int seriesMoves = period.moves() == null ? REST_OF_GAME : period.moves();
      // A stage of a set number of moves, otherwise the rest of the game, which has an increment
      // or hasn't
      int type = period.moves() != null ? 1 : period.increment() > 0 ? 3 : 0;
      series.add(
          ImmutableTimeSerie.of(
              period.seconds() * 100, period.increment() * 100, seriesMoves, type));
    }
    annotations.add(ImmutableTimeControlAnnotation.of(series));
  }
}
