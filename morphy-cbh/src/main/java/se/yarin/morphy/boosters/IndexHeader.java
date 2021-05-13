package se.yarin.morphy.boosters;

import org.immutables.value.Value;

/**
 * Represents the header in the .cit/.cit2 file
 */
@Value.Immutable
public interface IndexHeader {
    int itemSize();
    int unknown1();
    int unknown2();

    static IndexHeader emptyCIT() {
        return ImmutableIndexHeader.builder()
                .itemSize(40)
                .unknown1(0)
                .unknown2(0)
                .build();
    }

    static IndexHeader emptyCIT2() {
        return ImmutableIndexHeader.builder()
                .itemSize(8)
                .unknown1(0)
                .unknown2(0)
                .build();
    }
}
