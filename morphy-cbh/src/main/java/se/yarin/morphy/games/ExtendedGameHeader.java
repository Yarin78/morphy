package se.yarin.morphy.games;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Calendar;
import java.util.TimeZone;

@Value.Immutable
public abstract class ExtendedGameHeader {

    @Value.Default
    public int whiteTeamId() {
        return -1;
    }

    @Value.Default
    public int blackTeamId() {
        return -1;
    }

    @Value.Default
    public int mediaOffset() {
        // Offset into the .cbm file. Only used by guiding texts
        return -1;
    }

    @Value.Default
    public long annotationOffset() {
        // Should be same as GameHeader#annotationOffset. If different, the GH one will be used.
        return 0;
    }

    @Value.Default
    public boolean finalMaterial() {
        // If true, the FinalMaterial values below are set
        return false;
    }

    @Value.Default
    public FinalMaterial materialPlayer1() {
        // Not necessarily the white player!
        return FinalMaterial.decode(0);
    }

    @Value.Default
    public FinalMaterial materialPlayer2() {
        // Sorted by encoding order: pawns, queens and so on
        return FinalMaterial.decode(0);
    }

    @Value.Default
    public FinalMaterial materialTotal() {
        return FinalMaterial.decode(0);
    }

    @Value.Default
    public long movesOffset() {
        // Same as GameHeader#movesOffset. If different, the GH one will be used.
        return 0;
    }

    @Value.Default
    public @NotNull RatingType whiteRatingType() {
        return RatingType.unspecified();
    }

    @Value.Default
    public @NotNull RatingType blackRatingType() {
        return RatingType.unspecified();
    }

    @Value.Default
    public int unknown1() {
        // Quite often set, no idea for what. Probably something encoded.
        return 0;
    }

    @Value.Default
    public int unknown2() {
        // Quite often set, no idea for what. Probably something encoded.
        return 0;
    }

    @Value.Default
    public int gameVersion() {
        return 0;
    }

    @Value.Default
    public long creationTimestamp() {
        // 1/1024th seconds since December 1st 2008
        return 0;
    }

    @Value.Default
    public EndgameInfo endgameInfo() {
        return EndgameInfo.empty();
    }

    @Value.Default
    public long lastChangedTimestamp() {
        // 1/10,000,000th seconds since October 15th 1582
        return 0;
    }

    @Value.Default
    public int gameTagId() {
        return -1;
    }

    public static ImmutableExtendedGameHeader empty(long annotationOffset, long movesOffset) {
        return ImmutableExtendedGameHeader.builder()
                .annotationOffset(annotationOffset)
                .movesOffset(movesOffset)
                .build();
    }

    public static ImmutableExtendedGameHeader empty(GameHeader header) {
        return empty(header.annotationOffset(), header.movesOffset());
    }

    /**
     * Gets the date and time when this game was first created.
     * @return a date and time in the default TimeZone
     */
    public Calendar getCreationTime() {
        // Creation time is based on a ChessBase specific date
        Calendar creationTime = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"));
        creationTime.set(2008, 11, 1, 0, 0, 0);
        long seconds = creationTimestamp() / 1024;
        creationTime.add(Calendar.MINUTE, (int) (seconds / 60));
        creationTime.add(Calendar.SECOND, (int) (seconds % 60));
        creationTime.setTimeZone(TimeZone.getDefault());
        return creationTime;
    }

    /**
     * Gets the date and time when this game was last changed.
     * If the game has never changed since it was created, the value is undefined.
     * @return a date and time in the default TimeZone
     */
    public Calendar getLastChangedTime() {
        // Last changed time is based on the start of the Gregorian Calendar in UTC
        Calendar lastChangedTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        lastChangedTime.set(1582, 9, 15, 0, 0, 0);
        long seconds = lastChangedTimestamp() / 10000000L;
        lastChangedTime.add(Calendar.MINUTE, (int) (seconds / 60));
        lastChangedTime.add(Calendar.SECOND, (int) (seconds % 60));
        lastChangedTime.setTimeZone(TimeZone.getDefault());
        return lastChangedTime;
    }
}
