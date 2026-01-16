package se.yarin.morphy.boosters;

import java.util.*;

/** Represents an item in the .cib/.cib2 file */
public record IndexBlockItem(int nextBlockId, int unknown, List<Integer> gameIds) {
  public static IndexBlockItem empty() {
    return new IndexBlockItem(-1, 0, List.of());
  }
}
