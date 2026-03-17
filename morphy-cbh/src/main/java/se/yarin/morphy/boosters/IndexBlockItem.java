package se.yarin.morphy.boosters;

import se.yarin.morphy.IdObject;

import java.util.*;

/** Represents an item in the .cib/.cib2 file */
public record IndexBlockItem(int id, int nextBlockId, int unknown, List<Integer> gameIds)
    implements IdObject {
  public static IndexBlockItem empty(int id) {
    return new IndexBlockItem(id, -1, 0, List.of());
  }
}
