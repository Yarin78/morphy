package se.yarin.morphy.games;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.DatabaseContext;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.exceptions.MorphyIOException;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
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

public class TopGamesStorage {
  private static final Logger log = LoggerFactory.getLogger(TopGamesStorage.class);

  // The physical storage measures capacity as "number of 32 bit ints"; each such int stores 16
  // games.
  // Capacity is also always a multiple of 16, so in practice 256, 512, 768... games are stored

  // But this chunk size is a bit too small for it to efficient in memory when expanding, so we
  // use another chunk size for the in-memory buffer.

  // ChessBase 12 (Mega Database 2014) wrote 3 bits per game instead of 2. The first two bits have
  // the same meaning in both; the third has always been found to be 0.
  private static final int STEP = 16;
  private static final int BITS_PER_GAME = 2;
  private static final int IN_MEMORY_CHUNK_SIZE = 65536;

  private final @NotNull DatabaseContext context;
  private final @Nullable BlobChannel channel;
  private @NotNull TopGamesStorage.FlagsProlog prolog;
  private @NotNull ByteBuffer
      flagBuffer; // the position is always 0, limit is always a multiple of IN_MEMORY_CHUNK_SIZE
  private final boolean inMemory;
  private int highestGameId;

  private TopGamesStorage(
      @NotNull File file,
      @NotNull Set<OpenOption> options,
      @Nullable DatabaseContext context,
      boolean inMemory)
      throws IOException {
    if (!CBUtil.extension(file).equals(".flags")) {
      throw new IllegalArgumentException("The file extension of a TopGamesStorage must be .flags");
    }

    this.context = context == null ? new DatabaseContext() : context;
    this.channel = BlobChannel.open(file.toPath(), this.context, MorphyOpenOption.valid(options));
    this.inMemory = inMemory;
    this.flagBuffer = ByteBuffer.allocate(IN_MEMORY_CHUNK_SIZE);
    boolean strict = options.contains(WRITE) || !options.contains(IGNORE_NON_CRITICAL_ERRORS);

    if (channel.size() == 0) {
      if (options.contains(CREATE) || options.contains(CREATE_NEW)) {
        this.prolog = FlagsProlog.empty();
        flushProlog();
      } else {
        throw new IllegalStateException("File was empty");
      }
      this.highestGameId = 0;
      extendBuffer();
    } else {
      ByteBuffer buf = this.channel.read(0L, FlagsProlog.SERIALIZED_SIZE);
      this.prolog = FlagsProlog.deserializeHeader(buf);
      this.highestGameId = (int) (this.prolog.capacity() * 32L / bitsPerGame());
      extendBuffer();

      this.channel.read(FlagsProlog.SERIALIZED_SIZE, this.flagBuffer);
      this.flagBuffer.position(0);

      if (strict) {
        if (this.prolog.id() != FlagsProlog.ID) {
          throw new MorphyInvalidDataException(
              String.format(
                  "TopGamesStorage has invalid header id (%d != %d)",
                  this.prolog.id(), FlagsProlog.ID));
        }
        if (this.prolog.gameBits() != 2 && this.prolog.gameBits() != 3) {
          throw new MorphyInvalidDataException(
              String.format(
                  "TopGamesStorage has unsupported bits per game (%d)", this.prolog.gameBits()));
        }
      }
    }
  }

  public TopGamesStorage() {
    this(null);
  }

  public TopGamesStorage(@Nullable DatabaseContext context) {
    this.context = context;
    this.flagBuffer = ByteBuffer.allocate(IN_MEMORY_CHUNK_SIZE);
    this.channel = null;
    this.prolog = FlagsProlog.empty();
    this.inMemory = true;
  }

  public static TopGamesStorage create(@NotNull File file, @Nullable DatabaseContext context)
      throws IOException, MorphyInvalidDataException {
    return new TopGamesStorage(file, Set.of(READ, WRITE, CREATE_NEW), context, false);
  }

