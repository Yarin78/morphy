package se.yarin.morphy.boosters;

import org.immutables.value.Value;

/**
 * Represents an item in the .cit/.cit2 file
 */
@Value.Immutable
public interface IndexItem {
    int[] headTails();

    static IndexItem emptyCIT() {
        return ImmutableIndexItem.builder().headTails(-1, -1, -1, -1, -1, -1, -1, -1, -1, -1).build();
    }

    static IndexItem emptyCIT2() {
        return ImmutableIndexItem.builder().headTails(-1, -1).build();
    }
}
