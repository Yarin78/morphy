package se.yarin.morphy.boosters;

import java.util.Arrays;

/** Represents an item in the .cit/.cit2 file */
public record IndexItem(int[] headTails) {
  public static IndexItem emptyCIT(int numEntityTypes) {
    int[] ints = new int[numEntityTypes * 2];
    Arrays.fill(ints, -1);
    return new IndexItem(ints);
  }
}
