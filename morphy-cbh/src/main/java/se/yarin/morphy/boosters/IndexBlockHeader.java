package se.yarin.morphy.boosters;

import org.immutables.value.Value;

/**
 * Represents the header in the .cib/.cib2 file
 */
@Value.Immutable
public interface IndexBlockHeader {
    int itemSize();
    int numBlocks();
    int unknown();

    static IndexBlockHeader empty() {
        return ImmutableIndexBlockHeader.builder()
                .itemSize(64)
                .numBlocks(0)
                .unknown(0)
                .build();
    }
}
