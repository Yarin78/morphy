package se.yarin.morphy.cb2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.GameResult;
import se.yarin.morphy.api.GameFacts;
import se.yarin.morphy.api.GameScan;
import se.yarin.morphy.api.ScannedGame;
import se.yarin.morphy.cb2.games.Dates;
import se.yarin.morphy.cb2.games.GameHeader;
import se.yarin.morphy.cb2.games.GameRecord;
import se.yarin.morphy.cb2.moves.MoveStreamCodec;
import se.yarin.morphy.cb2.storage.RecordFile;

/**
 * A scan of a v2 database's games: the headers and moves read straight from their files, which
 * can be read from several threads, under a read transaction that keeps writers out.
 */
final class Scan implements GameScan {
  private final @NotNull Database2Cbh database;
  private final @NotNull ReadTransaction transaction;

  Scan(@NotNull Database2Cbh database) {
    this.database = database;
    this.transaction = new ReadTransaction(database);
  }

  @Override
  public int maxId() {
    return transaction.count();
  }

  @Override
  public @Nullable ScannedGame read(int id) {
    GameRecord record = database.gameHeaderFile().get(id);
    // Guiding texts and analyses have records of their own kinds
    if (!(record instanceof GameHeader header) || header.deleted() || header.chess960()) {
      return null;
    }
    GameMovesModel moves;
    try {
      RecordFile.Record data = database.moveFile().read(header.movesOffset());
      if (data.tag() != RecordFile.TAG_GAME) {
        return null;
      }
      moves = MoveStreamCodec.decode(data.tag(), data.content());
    } catch (RuntimeException e) {
      // Not only InvalidDataException: a move that can't be played fails in the chess core
      return null;
    }
    GameResult[] results = GameResult.values();
    GameResult result =
        header.result() >= 0 && header.result() < results.length
            ? results[header.result()]
            : GameResult.NOT_FINISHED;
    GameFacts facts =
        new GameFacts(
            result,
            Dates.decode(header.playedDate()),
            Math.max(0, header.whiteElo()),
            Math.max(0, header.blackElo()),
            header.whiteId(),
            header.blackId());
    return new ScannedGame(id, moves, facts);
  }

  @Override
  public void close() {
    transaction.close();
  }
}
