package se.yarin.morphy.cb2.games;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.cb2.InvalidDataException;

/**
 * One 192-byte record of the {@code .2cbh} file: a {@link GameHeader game}, a {@link TextHeader
 * guiding text} or an {@link AnalysisHeader analysis}. The byte at 0x02 gives the kind, and bit 1
 * of the type byte at 0x00 separates a text from a game.
 *
 * <p>Each record remembers the bytes it was read from, so that fields whose meaning is unknown
 * are written back as they were.
 */
public sealed interface GameRecord permits GameHeader, TextHeader, AnalysisHeader {

  int SIZE = 192;

  /** Bit 0 of the type byte, always set. */
  int TYPE_BASE = 0x01;
  /** Bit 1 of the type byte, set for a guiding text. */
  int TYPE_TEXT = 0x02;
  /** Bit 7 of the type byte, set for a deleted record. */
  int TYPE_DELETED = 0x80;

  int KIND_GAME = 1;
  int KIND_ANALYSIS = 2;

  /** The 1-based game id. */
  int id();

  /** Whether the record is marked as deleted. */
  boolean deleted();

  /** The offset of the record's moves, or of a text's body, in the {@code .2cbg} file. */
  long movesOffset();

  /** The offset of the record's annotations in the {@code .2cba} file, or 0 if it has none. */
  long annotationOffset();

  /** The bytes the record was read from, or null for a new record. */
  byte @Nullable [] raw();

  /** The record with its moves stored at another offset. */
  @NotNull
  GameRecord withMovesOffset(long offset);

  /** The record with its annotations stored at another offset. */
  @NotNull
  GameRecord withAnnotationOffset(long offset);

  /** The record as stored. */
  byte @NotNull [] encode();

  /**
   * Decodes a record.
   *
   * @param id the game id
   * @param bytes the 192 bytes of the record
   * @return the record
   * @throws InvalidDataException if the record is of an unknown kind
   */
  static @NotNull GameRecord decode(int id, byte @NotNull [] bytes) {
    if (bytes.length != SIZE) {
      throw new InvalidDataException("A game record is " + bytes.length + " bytes");
    }
    int type = bytes[0] & 0xFF;
    int kind = bytes[2] & 0xFF;
    if (kind == KIND_ANALYSIS) {
      return AnalysisHeader.decode(id, bytes);
    }
    if (kind == KIND_GAME) {
      return (type & TYPE_TEXT) != 0 ? TextHeader.decode(id, bytes) : GameHeader.decode(id, bytes);
    }
    throw new InvalidDataException("Game " + id + " has an unknown record kind " + kind);
  }

  /** A little-endian buffer over a copy of the base bytes, or over zeros. */
  static @NotNull ByteBuffer buffer(byte @Nullable [] base) {
    byte[] bytes = base == null ? new byte[SIZE] : base.clone();
    return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
  }

  /** Writes the eight bytes common to every kind of record. */
  static void putCommon(@NotNull ByteBuffer buf, int type, int kind, boolean deleted) {
    int typeByte = type | TYPE_BASE;
    buf.put(0, (byte) (typeByte | (deleted ? TYPE_DELETED : 0)));
    buf.put(2, (byte) kind);
    buf.put(3, (byte) typeByte);
  }
}