  public static TopGamesStorage open(@NotNull File file, @Nullable DatabaseContext context)
      throws IOException {
    return open(file, DatabaseMode.READ_WRITE, context);
  }

  public static TopGamesStorage open(
      @NotNull File file, @NotNull DatabaseMode mode, @Nullable DatabaseContext context)
      throws IOException {
    return new TopGamesStorage(file, mode.openOptions(), context, mode == DatabaseMode.IN_MEMORY);
  }

  public @NotNull DatabaseContext context() {
    return context;
  }

  private void flushProlog() {
    if (channel != null) {
      ByteBuffer buf = ByteBuffer.allocate(FlagsProlog.SERIALIZED_SIZE);
      this.prolog.serializeHeader(buf);
      buf.rewind();
      try {
        channel.write(0, buf);
      } catch (IOException e) {
        throw new MorphyIOException("Error writing header to TopGamesStorage", e);
      }
    }
  }

  @Value.Immutable
  public abstract static class FlagsProlog {
    public static final int ID = 0x0F010B09;
    public static final int SERIALIZED_SIZE = 12;

    public abstract int id();

    // Number of allocated 32-bit ints in the file with flags (16 games). Always a multiple of STEP
    public abstract int capacity();

    // Numbers of bits per game
    public abstract int gameBits();

    public static TopGamesStorage.FlagsProlog ofCapacity(int capacity) {
      return ofCapacity(capacity, BITS_PER_GAME);
    }

    public static TopGamesStorage.FlagsProlog ofCapacity(int capacity, int gameBits) {
      return ImmutableFlagsProlog.builder().id(ID).capacity(capacity).gameBits(gameBits).build();
    }

    public static TopGamesStorage.FlagsProlog empty() {
      return ofCapacity(0);
    }

    public static @NotNull TopGamesStorage.FlagsProlog deserializeHeader(@NotNull ByteBuffer buf) {
      return ImmutableFlagsProlog.builder()
          .id(ByteBufferUtil.getIntB(buf))
          .capacity(ByteBufferUtil.getIntB(buf))
          .gameBits(ByteBufferUtil.getIntB(buf))
          .build();
    }

    public void serializeHeader(@NotNull ByteBuffer buf) {
      ByteBufferUtil.putIntB(buf, id());
      ByteBufferUtil.putIntB(buf, capacity());
      ByteBufferUtil.putIntB(buf, gameBits());
    }
  }

  public enum TopGameStatus {
    UNKNOWN,
    INVALID, // Not used
    IS_NOT_TOP_GAME,
    IS_TOP_GAME,
  }

  /** The number of bits used for each game: 2, or 3 in the files of some older versions. */
  private int bitsPerGame() {
    // If the value is invalid we only get here when ignoring errors; use the default
    int bits = prolog.gameBits();
    return bits == 2 || bits == 3 ? bits : BITS_PER_GAME;
  }

  private int fileCapacity(int numGames) {
    // How many 32-bit ints should be used to store numGames games in the storage?
    // Since first game has id 1 we waste the bits of id 0 in the storage
    long bitsNeeded = (long) (numGames + 1) * bitsPerGame();
    int intsNeeded = (int) ((bitsNeeded + 31) / 32);
    return ((intsNeeded + STEP - 1) / STEP) * STEP; // Ensure it's modulo STEP (rounded up)
  }

  private int numInMemoryChunks(int numGames) {
    // One byte more than needed, since a game can straddle two bytes when using 3 bits per game
    long bytesNeeded = ((long) (numGames + 1) * bitsPerGame() + 7) / 8 + 1;
    return (int) (bytesNeeded / IN_MEMORY_CHUNK_SIZE) + 1;
  }

  public int count() {
    return highestGameId;
  }

