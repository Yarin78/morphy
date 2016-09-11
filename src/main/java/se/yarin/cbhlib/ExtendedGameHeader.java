package se.yarin.cbhlib;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * An extended game header record in the CBJ file. This class is immutable.
 */
@Builder(toBuilder = true)
@Data
@EqualsAndHashCode
public class ExtendedGameHeader {
    private final int id;
    private final int whiteTeamId;
    private final int blackTeamId;
    private final int mediaOffset; // Offset into the .cbm file. Only used by guiding texts
    // There are cases where these two differs, which is probably a bug in ChessBase
    // TODO: Check which of the two values are actually used by ChessBase. And if the CB validator detects the errors
    private final long annotationOffset; // Same as GameHeader#annotationOffset
    private final boolean finalMaterial; // If false, the FinalMaterial values below are undefined
    private final FinalMaterial materialPlayer1; // Not necessarily the white player!
    private final FinalMaterial materialPlayer2; // Sorted by encoding order: pawns, queens and so on
    private final FinalMaterial materialTotal;
    private final long movesOffset; // Same as GameHeader#movesOffset
    private final @NonNull RatingType whiteRatingType;
    private final @NonNull RatingType blackRatingType;
    private final int unknown1; // Quite often set, no idea for what. Probably something encoded.
    private final int unknown2; // Quite often set, no idea for what. Probably something encoded.
    private final int gameVersion;
    private final long creationTimestamp; // 1/1024th seconds since December 1st 2008
    private final @NonNull Endgame endgame;
    private final long lastChangedTimestamp;

    /**
     * Gets the date and time when this game was first created.
     * @return a date and time in the default TimeZone
     */
    public Calendar getCreationTime() {
        // Creation time is based on a ChessBase specific date
        Calendar creationTime = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"));
        creationTime.set(2008, 11, 1, 0, 0, 0);
        long seconds = creationTimestamp / 1024;
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
        long seconds = lastChangedTimestamp / 10000000L;
        lastChangedTime.add(Calendar.MINUTE, (int) (seconds / 60));
        lastChangedTime.add(Calendar.SECOND, (int) (seconds % 60));
        lastChangedTime.setTimeZone(TimeZone.getDefault());
        return lastChangedTime;
    }
}
