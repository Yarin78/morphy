package se.yarin.morphy.queries.operations;

import org.immutables.value.Value;

@Value.Immutable
public interface OperatorCost {
    long rows();
    long pageReads();
    long numDeserializations();

    static long capRowEstimate(long value) {
        return Math.max(1, Math.min(value, Integer.MAX_VALUE));
    }

    static long capRowEstimate(double value) {
        return capRowEstimate(Math.round(value));
    }
}
