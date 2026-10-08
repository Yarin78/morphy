package se.yarin.morphy;

import java.nio.ByteBuffer;
import java.util.BitSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.GameMovesModel;
import se.yarin.morphy.api.GameFacts;
import se.yarin.morphy.api.GameScan;
import se.yarin.morphy.api.ScannedGame;
import se.yarin.morphy.exceptions.MorphyMoveDecodingException;
import se.yarin.morphy.games.GameHeader;

/**
 * A scan of a v1 database's games under a read transaction. The files' channels read by position,
 * so games can be read and decoded from several threads at once (the storages' metrics may then
 * miss a count now and then).
 */
final class CbhScan implements GameScan {
  private final @NotNull DatabaseCbh database;
  private final @NotNull DatabaseReadTransaction transaction;

  // The games scanned; null for every one
  private final @Nullable BitSet games;

  /** @param games the games to scan, or null for every one */
  CbhScan(@NotNull DatabaseCbh database, @Nullable BitSet games) {
    this.database = database;
    this.games = games;
    this.transaction = new DatabaseReadTransaction(database);
  }

  @Override
  public int maxId() {
    return database.count();
  }

  @Override
  public @Nullable ScannedGame read(int id) {
    if (games != null && !games.get(id)) {
      return null;
    }
    GameHeader header = database.gameHeaderIndex().getGameHeader(id);
    if (header.deleted() || header.guidingText() || header.chess960StartPosition() >= 0) {
      return null;
    }
    ByteBuffer blob = database.moveRepository().getMovesBlob(header.movesOffset());
    GameMovesModel moves;
    try {
      moves = database.moveRepository().moveSerializer().deserializeMoves(blob, true, id);
    } catch (MorphyMoveDecodingException e) {
      // The moves up to the one that couldn't be decoded, as a game is shown
      moves = e.getModel();
      if (moves == null) {
        return null;
      }
    } catch (RuntimeException e) {
      return null;
    }
    GameFacts facts =
        new GameFacts(
            header.result(),
            header.playedDate(),
            header.whiteElo(),
            header.blackElo(),
            header.whitePlayerId(),
            header.blackPlayerId());
    return new ScannedGame(id, moves, facts);
  }

  @Override
  public void close() {
    transaction.close();
  }
}
