package se.yarin.morphy.cb2.games;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The record of a game in the {@code .2cbh} file. See format/v2/1-game-headers.md.
 *
 * @param id the 1-based game id
 * @param deleted whether the game is marked as deleted
 * @param movesOffset the offset of the moves in {@code .2cbg}
 * @param annotationOffset the offset of the annotations in {@code .2cba}; every game has a record
 * @param whiteId the white player
 * @param blackId the black player
 * @param tournamentId the tournament
 * @param annotatorId the annotator, a player entity
 * @param sourceId the source
 * @param whiteTeamId the white team, -1 if none
 * @param blackTeamId the black team, -1 if none
 * @param gameTagId the game tag
 * @param result the result, the ordinal of a {@link se.yarin.chess.GameResult}
 * @param lineEvaluation a NAG evaluating the position when the result is a line, otherwise 0
 * @param round the round, 0 if not set
 * @param subRound the subround, 0 if not set
 * @param board the board, 0 if not set
 * @param whiteElo white's elo, 0 if none
 * @param whiteRating what white's elo is
 * @param blackElo black's elo, 0 if none
 * @param blackRating what black's elo is
 * @param eco the ECO code or Chess960 start position, see {@link EcoField}
 * @param medals the medals, one bit each
 * @param flags which kinds of annotations the game has, and more; see {@link
 *     se.yarin.morphy.chessbase.GameHeaderFlags}
 * @param annotationMagnitudes a rough size of some kinds of annotations
 * @param moveCount the number of full moves in the main line
 * @param finalMaterialGreater the greater of the players' final material, see {@link
 *     FinalMaterial}
 * @param finalMaterialLesser the lesser of the players' final material
 * @param creationTimestamp when the record was created, see {@link Timestamps}
 * @param lastChangedTimestamp when the record was last changed, 0 if never
 * @param endgameTypes a 48-bit mask of the endgame matchups the main line passed through
 * @param classificationScores 48 bits of scores filled in when the database is classified
 * @param version increased by one each time the record is saved
 * @param playedDate the date the game was played, see {@link Dates}
 * @param raw the bytes the record was read from, or null
 */
