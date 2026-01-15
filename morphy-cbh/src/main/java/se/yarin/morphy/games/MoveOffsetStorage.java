package se.yarin.morphy.games;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.DatabaseContext;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.exceptions.MorphyIOException;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.exceptions.MorphyNotSupportedException;
import se.yarin.morphy.storage.MorphyOpenOption;
import se.yarin.morphy.util.CBUtil;
import se.yarin.util.BlobChannel;
import se.yarin.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.OpenOption;
import java.util.*;

import static java.nio.file.StandardOpenOption.*;
import static se.yarin.morphy.storage.MorphyOpenOption.IGNORE_NON_CRITICAL_ERRORS;

public class MoveOffsetStorage {
  private static final Logger log = LoggerFactory.getLogger(MoveOffsetStorage.class);

  private static final int MAX_GAMES = 100_000_000;
  private static final int PRELOAD_LIMIT = 10_000;
  private static final int CHUNK_SIZE = 131072; // Number of games

  private final @NotNull DatabaseContext context;
  private final @NotNull BlobChannel channel;
  private int numGames;
  private final boolean strict;
  private int[] moveOffsets; // null if not initialized; if not null, length is always numGames
  private final boolean inMemory;

  private MoveOffsetStorage(
      @NotNull File file,
      @NotNull Set<OpenOption> options,
      @Nullable DatabaseContext context,
      boolean inMemory)
      throws IOException {
    if (!CBUtil.extension(file).equals(".cbgi")) {
      throw new IllegalArgumentException(
          "The file extension of a MoveOffsetStorage index must be .cbgi");
    }

    this.context = context == null ? new DatabaseContext() : context;
    this.channel = BlobChannel.open(file.toPath(), this.context, MorphyOpenOption.valid(options));
    this.inMemory = inMemory;
    this.strict = options.contains(WRITE) || !options.contains(IGNORE_NON_CRITICAL_ERRORS);

    if (channel.size() == 0) {
      if (options.contains(CREATE) || options.contains(CREATE_NEW)) {
        this.numGames = 0;
        putHeader();
      } else {
        throw new IllegalStateException("File was empty");
      }
    } else {
      ByteBuffer buf = this.channel.read(0L, 4);
      this.numGames = ByteBufferUtil.getIntL(buf);

      if (strict) {
        if (numGames > MAX_GAMES) {
          throw new MorphyNotSupportedException("Too many games in MoveOffsetStorage");
        }
      }
    }

    if (this.numGames < PRELOAD_LIMIT) {
      init();
    }
  }

  private void putHeader() {
    ByteBuffer buf = ByteBuffer.allocate(4);
    ByteBufferUtil.putIntL(buf, numGames);
    buf.rewind();
    try {
      channel.write(0, buf);
    } catch (IOException e) {
      throw new MorphyIOException("Error writing header to MoveOffsetStorage", e);
    }
  }

  private synchronized void init() {
    if (moveOffsets == null) { // Check within synchronized block
      log.info("Loading MoveOffsetStorage");

      try {
        ByteBuffer buf = ByteBuffer.allocate((int) this.channel.size());
        this.channel.read(0, buf);
        buf.rewind();
        this.moveOffsets = new int[buf.limit() / 4];
        int i = 0;
        while (buf.position() < buf.limit()) {
          this.moveOffsets[i++] = ByteBufferUtil.getIntL(buf);
        }
      } catch (IOException e) {
        throw new MorphyIOException("Error loading data in MoveOffsetStorage", e);
      }
    }
  }

  public static MoveOffsetStorage create(@NotNull File file, @Nullable DatabaseContext context)
      throws IOException, MorphyInvalidDataException {
    return new MoveOffsetStorage(file, Set.of(READ, WRITE, CREATE_NEW), context, false);
  }

  public static MoveOffsetStorage open(@NotNull File file, @Nullable DatabaseContext context)
      throws IOException {
    return open(file, DatabaseMode.READ_WRITE, context);
  }

  public static MoveOffsetStorage open(
      @NotNull File file, @NotNull DatabaseMode mode, @Nullable DatabaseContext context)
      throws IOException {
    return new MoveOffsetStorage(file, mode.openOptions(), context, mode == DatabaseMode.IN_MEMORY);
  }

  public @NotNull DatabaseContext context() {
    return context;
  }

  public int count() {
    return numGames;
  }

  /**
   * Gets the offset in the Move Repository where the moves for a game starts
   *
   * @param gameId the id of the game
   * @return an offset, or 0 if the offset is missing (typically a text entry, not a game)
   */
  public int getOffset(int gameId) {
    if (moveOffsets == null) {
      init();
    }

    if (gameId < 1 || gameId > numGames) {
      if (strict) {
        throw new IllegalArgumentException(
            "gameId must be between 1 and number of games in the storage");
      }
      return 0;
    }
    return moveOffsets[gameId];
  }

  public void putOffset(int gameId, int offset) {
    putOffsets(Map.of(gameId, offset));
  }

  public void putOffsets(@NotNull Map<Integer, Integer> newOffsets) {
    if (newOffsets.size() == 0) {
      return;
    }

    if (moveOffsets == null) {
      init();
    }
    numGames = Math.max(numGames, Collections.max(newOffsets.keySet()));

    if (numGames >= moveOffsets.length) {
      int newSize = (numGames / CHUNK_SIZE + 1) * CHUNK_SIZE;
      this.moveOffsets = Arrays.copyOf(moveOffsets, newSize);
    }

    Set<Integer> dirtyChunks = new TreeSet<>();

    // Update everything in memory and flush dirty chunks
    for (Map.Entry<Integer, Integer> entry : newOffsets.entrySet()) {
      int gameId = entry.getKey();
      int offset = entry.getValue();
      moveOffsets[gameId] = offset;
      dirtyChunks.add(gameId / CHUNK_SIZE);
    }
    moveOffsets[0] = numGames; // Ensures header is updated when writing
    dirtyChunks.add(0);

    for (int chunkId : dirtyChunks) {
      flushChunk(chunkId);
    }
  }

  private void flushChunk(int chunkId) {
    if (inMemory) return;

    int startGameId = chunkId * CHUNK_SIZE;

    ByteBuffer buf = ByteBuffer.allocate(CHUNK_SIZE * 4);
    for (int i = 0; i < CHUNK_SIZE && startGameId + i < moveOffsets.length; i++) {
      ByteBufferUtil.putIntL(buf, moveOffsets[startGameId + i]);
    }
    buf.rewind();

    try {
      channel.write(startGameId * 4L, buf);
    } catch (IOException e) {
      throw new MorphyIOException("Error writing to MoveOffsetStorage", e);
    }
  }

  public void close() throws IOException {
    channel.close();
  }
}
