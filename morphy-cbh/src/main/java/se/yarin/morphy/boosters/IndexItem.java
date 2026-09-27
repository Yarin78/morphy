package se.yarin.morphy.boosters;

import se.yarin.morphy.IdObject;

import java.util.Arrays;

/** Represents an item in the .cit/.cit2 file */
public record IndexItem(int id, int[] headTails) implements IdObject {
  public static IndexItem emptyCIT(int id, int numEntityTypes) {
    int[] ints = new int[numEntityTypes * 2];
    Arrays.fill(ints, -1);
    return new IndexItem(id, ints);
  }
}
