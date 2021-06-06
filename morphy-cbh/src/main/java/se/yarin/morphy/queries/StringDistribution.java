package se.yarin.morphy.queries;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StringDistribution {
    private final List<String> strings;

    public StringDistribution() {
        strings = List.of("");
    }

    public StringDistribution(Stream<String> seed) {
        strings = seed.sorted().collect(Collectors.toList());
        if (strings.size() == 0) {
            strings.add("");
        }
    }

    public double ratioLessThan(String s) {
        int index = Collections.binarySearch(strings, s);
        if (index >= 0) {
            return 1.0 * index / strings.size();
        }
        int insertionPoint = -index-1;
        return 1.0 * insertionPoint / strings.size();
    }

    public double ratioLessThanEqual(String s) {
        int index = Collections.binarySearch(strings, s);
        if (index >= 0) {
            return 1.0 * (index + 1) / strings.size();
        }
        int insertionPoint = -index-1;
        return 1.0 * insertionPoint / strings.size();
    }

    public double ratioBetween(String lo, String hi) {
        return ratioLessThanEqual(hi) - ratioLessThan(lo);
    }

    public double ratioPrefix(String prefix) {
        return ratioBetween(prefix, prefix + "zzz");
    }
}
