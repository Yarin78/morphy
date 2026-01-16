package se.yarin.morphy.boosters;

/** Represents the header in the .cit/.cit2 file */
public record IndexHeader(int itemSize, int unknown1, int unknown2) {
  public static IndexHeader emptyCIT() {
    return new IndexHeader(40, 0, 0);
  }

  public static IndexHeader emptyCIT2() {
    return new IndexHeader(8, 0, 0);
  }
}
