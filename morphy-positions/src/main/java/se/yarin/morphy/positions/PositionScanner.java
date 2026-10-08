package se.yarin.morphy.positions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jetbrains.annotations.NotNull;
import se.yarin.chess.MoveCode;
import se.yarin.chess.Position;
import se.yarin.morphy.api.GameScan;
import se.yarin.morphy.api.MainLine;

/**
 * Finds the games that reached a position without an index, by playing through the main line of
 * every game of a scan: what an index answers in milliseconds takes some seconds for a Megabase,
 * but needs no build. The answer is the one an index built from the same games gives.
 */
public final class PositionScanner {
  private PositionScanner() {}

  // What moveFrom returns for a game that doesn't reach the position
  private static final int NOT_REACHED = -1;

  /**
   * Finds the games of a scan that reached a position, and the moves they played from it, the
   * first time they reached it.
   *
   * @param scan the games to look through, from several threads
   */
  public static @NotNull PositionGames find(@NotNull Position position, @NotNull GameScan scan) {
    long hash = position.getZobristHashLo();
    Queue<Found> all = new ConcurrentLinkedQueue<>();
    ThreadLocal<Found> found =
        ThreadLocal.withInitial(
            () -> {
              Found f = new Found();
              all.add(f);
              return f;
            });
    scan.forEachMainLine(
        (id, facts, line) -> {
          Found f = found.get();
          f.newestYear = Math.max(f.newestYear, facts.date().year());
          int move;
          try {
            move = moveFrom(line, hash);
          } catch (RuntimeException e) {
            // Moves that can't be decoded; the game is left out, as a scan leaves out such games
            return;
          }
          if (move != NOT_REACHED) {
            f.add(id, move);
            GameFactsTable.pack(facts, f.facts, 2 * (f.size - 1));
          }
        });

    // In id order, each game with its move and facts
    int n = all.stream().mapToInt(f -> f.size).sum();
    long[] order = new long[n];
    int[] foundMoves = new int[n];
    long[] foundFacts = new long[2 * n];
    int newestYear = 0, k = 0;
    for (Found f : all) {
      newestYear = Math.max(newestYear, f.newestYear);
      System.arraycopy(f.moves, 0, foundMoves, k, f.size);
      System.arraycopy(f.facts, 0, foundFacts, 2 * k, 2 * f.size);
      for (int i = 0; i < f.size; i++, k++) {
        // The id above where the game was found, so sorting orders by id
        order[k] = ((long) f.ids[i] << 32) | k;
      }
    }
    Arrays.sort(order);
    int[] ids = new int[n];
    int[] moves = new int[n];
    long[] facts = new long[2 * n];
    for (int i = 0; i < n; i++) {
      int from = (int) order[i];
      ids[i] = (int) (order[i] >>> 32);
      moves[i] = foundMoves[from];
      facts[2 * i] = foundFacts[2 * from];
      facts[2 * i + 1] = foundFacts[2 * from + 1];
    }
    return PositionGames.of(
        position, groups(ids, moves), GameFactsTable.ofGames(ids, facts), newestYear - 2);
  }

  /**
   * The move a main line plays from a position, the first time it's reached.
   *
   * @return the move's code, {@link IndexFiles#GAME_ENDED} if the game ends there, or {@link
   *     #NOT_REACHED}
   */
  private static int moveFrom(MainLine line, long hash) {
    while (true) {
      int code = line.moveCode();
      if (line.hash() == hash) {
        return code == MoveCode.NONE ? IndexFiles.GAME_ENDED : code;
      }
      if (code == MoveCode.NONE) {
        return NOT_REACHED;
      }
      line.advance();
    }
  }

  /** The games by the move they played, each group in id order. */
  private static List<MoveGroup> groups(int[] ids, int[] moves) {
    int[] codes = Arrays.stream(moves).distinct().toArray();
    List<MoveGroup> groups = new ArrayList<>(codes.length);
    for (int code : codes) {
      int[] games = new int[ids.length];
      int count = 0;
      for (int i = 0; i < ids.length; i++) {
        if (moves[i] == code) {
          games[count++] = ids[i];
        }
      }
      groups.add(new MoveGroup(code, Arrays.copyOf(games, count), null));
    }
    return groups;
  }

  /** The games one thread found: their ids, moves and facts, two longs each. */
  private static final class Found {
    int[] ids = new int[64];
    int[] moves = new int[64];
    long[] facts = new long[128];
    int size;
    int newestYear;

    void add(int id, int move) {
      if (size == ids.length) {
        ids = Arrays.copyOf(ids, 2 * size);
        moves = Arrays.copyOf(moves, 2 * size);
        facts = Arrays.copyOf(facts, 4 * size);
      }
      ids[size] = id;
      moves[size] = move;
      size++;
    }
  }
}
