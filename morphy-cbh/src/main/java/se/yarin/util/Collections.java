package se.yarin.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class Collections {
    /**
     * Generates all permutations of a list of elements. If the input list has n elements, the output list will have n!
     * elements. In particular, if the input list is empty, the output list will contain a single empty list.
     * @param elements the elements to permute
     * @return a list of all permutations
     * @param <T> the type of the elements
     */
    public static <T> List<List<T>> generatePermutations(@NotNull List<T> elements) {
        ArrayList<T> array = new ArrayList<>(elements); // Make a copy so we can mutate the order
        ArrayList<List<T>> permutations = new ArrayList<>();

        // https://www.baeldung.com/java-array-permutations
        int n = array.size();
        int[] indexes = new int[n];

        permutations.add(List.copyOf(array));

        int i = 0;
        while (i < n) {
            if (indexes[i] < i) {
                int j = i % 2 == 0 ?  0: indexes[i];
                T tmp = array.get(j);
                array.set(j, array.get(i));
                array.set(i, tmp);
                permutations.add(List.copyOf(array));
                indexes[i]++;
                i = 0;
            }
            else {
                indexes[i] = 0;
                i++;
            }
        }

        return permutations;
    }
}
