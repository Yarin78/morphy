package se.yarin.morphy.boosters;

import org.immutables.value.Value;

import java.util.*;

/** Represents an item in the .cib/.cib2 file */
@Value.Immutable
public interface IndexBlockItem {
  int nextBlockId();

  int unknown();

  List<Integer> gameIds();

  static IndexBlockItem empty() {
    return ImmutableIndexBlockItem.builder()
        .nextBlockId(-1)
        .unknown(0)
        .gameIds(Collections.emptyList())
        .build();
  }
}
