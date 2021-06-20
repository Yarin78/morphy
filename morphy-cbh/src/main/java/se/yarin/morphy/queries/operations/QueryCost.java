package se.yarin.morphy.queries.operations;

import org.immutables.value.Value;

@Value.Immutable
public interface QueryCost {
    long rows();
    long pageReads();
    long numDeserializations();

    double cpuCost();
    double ioCost();

    long wallClockTime();

    default String format() {
        String s = String.format("cpuCost = %f, ioCost = %f (rows = %d, deser = %d, pageReads = %d)",
                cpuCost(), ioCost(), rows(), numDeserializations(), pageReads());
        if (wallClockTime() > 0) {
            s += String.format(" [%d ms]", wallClockTime());
        }
        return s;
    }
}
