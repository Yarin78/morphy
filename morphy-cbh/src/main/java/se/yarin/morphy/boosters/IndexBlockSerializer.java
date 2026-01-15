package se.yarin.morphy.boosters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.metrics.ItemMetrics;
import se.yarin.morphy.metrics.MetricsRef;
import se.yarin.morphy.storage.ItemStorageSerializer;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;

public class IndexBlockSerializer
    implements ItemStorageSerializer<IndexBlockHeader, IndexBlockItem> {

  private final MetricsRef<ItemMetrics> itemMetricsRef;

  public IndexBlockSerializer(@NotNull MetricsRef<ItemMetrics> itemMetricsRef) {
    this.itemMetricsRef = itemMetricsRef;
  }

  @Override
  public int serializedHeaderSize() {
    return 12;
  }

  @Override
  public long itemOffset(@NotNull IndexBlockHeader indexBlockHeader, int index) {
    return 12 + (long) itemSize(indexBlockHeader) * index;
  }

  @Override
  public int itemSize(@NotNull IndexBlockHeader indexBlockHeader) {
    return indexBlockHeader.itemSize();
  }

  @Override
  public int headerSize(@NotNull IndexBlockHeader indexBlockHeader) {
    return 12;
  }

  @Override
  public @NotNull IndexBlockHeader deserializeHeader(@NotNull ByteBuffer buf)
      throws MorphyInvalidDataException {
    return ImmutableIndexBlockHeader.builder()
        .itemSize(ByteBufferUtil.getIntL(buf))
        .numBlocks(ByteBufferUtil.getIntL(buf))
        .deletedBlockId(ByteBufferUtil.getIntL(buf))
        .build();
  }

  @Override
  public @NotNull IndexBlockItem deserializeItem(
      int id, @NotNull ByteBuffer buf, @NotNull IndexBlockHeader header) {
    itemMetricsRef.update(metrics -> metrics.addDeserialization(1));

    int prevPos = buf.position();
    ImmutableIndexBlockItem.Builder builder =
        ImmutableIndexBlockItem.builder()
            .nextBlockId(ByteBufferUtil.getIntL(buf))
            .unknown(ByteBufferUtil.getIntL(buf));

    int numGames = ByteBufferUtil.getIntL(buf);

    ArrayList<Integer> gameIds = new ArrayList<>();
    for (int i = 0; i < numGames; i++) {
      gameIds.add(ByteBufferUtil.getIntL(buf));
    }
    buf.position(prevPos + header.itemSize());

    return builder.gameIds(Collections.unmodifiableList(gameIds)).build();
  }

  @Override
  public @NotNull IndexBlockItem emptyItem(int id) {
    return IndexBlockItem.empty();
  }

  @Override
  public void serializeHeader(@NotNull IndexBlockHeader indexBlockHeader, @NotNull ByteBuffer buf) {
    ByteBufferUtil.putIntL(buf, indexBlockHeader.itemSize());
    ByteBufferUtil.putIntL(buf, indexBlockHeader.numBlocks());
    ByteBufferUtil.putIntL(buf, indexBlockHeader.deletedBlockId());
  }

  @Override
  public void serializeItem(
      @NotNull IndexBlockItem indexBlockItem,
      @NotNull ByteBuffer buf,
      @NotNull IndexBlockHeader header) {
    itemMetricsRef.update(metrics -> metrics.addSerialization(1));

    int numInts = (header.itemSize() - 12) / 4;

    ByteBufferUtil.putIntL(buf, indexBlockItem.nextBlockId());
    ByteBufferUtil.putIntL(buf, indexBlockItem.unknown());
    int numGames = indexBlockItem.gameIds().size();
    ByteBufferUtil.putIntL(buf, numGames);
    for (int i = 0; i < numInts; i++) {
      ByteBufferUtil.putIntL(buf, i < numGames ? indexBlockItem.gameIds().get(i) : 0);
    }
  }
}
