package se.yarin.morphy.cb2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.cb2.games.GameHeaderFile;
import se.yarin.morphy.cb2.games.GameRecord;
import se.yarin.morphy.cb2.games.TextHeader;
import se.yarin.morphy.cb2.storage.RecordFile;

/**
 * Places the records of games in the {@code .2cbg} or {@code .2cba} file, as ChessBase does (see
 * format/v2/6-behaviour.md#free-space-in-the-move-and-annotation-files).
 *
 * <p>A new record is appended with 96 spare bytes, or 192 for a guiding text, and 1 to 8 more so
 * that it ends on a multiple of 8. A replaced record that still fits is rewritten in place,
 * keeping its length. One that doesn't takes what it needs from the spare areas of the records
 * after it: each of those moves up by what's still needed and gives up as much of its spare as it
 * has, until enough is found. Records are kept in game id order, so the record after a game's is
 * the next game's; the file grows if the records after it haven't enough spare between them.
 */
final class RecordPlacement {
  static final int GAME_SPARE = 96;
  static final int TEXT_SPARE = 192;

  private final @NotNull RecordFile file;
  private final @NotNull GameHeaderFile headers;
  private final boolean annotations;
  private @Nullable Map<Long, Integer> owners;

  /**
   * @param file the file of records
   * @param headers the game headers, whose offsets are updated when records move
   * @param annotations whether the file is {@code .2cba}, whose offsets are at 0x10 and which has
   *     no records for guiding texts
   */
  RecordPlacement(@NotNull RecordFile file, @NotNull GameHeaderFile headers, boolean annotations) {
    this.file = file;
    this.headers = headers;
    this.annotations = annotations;
  }

  private long offsetOf(GameRecord record) {
    return annotations ? record.annotationOffset() : record.movesOffset();
  }

  private boolean hasRecord(GameRecord record) {
    return !annotations || !(record instanceof TextHeader);
  }

  private GameRecord withOffset(GameRecord record, long offset) {
    return annotations ? record.withAnnotationOffset(offset) : record.withMovesOffset(offset);
  }

  /**
   * Appends the record of a new game.
   *
   * @param tag the record's tag
   * @param content the record's content
   * @param baseSpare the spare area before rounding up to a multiple of 8
   * @return the offset of the record
   */
  long append(int tag, byte @NotNull [] content, int baseSpare) {
    long end = file.size() + RecordFile.FRAMING_SIZE + content.length + baseSpare;
    int pad = (int) (8 - end % 8);
    return file.append(new RecordFile.Record(tag, content, baseSpare + pad));
  }

  /**
   * Replaces the record of an existing game, moving the records after it if it doesn't fit.
   *
   * @param gameId the game, whose header holds the record's current offset
   * @param tag the new tag
   * @param content the new content
   */
  void replace(int gameId, int tag, byte @NotNull [] content) {
    long offset = offsetOf(headers.get(gameId));
    int oldLength = file.recordLength(offset);
    int room = oldLength - RecordFile.FRAMING_SIZE;
    if (content.length <= room) {
      file.write(offset, new RecordFile.Record(tag, content, room - content.length));
      return;
    }

    // Squeeze the records after it, collecting where each one goes
    record Move(int gameId, long from, long to, RecordFile.Record record) {}
    List<Move> moves = new ArrayList<>();
    long shift = content.length - room;
    long next = offset + oldLength;
    int id = gameId;
    while (shift > 0 && next < file.size()) {
      id = ownerOf(next, id);
      RecordFile.Record r = file.read(next);
      int take = (int) Math.min(shift, r.spare());
      moves.add(new Move(id, next, next + shift, new RecordFile.Record(r.tag(), r.content(), r.spare() - take)));
      next += r.length();
      shift -= take;
    }
    // Write the moved records last to first, so none overwrites one not yet read
    for (int i = moves.size() - 1; i >= 0; i--) {
      Move m = moves.get(i);
      file.write(m.to(), m.record());
      headers.put(withOffset(headers.get(m.gameId()), m.to()));
      if (owners != null) {
        owners.remove(m.from());
        owners.put(m.to(), m.gameId());
      }
    }
    file.write(offset, new RecordFile.Record(tag, content, 0));
  }

  /** The game whose record starts at an offset, the one after {@code previous} if it's there. */
  private int ownerOf(long offset, int previous) {
    for (int id = previous + 1; id <= headers.count(); id++) {
      GameRecord record = headers.get(id);
      if (!hasRecord(record)) {
        continue;
      }
      if (offsetOf(record) == offset) {
        return id;
      }
      break;
    }
    // The records are not in game id order; find the owner by its offset
    if (owners == null) {
      owners = new HashMap<>();
      for (int id = 1; id <= headers.count(); id++) {
        GameRecord record = headers.get(id);
        if (hasRecord(record)) {
          owners.put(offsetOf(record), id);
        }
      }
    }
    Integer owner = owners.get(offset);
    if (owner == null) {
      throw new InvalidDataException("No game owns the record at offset " + offset);
    }
    return owner;
  }
}
