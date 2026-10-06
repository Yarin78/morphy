package se.yarin.morphy.cb2;

import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.GameResult;
import se.yarin.morphy.api.GameFacts;
import se.yarin.morphy.api.GameScan;
import se.yarin.morphy.api.ParallelBatches;
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
  // The games read at a time by {@link #forEach}: their headers in one read, and their moves,
  // which follow each other in the moves file, in one or a few
  private static final int BATCH = 4096;

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
    GameHeader header = indexable(database.gameHeaderFile().get(id));
    if (header == null) {
      return null;
    }
    RecordFile.Record moves;
    try {
      moves = database.moveFile().read(header.movesOffset());
    } catch (RuntimeException e) {
      return null;
    }
    return scanned(header, moves);
  }

  @Override
  public void forEach(@NotNull Consumer<ScannedGame> consumer) {
    ParallelBatches.run(
        maxId(),
        BATCH,
        (first, end) -> {
          GameRecord[] records = database.gameHeaderFile().read(first, end - first);
          GameHeader[] headers = new GameHeader[records.length];
          int n = 0;
          for (GameRecord record : records) {
            GameHeader header = indexable(record);
            if (header != null) {
              headers[n++] = header;
            }
          }
          long[] offsets = new long[n];
          for (int i = 0; i < n; i++) {
            offsets[i] = headers[i].movesOffset();
          }
          RecordFile.Record[] moves;
          try {
            moves = database.moveFile().readMany(offsets);
          } catch (RuntimeException e) {
            // A record that can't be read: the batch game by game, leaving that one out
            for (int i = 0; i < n; i++) {
              ScannedGame game = read(headers[i].id());
              if (game != null) {
                consumer.accept(game);
              }
            }
            return;
          }
          for (int i = 0; i < n; i++) {
            ScannedGame game = scanned(headers[i], moves[i]);
            if (game != null) {
              consumer.accept(game);
            }
          }
        });
  }

  /** The record as a game to index, or null if it isn't one. */
  private static @Nullable GameHeader indexable(GameRecord record) {
    // Guiding texts and analyses have records of their own kinds
    return record instanceof GameHeader header && !header.deleted() && !header.chess960()
        ? header
        : null;
  }

  /** The game of a header and its moves, or null if the moves aren't a game's or can't be decoded. */
  private static @Nullable ScannedGame scanned(GameHeader header, RecordFile.Record data) {
    if (data.tag() != RecordFile.TAG_GAME) {
      return null;
    }
    GameMovesModel moves;
    try {
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
    return new ScannedGame(header.id(), moves, facts);
  }

  @Override
  public void close() {
    transaction.close();
  }
}
