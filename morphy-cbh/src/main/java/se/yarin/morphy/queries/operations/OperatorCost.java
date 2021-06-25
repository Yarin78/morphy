package se.yarin.morphy.queries.operations;

import org.immutables.value.Value;

@Value.Immutable
public abstract class OperatorCost {
    @Value.Default
    public long estimateRows() { return 0; }

    @Value.Default
    public long estimateDeserializations()  { return 0; }

    @Value.Default
    public long estimatePageReads()  { return 0; }

    @Value.Default
    public long actualRows()  { return 0; }

    @Value.Default
    public long actualDeserializations()  { return 0; }

    @Value.Default
    public long actualPhysicalPageReads()  { return 0; }

    @Value.Default
    public long actualLogicalPageReads()  { return 0; }

    @Value.Default
    public boolean actualIsDuplicate() { return false; }

    static long capRowEstimate(long value) {
        return Math.max(1, Math.min(value, Integer.MAX_VALUE));
    }

    static long capRowEstimate(double value) {
        return capRowEstimate(Math.round(value));
    }
}
