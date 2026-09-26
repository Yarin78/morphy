package se.yarin.morphy.cb2.games;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The record of a guiding text in the {@code .2cbh} file. A text has no annotations, and its title
 * is a game tag entity.
 *
 * @param id the 1-based game id
 * @param deleted whether the text is marked as deleted
 * @param movesOffset the offset of the text's body in {@code .2cbg}
 * @param tournamentId the tournament
 * @param sourceId the source
 * @param annotatorId the author, a player entity
 * @param titleId the title, a game tag entity
 * @param creationTimestamp when the record was created
 * @param mediaOffset the media offset; its high bits are unknown
 * @param version increased by one each time the record is saved
 * @param raw the bytes the record was read from, or null
 */
public record TextHeader(
    int id,
    boolean deleted,
    long movesOffset,
    long tournamentId,
    long sourceId,
    long annotatorId,
    long titleId,
    long creationTimestamp,
    long mediaOffset,
    long version,
    byte @Nullable [] raw)
    implements GameRecord {

  static @NotNull TextHeader decode(int id, byte @NotNull [] bytes) {
    ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    return new TextHeader(
        id,
        (bytes[0] & TYPE_DELETED) != 0,
        buf.getLong(0x08),
        buf.getLong(0x10),
        buf.getLong(0x18),
        buf.getLong(0x20),
        buf.getLong(0x28),
        buf.getLong(0x30),
        buf.getLong(0x38),
        buf.getLong(0x40),
        bytes);
  }

  @Override
  public byte @NotNull [] encode() {
    ByteBuffer buf = GameRecord.buffer(raw);
    GameRecord.putCommon(buf, TYPE_TEXT, KIND_GAME, deleted);
    buf.putLong(0x08, movesOffset);
    buf.putLong(0x10, tournamentId);
    buf.putLong(0x18, sourceId);
    buf.putLong(0x20, annotatorId);
    buf.putLong(0x28, titleId);
    buf.putLong(0x30, creationTimestamp);
    buf.putLong(0x38, mediaOffset);
    buf.putLong(0x40, version);
    return buf.array();
  }

  /** A text has no annotations. */
  @Override
  public long annotationOffset() {
    return 0;
  }

  @Override
  public @NotNull TextHeader withMovesOffset(long offset) {
    return new TextHeader(
        id, deleted, offset, tournamentId, sourceId, annotatorId, titleId, creationTimestamp,
        mediaOffset, version, raw);
  }

  @Override
  public @NotNull TextHeader withAnnotationOffset(long offset) {
    if (offset != 0) {
      throw new IllegalArgumentException("A guiding text has no annotations");
    }
    return this;
  }
}
