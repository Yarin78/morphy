package se.yarin.morphy.queries.operations;

import org.immutables.value.Value;

@Value.Immutable
public interface QueryCost {
  long actualRows();

  long actualPhysicalPageReads();

  long actualLogicalPageReads();

  long actualDeserializations();

  long actualWallClockTime();

  long estimatedRows();

  long estimatedPageReads();

  long estimatedDeserializations();

  double estimatedCpuCost();

  double estimatedIOCost();

  double estimatedTotalCost();

  default String format() {
    String actual = "";
    if (actualRows() > 0) {
      // We have actual data
      actual =
          String.format(
              """
                    Execution time:             %8d ms

                    Actual rows:                %8d
                    Actual physical reads:      %8d
                    Actual logical reads:       %8d
                    Actual deserializations:    %8d

                    """,
              actualWallClockTime(),
              actualRows(),
              actualPhysicalPageReads(),
              actualLogicalPageReads(),
              actualDeserializations());
    }
    String estimate =
        String.format(
            """
                        Estimate rows:              %8d
                        Estimate page reads:        %8d
                        Estimate deserializations:  %8d

                        Estimate CPU cost:          %8d
                        Estimate IO cost:           %8d
                        Estimate Total cost:        %8d
                        """,
            estimatedRows(),
            estimatedPageReads(),
            estimatedDeserializations(),
            (long) estimatedCpuCost(),
            (long) estimatedIOCost(),
            (long) estimatedTotalCost());

    return actual + estimate;
  }
}
