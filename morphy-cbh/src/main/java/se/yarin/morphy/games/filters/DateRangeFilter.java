package se.yarin.morphy.games.filters;

import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.chess.Date;
import se.yarin.morphy.games.GameHeader;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateRangeFilter extends IsGameFilter {
    private static final Pattern dateRangePattern = Pattern.compile("^(([0-9]{4})(-([0-9]{2})(-([0-9]{2}))?)?)?-(([0-9]{4})(-([0-9]{2})(-([0-9]{2}))?)?)?$");

    @Getter
    private final @NonNull Date fromDate;
    @Getter
    private final @NonNull Date toDate;

    public DateRangeFilter(@NotNull String dateRange) {
        this.fromDate = parseFromDate(dateRange);
        this.toDate = parseToDate(dateRange);
    }

    public static Date parseFromDate(@NotNull String dateRange) {
        Matcher matcher = dateRangePattern.matcher(dateRange);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid date range specified: " + dateRange);
        }

        return extractDate(matcher.group(2), matcher.group(4), matcher.group(6));
    }

    public static Date parseToDate(@NotNull String dateRange) {
        Matcher matcher = dateRangePattern.matcher(dateRange);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid date range specified: " + dateRange);
        }

        return extractDate(matcher.group(8), matcher.group(10), matcher.group(12));
    }

    private static Date extractDate(@Nullable String year, @Nullable String month, @Nullable String day) {
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

    public DateRangeFilter(@Nullable Date fromDate, @Nullable Date toDate) {
        this.fromDate = fromDate == null ? Date.unset() : fromDate;
        this.toDate = toDate == null ? Date.unset() : toDate;
    }

    @Override
    public boolean matches(@NotNull GameHeader gameHeader) {
        return super.matches(gameHeader) && matches(gameHeader.playedDate());
    }

    @Override
    public boolean matchesSerialized(@NotNull ByteBuffer buf) {
        return super.matchesSerialized(buf) && matches(CBUtil.decodeDate(ByteBufferUtil.getUnsigned24BitB(buf, 24)));
    }

    public boolean matches(@NotNull Date playedDate) {
        if (!fromDate.isUnset() && fromDate.compareTo(playedDate) > 0) {
            return false;
        }
        if (!toDate.isUnset() && toDate.compareTo(playedDate) < 0) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        String fromDateStr = fromDate.isUnset() ? null :  ("fromDate >= '" + fromDate + "'");
        String toDateStr = toDate.isUnset() ? null :  ("toDate <= '" + toDate + "'");
        if (fromDateStr == null && toDateStr == null) {
            return "true";
        } else if (fromDateStr != null && toDateStr != null) {
            return fromDateStr + " and " + toDateStr;
        } else if (fromDateStr != null) {
            return fromDateStr;
        } else {
            return toDateStr;
        }
    }
}
