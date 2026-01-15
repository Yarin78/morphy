package se.yarin.chess;

import org.jetbrains.annotations.NotNull;

/** Represents a complete chess game with header, moves and annotations. */
public class GameModel {
  // These two fields should be final so we don't drop listeners when trying to replace them
  private final GameHeaderModel header;
  private final GameMovesModel moves;

  public GameModel() {
    this(new GameHeaderModel(), new GameMovesModel());
  }

  public GameModel(@NotNull GameHeaderModel header, @NotNull GameMovesModel moves) {
    this.header = header;
    this.moves = moves;
  }

  /**
   * @return the underlying {@link GameMovesModel}
   */
  public GameMovesModel moves() {
    return this.moves;
  }

  /**
   * @return the underlying {@link GameHeaderModel}
   */
  public GameHeaderModel header() {
    return this.header;
  }

  /**
   * Replaces the data inside the underlying models by copying the data from a source model
   *
   * @param model the model to copy data from
   */
  public void replaceAll(@NotNull GameModel model) {
    header.replaceAll(model.header());
    moves.replaceAll(model.moves());
  }

  /** Resets the game model by clearing all the data. */
  public void reset() {
    header.clear();
    moves.reset();
  }
}
