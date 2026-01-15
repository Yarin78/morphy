package se.yarin.chess;

public interface NavigableGameModelChangeListener
    extends GameHeaderModelChangeListener, GameMovesModelChangeListener {
  /**
   * A notification that the cursor has changed.
   *
   * @param oldCursor the old cursor
   * @param newCursor the new cursor
   */
  void cursorChanged(GameMovesModel.Node oldCursor, GameMovesModel.Node newCursor);
}
