package se.yarin.morphy.cb2.entities;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.cb2.InvalidDataException;
import se.yarin.morphy.cb2.TextEncoding;

/**
 * An entity record of the {@code .2lid} file: a {@link Player}, {@link Tournament}, {@link
 * Source}, {@link Team} or {@link GameTag}. Every record starts with an {@code int} giving the
 * number of bytes that follow. See format/v2/4-entities.md.
 */
public sealed interface Entity permits Player, Tournament, Source, Team, GameTag {

  /** The kind of entity. */
  @NotNull
  EntityType type();

  /** Whether the entity's text is empty, making it the placeholder for a field left blank. */
  boolean isEmpty();

  /** The record as stored, its leading length included. */
  byte @NotNull [] encode();

  /**
   * Decodes a record.
   *
   * @param type the kind of entity
   * @param record the record, its leading length included
   * @throws InvalidDataException if the record doesn't follow its type's layout
   */
  static @NotNull Entity decode(@NotNull EntityType type, byte @NotNull [] record) {
    ByteBuffer buf = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN);
    try {
      int length = buf.getInt();
      if (length + 4 != record.length) {
        throw new InvalidDataException("An entity record with the wrong length");
      }
      Entity entity =
          switch (type) {
            case PLAYER -> Player.read(buf);
            case TOURNAMENT -> Tournament.read(buf);
            case SOURCE -> Source.read(buf);
            case TEAM -> Team.read(buf);
            case GAME_TAG -> GameTag.read(buf);
            case UNKNOWN -> throw new InvalidDataException("An entity of the unknown type 3");
          };
      if (buf.hasRemaining()) {
        throw new InvalidDataException(
            buf.remaining() + " bytes left over in a " + type + " record");
      }
      return entity;
    } catch (BufferUnderflowException | IndexOutOfBoundsException | NegativeArraySizeException e) {
      throw new InvalidDataException("A " + type + " record doesn't fit its layout", e);
    }
  }

  /** Reads an {@code int}-prefixed string. */
  static @NotNull String getString(@NotNull ByteBuffer buf) {
    byte[] bytes = new byte[buf.getInt()];
    buf.get(bytes);
    return TextEncoding.decode(bytes);
  }

  /** Writes an {@code int}-prefixed string. */
  static void putString(@NotNull ByteBuffer buf, @NotNull String text) {
    byte[] bytes = TextEncoding.encode(text);
    buf.putInt(bytes.length);
    buf.put(bytes);
  }

  /** The byte length of an {@code int}-prefixed string. */
  static int stringSize(@NotNull String text) {
    return 4 + TextEncoding.encode(text).length;
  }

  /** A buffer for a record whose content is {@code size} bytes, with the length already put. */
  static @NotNull ByteBuffer recordBuffer(int size) {
    ByteBuffer buf = ByteBuffer.allocate(4 + size).order(ByteOrder.LITTLE_ENDIAN);
    buf.putInt(size);
    return buf;
  }

  /** The bytes of a finished record buffer. */
  static byte @NotNull [] finish(@NotNull ByteBuffer buf) {
    if (buf.hasRemaining()) {
      throw new IllegalStateException(buf.remaining() + " bytes of a record left unwritten");
    }
    return Arrays.copyOf(buf.array(), buf.position());
  }
}
