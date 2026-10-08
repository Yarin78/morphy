package se.yarin.morphy.positions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Merges segments into one: their positions read through in hash order together, the games of a
 * position several segments have joined by the move played, the entries of superseded games left
 * out. What builds an index from its batches, and compacts an index's segments.
 */
final class SegmentMerger {
  private SegmentMerger() {}

  /** A segment's next position, and which segment it is. */
  private record Head(PositionEntry entry, int segment) {}

  /**
   * Merges segments into a writer.
   *
   * @param segments the segments, the oldest first
   * @param superseded which segment's entries of each game count
   * @return the positions written
   */
  static long merge(
      @NotNull List<SegmentReader> segments,
      @NotNull SupersededGames superseded,
      @NotNull SegmentWriter out,
      @NotNull Consumer<String> progress)
      throws IOException {
    long start = System.nanoTime();
    long total =
        segments.stream()
            .mapToLong(s -> s.meta().sharedPositions() + s.meta().singlePositions())
            .sum();
    List<SegmentReader.Positions> inputs = new ArrayList<>();
    try {
      PriorityQueue<Head> heads =
          new PriorityQueue<>((a, b) -> Long.compareUnsigned(a.entry().hash(), b.entry().hash()));
      for (int s = 0; s < segments.size(); s++) {
        SegmentReader.Positions positions = segments.get(s).positions();
        inputs.add(positions);
        PositionEntry first = positions.next();
        if (first != null) {
          heads.add(new Head(first, s));
        }
      }
      long read = 0, written = 0, reported = 0;
      List<Head> same = new ArrayList<>();
      while (!heads.isEmpty()) {
        same.clear();
        long hash = heads.peek().entry().hash();
        while (!heads.isEmpty() && heads.peek().entry().hash() == hash) {
          Head head = heads.poll();
          same.add(head);
          PositionEntry next = inputs.get(head.segment()).next();
          if (next != null) {
            heads.add(new Head(next, head.segment()));
          }
        }
        read += same.size();
        PositionEntry merged = join(same, superseded);
        if (merged != null) {
          out.add(merged);
          written++;
        }
        if (read - reported >= 50_000_000) {
          reported = read;
          progress.accept(
              String.format(
                  Locale.ROOT,
                  "%,d of %,d positions merged, %.0f s",
                  read,
                  total,
                  (System.nanoTime() - start) / 1e9));
        }
      }
      return written;
    } finally {
      for (SegmentReader.Positions input : inputs) {
        input.close();
      }
    }
  }

  /** The entries of one position in several segments as one, or null if no game of it counts. */
  private static @Nullable PositionEntry join(List<Head> same, SupersededGames superseded) {
    if (same.size() == 1 && superseded.none()) {
      return same.getFirst().entry();
    }
    Map<Integer, List<int[]>> byMove = new TreeMap<>();
    for (Head head : same) {
      for (MoveGroup group : head.entry().groups()) {
        int[] ids = group.gameIds();
        if (!superseded.none()) {
          ids = Arrays.stream(ids).filter(id -> superseded.counts(head.segment(), id)).toArray();
        }
        if (ids.length > 0) {
          byMove.computeIfAbsent(group.moveCode(), m -> new ArrayList<>()).add(ids);
        }
      }
    }
    if (byMove.isEmpty()) {
      return null;
    }
    List<MoveGroup> groups = new ArrayList<>(byMove.size());
    byMove.forEach(
        (move, parts) -> {
          int[] ids = parts.stream().flatMapToInt(Arrays::stream).toArray();
          if (parts.size() > 1) {
            Arrays.sort(ids);
          }
          groups.add(new MoveGroup(move, ids, null));
        });
    PositionEntry first = same.getFirst().entry();
    return new PositionEntry(first.hash(), first.whiteToMove(), groups);
  }
}
