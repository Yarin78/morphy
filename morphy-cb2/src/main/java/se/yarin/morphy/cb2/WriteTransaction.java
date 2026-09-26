package se.yarin.morphy.cb2;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.GameModel;
import se.yarin.morphy.chessbase.DatabaseLocks;
import se.yarin.morphy.chessbase.text.TextModel;

/** A transaction that changes the database. */
public final class WriteTransaction extends DatabaseTransaction {

  public WriteTransaction(@NotNull Database2Cbh database) {
    super(database, DatabaseLocks.Lock.UPDATE);
  }

  public int addGame(@NotNull GameModel game) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void replaceGame(int id, @NotNull GameModel game) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public int addText(@NotNull TextModel text) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void replaceText(int id, @NotNull TextModel text) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void commit() {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
