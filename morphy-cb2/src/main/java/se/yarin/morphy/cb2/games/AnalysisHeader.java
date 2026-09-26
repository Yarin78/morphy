package se.yarin.morphy.cb2.games;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The record of an analysis in the {@code .2cbh} file. An analysis has moves and annotations like
 * a game, and a title held by a game tag entity. The fields from 0x30 to 0x57 are unknown and kept
 * in {@link #raw()}.
 *
 * @param id the 1-based game id
 * @param deleted whether the analysis is marked as deleted
 * @param movesOffset the offset of the moves in {@code .2cbg}
 * @param annotationOffset the offset of the annotations in {@code .2cba}
 * @param titleId the title, a game tag entity
 * @param sourceId the source
 * @param annotatorId the author, a player entity
 * @param raw the bytes the record was read from, or null
 */
public record AnalysisHeader(
    int id,
    boolean deleted,
    long movesOffset,
    long annotationOffset,
    long titleId,
    long sourceId,
    long annotatorId,
    byte @Nullable [] raw)
    implements GameRecord {

  static @NotNull AnalysisHeader decode(int id, byte @NotNull [] bytes) {
    ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    return new AnalysisHeader(
        id,
        (bytes[0] & TYPE_DELETED) != 0,
        buf.getLong(0x08),
        buf.getLong(0x10),
        buf.getLong(0x18),
        buf.getLong(0x20),
        buf.getLong(0x28),
        bytes);
  }

  @Override
  public byte @NotNull [] encode() {
    ByteBuffer buf = GameRecord.buffer(raw);
    GameRecord.putCommon(buf, 0, KIND_ANALYSIS, deleted);
    buf.putLong(0x08, movesOffset);
    buf.putLong(0x10, annotationOffset);
    buf.putLong(0x18, titleId);
    buf.putLong(0x20, sourceId);
    buf.putLong(0x28, annotatorId);
    return buf.array();
  }

  @Override
  public @NotNull AnalysisHeader withMovesOffset(long offset) {
    return new AnalysisHeader(
        id, deleted, offset, annotationOffset, titleId, sourceId, annotatorId, raw);
  }

  @Override
  public @NotNull AnalysisHeader withAnnotationOffset(long offset) {
    return new AnalysisHeader(id, deleted, movesOffset, offset, titleId, sourceId, annotatorId, raw);
  }
}
