package se.yarin.morphy.entities;

import org.immutables.value.Value;

@Value.Immutable
public abstract class TournamentExtraHeader {
  static final int DEFAULT_HEADER_VERSION = 3;
  static final int DEFAULT_RECORD_SIZE = 65;

  public abstract int version();

  public abstract int recordSize();

  // One less than the actual number of entries in the storage.
  // When 0, it can either mean that there's one entry or zero entries.
  public abstract int highestIndex();

  public static TournamentExtraHeader empty(int highestIndex) {
    return ImmutableTournamentExtraHeader.builder()
        .version(DEFAULT_HEADER_VERSION)
        .recordSize(DEFAULT_RECORD_SIZE)
        .highestIndex(highestIndex)
        .build();
  }

  public static TournamentExtraHeader empty() {
    return empty(0);
  }
}
