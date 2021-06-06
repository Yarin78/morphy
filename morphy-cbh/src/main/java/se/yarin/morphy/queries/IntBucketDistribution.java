package se.yarin.morphy.queries;

import java.util.stream.Stream;

public class IntBucketDistribution {
    private int cnt[]; // TODO: Use Fenwick tree
    private int total;

    public IntBucketDistribution() {
        cnt = new int[1];
        cnt[0] = 1;
        total = 1;
    }

    public IntBucketDistribution(int maxValue, Stream<Integer> ints) {
        cnt = new int[maxValue + 1];
        ints.forEach(integer -> cnt[Math.max(0, Math.min(integer, maxValue))]++);
        for (int j : cnt) {
            total += j;
        }
    }

    public double ratioLessThan(int value) {
        if (value < 0) {
            return 0.0;
        }
        value = Math.min(value, cnt.length - 1);
        int sum = 0;
        for (int i = 0; i < value; i++) {
            sum += cnt[i];
        }
        return 1.0 * sum / total;
    }

    public double ratioBetween(int lo, int hi) {
        return ratioLessThan(hi + 1) - ratioLessThan(lo);
    }
}
