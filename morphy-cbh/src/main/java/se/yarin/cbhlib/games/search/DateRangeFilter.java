package se.yarin.cbhlib.games.search;

import lombok.Getter;
import lombok.NonNull;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.SerializedGameHeaderFilter;
import se.yarin.cbhlib.util.ByteBufferUtil;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.chess.Date;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateRangeFilter extends SearchFilterBase implements SearchFilter, SerializedGameHeaderFilter {

    private static final Pattern dateRangePattern = Pattern.compile("^(([0-9]{4})(-([0-9]{2})(-([0-9]{2}))?)?)?-(([0-9]{4})(-([0-9]{2})(-([0-9]{2}))?)?)?$");

    @Getter
    private final @NonNull Date fromDate;
    @Getter
    private final @NonNull Date toDate;

    public DateRangeFilter(Database db, String dateRange) {
        super(db);
        Matcher matcher = dateRangePattern.matcher(dateRange);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid date range specified: " + dateRange);
        }

        this.fromDate = extractDate(matcher.group(2), matcher.group(4), matcher.group(6));
        this.toDate = extractDate(matcher.group(8), matcher.group(10), matcher.group(12));
    }

    private static Date extractDate(String year, String month, String day) {
        if (year == null) {
            return Date.unset();
        }
        if (month == null) {
            return new Date(Integer.parseInt(year));
        }
        if (day == null) {
            return new Date(Integer.parseInt(year), Integer.parseInt(month));
        }
        return new Date(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
    }

    public DateRangeFilter(Database db, Date fromDate, Date toDate) {
        super(db);
        this.fromDate = fromDate == null ? Date.unset() : fromDate;
        this.toDate = toDate == null ? Date.unset() : toDate;
    }

    @Override
    public boolean matches(GameHeader gameHeader) {
        return matches(gameHeader.getPlayedDate());
    }

    public boolean matches(Date playedDate) {
        if (!fromDate.isUnset() && fromDate.compareTo(playedDate) > 0) {
            return false;
        }
        if (!toDate.isUnset() && toDate.compareTo(playedDate) < 0) {
            return false;
        }
        return true;
    }

    @Override
    public boolean matches(byte[] serializedGameHeader) {
        if ((serializedGameHeader[0] & 2) > 0) {
            // Guiding text has no dates
            return false;
        }
        return matches(CBUtil.decodeDate(ByteBufferUtil.getUnsigned24BitB(serializedGameHeader, 24)));
    }
}