  private void extendBuffer() {
    int desiredChunks = numInMemoryChunks(highestGameId);
    if (desiredChunks * IN_MEMORY_CHUNK_SIZE > flagBuffer.limit()) {
      int newSize = desiredChunks * IN_MEMORY_CHUNK_SIZE;
      ByteBuffer newFlagsBuffer = ByteBuffer.allocate(newSize);
      newFlagsBuffer.put(this.flagBuffer);
      newFlagsBuffer.position(0);
      this.flagBuffer = newFlagsBuffer;
    }
  }

  public @NotNull TopGameStatus getGameStatus(int gameId) {
    long bit = (long) gameId * bitsPerGame();
    int offset = (int) (bit >> 3);
    int shift = (int) (bit & 7);
    if (gameId < 1 || offset >= this.flagBuffer.limit()) {
      return TopGameStatus.UNKNOWN;
    }

    int b = ByteBufferUtil.getUnsignedByte(this.flagBuffer, offset);
    if (shift > 6 && offset + 1 < this.flagBuffer.limit()) {
      b |= ByteBufferUtil.getUnsignedByte(this.flagBuffer, offset + 1) << 8;
    }
    return TopGameStatus.values()[(b >> shift) & 3];
  }

  public boolean isTopGame(int gameId) {
    return getGameStatus(gameId) == TopGameStatus.IS_TOP_GAME;
  }

  public void putGameStatus(int gameId, @NotNull TopGameStatus status) {
    putGameStatuses(Map.of(gameId, status));
  }

  public void putGameStatuses(@NotNull Map<Integer, TopGameStatus> statusMap) {
    if (statusMap.size() == 0) {
      return;
    }

    highestGameId = Math.max(highestGameId, Collections.max(statusMap.keySet()));

    extendBuffer();

    Set<Integer> dirtyChunks = new TreeSet<>();

    // Update everything in memory and flush dirty chunks
    for (Map.Entry<Integer, TopGameStatus> entry : statusMap.entrySet()) {
      int gameId = entry.getKey();
      int value = entry.getValue().ordinal();
      long bit = (long) gameId * bitsPerGame();
      int offset = (int) (bit >> 3);
      int shift = (int) (bit & 7);
      boolean straddles = shift > 6; // Only when using 3 bits per game
      int old = ByteBufferUtil.getUnsignedByte(flagBuffer, offset);
      if (straddles) {
        old |= ByteBufferUtil.getUnsignedByte(flagBuffer, offset + 1) << 8;
      }
      int updated = (old & ~(3 << shift)) | (value << shift);
      ByteBufferUtil.putByte(flagBuffer, offset, updated & 0xFF);
      dirtyChunks.add(offset / IN_MEMORY_CHUNK_SIZE);
      if (straddles) {
        ByteBufferUtil.putByte(flagBuffer, offset + 1, updated >> 8);
        dirtyChunks.add((offset + 1) / IN_MEMORY_CHUNK_SIZE);
      }
    }

    this.prolog = FlagsProlog.ofCapacity(fileCapacity(highestGameId), bitsPerGame());

    for (int chunkId : dirtyChunks) {
      flushChunk(chunkId);
    }
    flushProlog();
  }

  private void flushChunk(int chunkId) {
    if (inMemory || channel == null) return;

    try {
      int startOffset = chunkId * IN_MEMORY_CHUNK_SIZE;
      int length = Math.min(fileCapacity(highestGameId) * 4 - startOffset, IN_MEMORY_CHUNK_SIZE);
      ByteBuffer chunkBuffer = this.flagBuffer.slice(startOffset, length);
      channel.write(
          FlagsProlog.SERIALIZED_SIZE + (long) chunkId * IN_MEMORY_CHUNK_SIZE, chunkBuffer);
    } catch (IOException e) {
      throw new MorphyIOException("Error writing to TopGamesStorage", e);
    }
  }

  public void close() throws IOException {
    if (channel != null) {
      channel.close();
    }
  }
}