public record GameHeader(
    int id,
    boolean deleted,
    long movesOffset,
    long annotationOffset,
    long whiteId,
    long blackId,
    long tournamentId,
    long annotatorId,
    long sourceId,
    long whiteTeamId,
    long blackTeamId,
    long gameTagId,
    int result,
    int lineEvaluation,
    int round,
    int subRound,
    int board,
    int whiteElo,
    @NotNull RatingType whiteRating,
    int blackElo,
    @NotNull RatingType blackRating,
    int eco,
    int medals,
    int flags,
    int annotationMagnitudes,
    int moveCount,
    int finalMaterialGreater,
    int finalMaterialLesser,
    long creationTimestamp,
    long lastChangedTimestamp,
    long endgameTypes,
    long classificationScores,
    int version,
    int playedDate,
    byte @Nullable [] raw)
    implements GameRecord {

  private static final long MASK_48 = (1L << 48) - 1;

  static @NotNull GameHeader decode(int id, byte @NotNull [] bytes) {
    ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    boolean deleted = (bytes[0] & TYPE_DELETED) != 0;
    buf.position(0x62);
    RatingType whiteRating = RatingType.read(buf);
    buf.position(0x72);
    RatingType blackRating = RatingType.read(buf);
    return new GameHeader(
        id,
        deleted,
        buf.getLong(0x08),
        buf.getLong(0x10),
        buf.getLong(0x18),
        buf.getLong(0x20),
        buf.getLong(0x28),
        buf.getLong(0x30),
        buf.getLong(0x38),
        buf.getLong(0x40),
        buf.getLong(0x48),
        buf.getLong(0x50),
        bytes[0x58] & 0xFF,
        bytes[0x59] & 0xFF,
        buf.getShort(0x5a),
        buf.getShort(0x5c),
        buf.getShort(0x5e),
        buf.getShort(0x60),
        whiteRating,
        buf.getShort(0x70),
        blackRating,
        buf.getShort(0x80) & 0xFFFF,
        buf.getShort(0x82) & 0xFFFF,
        buf.getInt(0x84),
        buf.getShort(0x88) & 0xFFFF,
        buf.getShort(0x8a),
        buf.getInt(0x8c),
        buf.getInt(0x90),
        buf.getLong(0x98),
        buf.getLong(0xa0),
        buf.getLong(0xa8) & MASK_48,
        buf.getLong(0xb0) & MASK_48,
        buf.getInt(0xb8),
        buf.getInt(0xbc),
        bytes);
  }

  @Override
  public byte @NotNull [] encode() {
    ByteBuffer buf = GameRecord.buffer(raw);
    GameRecord.putCommon(buf, 0, KIND_GAME, deleted);
    buf.putLong(0x08, movesOffset);
    buf.putLong(0x10, annotationOffset);
    buf.putLong(0x18, whiteId);
    buf.putLong(0x20, blackId);
    buf.putLong(0x28, tournamentId);
    buf.putLong(0x30, annotatorId);
    buf.putLong(0x38, sourceId);
    buf.putLong(0x40, whiteTeamId);
    buf.putLong(0x48, blackTeamId);
    buf.putLong(0x50, gameTagId);
    buf.put(0x58, (byte) result);
    buf.put(0x59, (byte) lineEvaluation);
    buf.putShort(0x5a, (short) round);
    buf.putShort(0x5c, (short) subRound);
    buf.putShort(0x5e, (short) board);
    buf.putShort(0x60, (short) whiteElo);
    buf.position(0x62);
    whiteRating.write(buf);
    buf.putShort(0x70, (short) blackElo);
    buf.position(0x72);
    blackRating.write(buf);
    buf.putShort(0x80, (short) eco);
    buf.putShort(0x82, (short) medals);
    buf.putInt(0x84, flags);
    buf.putShort(0x88, (short) annotationMagnitudes);
    buf.putShort(0x8a, (short) moveCount);
    buf.putInt(0x8c, finalMaterialGreater);
    buf.putInt(0x90, finalMaterialLesser);
    buf.putLong(0x98, creationTimestamp);
    buf.putLong(0xa0, lastChangedTimestamp);
    put48(buf, 0xa8, endgameTypes);
    put48(buf, 0xb0, classificationScores);
    buf.putInt(0xb8, version);
    buf.putInt(0xbc, playedDate);
    return buf.array();
  }

  private static void put48(ByteBuffer buf, int offset, long value) {
    for (int i = 0; i < 6; i++) {
      buf.put(offset + i, (byte) (value >> (8 * i)));
    }
  }

  /** Whether the game is a Chess960 game, whose {@link #eco()} holds the start position. */
  public boolean chess960() {
    return EcoField.isChess960(eco);
  }

  @Override
  public @NotNull GameHeader withMovesOffset(long offset) {
    return new GameHeader(
        id, deleted, offset, annotationOffset, whiteId, blackId, tournamentId, annotatorId,
        sourceId, whiteTeamId, blackTeamId, gameTagId, result, lineEvaluation, round, subRound,
        board, whiteElo, whiteRating, blackElo, blackRating, eco, medals, flags,
        annotationMagnitudes, moveCount, finalMaterialGreater, finalMaterialLesser,
        creationTimestamp, lastChangedTimestamp, endgameTypes, classificationScores, version,
        playedDate, raw);
  }

  @Override
  public @NotNull GameHeader withAnnotationOffset(long offset) {
    return new GameHeader(
        id, deleted, movesOffset, offset, whiteId, blackId, tournamentId, annotatorId, sourceId,
        whiteTeamId, blackTeamId, gameTagId, result, lineEvaluation, round, subRound, board,
        whiteElo, whiteRating, blackElo, blackRating, eco, medals, flags, annotationMagnitudes,
        moveCount, finalMaterialGreater, finalMaterialLesser, creationTimestamp,
        lastChangedTimestamp, endgameTypes, classificationScores, version, playedDate, raw);
  }
}
