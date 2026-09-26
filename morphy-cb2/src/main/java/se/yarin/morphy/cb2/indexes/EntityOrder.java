package se.yarin.morphy.cb2.indexes;

import java.util.Locale;
import java.util.function.IntFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.cb2.entities.Entity;
import se.yarin.morphy.cb2.entities.GameTag;
import se.yarin.morphy.cb2.entities.Player;
import se.yarin.morphy.cb2.entities.Source;
import se.yarin.morphy.cb2.entities.Team;
import se.yarin.morphy.cb2.entities.Tournament;

/**
 * The sort order of each kind of entity in the {@code .2lcd} indexes, and the 8-byte keys that make
 * the common comparison a single integer compare. See format/v2/5-indexes.md#keys.
 */
public final class EntityOrder {
  private EntityOrder() {}

  /** The key of an entity whose sort fields are all empty, placing it first. */
  public static final long EMPTY_KEY = 1;

  private static final String PLAYER_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789 -";
  private static final String TOURNAMENT_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

  /**
   * The key of an entity: the first bytes of its sort fields, compared as an unsigned big-endian
   * integer; 0 when a character it would be built from is not permitted; {@link #EMPTY_KEY} when
   * all the sort fields are empty.
   */
  public static long key(@NotNull Entity entity) {
    return switch (entity) {
      case Player p -> {
        if (p.lastName().isEmpty() && p.firstName().isEmpty()) {
          yield EMPTY_KEY;
        }
        yield textKey(new byte[0], p.lastName(), 8, 8, PLAYER_CHARS);
      }
      case Tournament t -> {
        int year = t.startDate() >> 9;
        if (year == 0 && t.title().isEmpty() && t.place().isEmpty() && (t.startDate() & 511) == 0) {
          yield EMPTY_KEY;
        }
        yield textKey(new byte[] {(byte) (year >> 8), (byte) year}, t.title(), 6, 6, TOURNAMENT_CHARS);
      }
      case Source s -> s.title().isEmpty() ? EMPTY_KEY : textKey(new byte[0], s.title(), 8, 8, null);
      case Team t -> {
        if (t.title().isEmpty() && t.number() == 0 && t.nation() == 0 && t.year() == 0) {
          yield EMPTY_KEY;
        }
        yield textKey(new byte[0], t.title(), 8, 8, null);
      }
      case GameTag g -> {
        if (g.titles().isEmpty()) {
          yield EMPTY_KEY;
        }
        GameTag.Title first = g.firstTitle();
        yield textKey(new byte[] {(byte) first.language()}, first.text(), 7, 8, null);
      }
    };
  }

  /**
   * Builds a key from a prefix and the start of a text.
   *
   * @param prefix bytes that come first
   * @param text the text
   * @param length how many characters of the text go into the key
   * @param checked how many characters must be permitted
   * @param permitted the permitted characters, or null for any ASCII
   */
  private static long textKey(
      byte[] prefix, String text, int length, int checked, @Nullable String permitted) {
    String lower = text.toLowerCase(Locale.ROOT);
    for (int i = 0; i < Math.min(checked, lower.length()); i++) {
      char c = lower.charAt(i);
      boolean ok = permitted == null ? c < 128 : permitted.indexOf(c) >= 0;
      if (!ok) {
        return 0;
      }
    }
    long key = 0;
    int pos = 0;
    for (byte b : prefix) {
      key |= (long) (b & 0xFF) << (8 * (7 - pos++));
    }
    for (int i = 0; i < Math.min(length, lower.length()); i++) {
      key |= (long) (lower.charAt(i) & 0xFF) << (8 * (7 - pos++));
    }
    return key;
  }

  /** Compares two entities of the same type by their sort fields, without the id. */
  public static int compareFields(@NotNull Entity a, @NotNull Entity b) {
    return switch (a) {
      case Player p -> {
        Player q = (Player) b;
        int c = Collation.PLAYERS.compare(p.lastName(), q.lastName());
        yield c != 0 ? c : Collation.PLAYERS.compare(p.firstName(), q.firstName());
      }
      case Tournament t -> {
        Tournament u = (Tournament) b;
        int c = Integer.compare(t.startDate() >> 9, u.startDate() >> 9);
        if (c == 0) c = Collation.TOURNAMENTS.compare(t.title(), u.title());
        if (c == 0) c = Collation.TOURNAMENTS.compare(t.place(), u.place());
        if (c == 0) c = Integer.compare((t.startDate() >> 5) & 15, (u.startDate() >> 5) & 15);
        if (c == 0) c = Integer.compare(t.startDate() & 31, u.startDate() & 31);
        yield c;
      }
      case Source s -> Collation.DEFAULT.compare(s.title(), ((Source) b).title());
      case Team t -> {
        Team u = (Team) b;
        int c = Collation.DEFAULT.compare(t.title(), u.title());
        if (c == 0) c = Integer.compare(t.number(), u.number());
        if (c == 0) c = Integer.compare(t.nation(), u.nation());
        if (c == 0) c = Integer.compare(t.year(), u.year());
        if (c == 0) c = Integer.compare(t.seasonFlags(), u.seasonFlags());
        yield c;
      }
      case GameTag g -> {
        GameTag.Title x = g.firstTitle(), y = ((GameTag) b).firstTitle();
        int c = Integer.compare(x.language(), y.language());
        yield c != 0 ? c : Collation.DEFAULT.compare(x.text(), y.text());
      }
    };
  }

  /**
   * Compares two entities as a sort order does: by key when both have one and they differ, and
   * otherwise by their sort fields, and finally by id.
   */
  public static int compare(
      @NotNull Entity a, long keyA, long idA, @NotNull Entity b, long keyB, long idB) {
    if (keyA != 0 && keyB != 0 && keyA != keyB) {
      return Long.compareUnsigned(keyA, keyB);
    }
    int c = compareFields(a, b);
    return c != 0 ? c : Long.compare(idA, idB);
  }

  /** A comparator of nodes whose entities are looked up by id. */
  public static SortIndexFile.@NotNull NodeComparator nodeComparator(
      @NotNull IntFunction<Entity> entities) {
    return (x, y) ->
        compare(
            entities.apply((int) x.id()), x.key(), x.id(), entities.apply((int) y.id()), y.key(), y.id());
  }
}
