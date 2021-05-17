package se.yarin.morphy.boosters;

import org.immutables.value.Value;

import java.util.Arrays;

/**
 * Represents an item in the .cit/.cit2 file
 */
@Value.Immutable
public interface IndexItem {
    int[] headTails();

    static IndexItem emptyCIT(int numEntityTypes) {
        int[] ints = new int[numEntityTypes * 2];
        Arrays.fill(ints, -1);
        return ImmutableIndexItem.builder().headTails(ints).build();
    }
}
