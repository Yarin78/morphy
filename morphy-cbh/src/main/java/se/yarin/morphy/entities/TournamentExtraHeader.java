package se.yarin.morphy.entities;

import org.immutables.value.Value;

@Value.Immutable
public abstract class TournamentExtraHeader {
    private static final int DEFAULT_HEADER_VERSION = 3;
    private static final int DEFAULT_RECORD_SIZE = 65;

    public abstract int version();

    public abstract int recordSize();

    public abstract int numTournaments();

    public static TournamentExtraHeader empty(int numTournaments) {
        return ImmutableTournamentExtraHeader.builder()
                .version(DEFAULT_HEADER_VERSION)
                .recordSize(DEFAULT_RECORD_SIZE)
                .numTournaments(numTournaments)
                .build();
    }

    public static TournamentExtraHeader empty() {
        return empty(0);
    }
}
