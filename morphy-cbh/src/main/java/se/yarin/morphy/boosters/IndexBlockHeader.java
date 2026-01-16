package se.yarin.morphy.boosters;

/** Represents the header in the .cib/.cib2 file */
public record IndexBlockHeader(int itemSize, int numBlocks, int deletedBlockId) {
  public static IndexBlockHeader empty() {
    return new IndexBlockHeader(64, 0, 0);
  }
}
