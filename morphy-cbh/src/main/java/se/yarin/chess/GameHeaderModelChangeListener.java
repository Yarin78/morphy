package se.yarin.chess;

public interface GameHeaderModelChangeListener {
  /**
   * A notification that a header model has changed
   *
   * @param headerModel the header model that has changed
   */
  void headerModelChanged(GameHeaderModel headerModel);
}
