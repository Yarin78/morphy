package se.yarin.cbhlib.games.search;

import lombok.NonNull;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.Date;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateRangeFilter implements SearchFilter {

    private static final Pattern dateRangePattern = Pattern.compile("^(([0-9]{4})(-([0-9]{2})(-([0-9]{2}))?)?)?-(([0-9]{4})(-([0-9]{2})(-([0-9]{2}))?)?)?$");

    private final Database db;
    private final @NonNull Date fromDate;
    private final @NonNull Date toDate;

    public DateRangeFilter(Database db, String dateRange) {
        this.db = db;
        Matcher matcher = dateRangePattern.matcher(dateRange);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid date range specified: " + dateRange);
        }

        this.fromDate = extractDate(matcher.group(2), matcher.group(4), matcher.group(6));
        this.toDate = extractDate(matcher.group(8), matcher.group(10), matcher.group(12));
    }

    private static Date extractDate(String year, String month, String day) {
        if (month == null) {
            return new Date(Integer.parseInt(year));
        }
        if (day == null) {
            return new Date(Integer.parseInt(year), Integer.parseInt(month));
        }
        return new Date(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
    }

    public DateRangeFilter(Database db, Date fromDate, Date toDate) {
        this.db = db;
        this.fromDate = fromDate == null ? Date.unset() : fromDate;
        this.toDate = toDate == null ? Date.unset() : toDate;
    }

    @Override
    public Database getDatabase() {
        return this.db;
    }

    @Override
    public void initSearch() throws IOException {
    }

    @Override
    public int countEstimate() {
        return SearchFilter.UNKNOWN_COUNT_ESTIMATE;
    }

    @Override
    public int firstGameId() {
        return 1;
    }

    @Override
    public boolean matches(GameHeader gameHeader) throws IOException {
        if (!fromDate.isUnset() && fromDate.compareTo(gameHeader.getPlayedDate()) > 0) {
            return false;
        }
        if (!toDate.isUnset() && toDate.compareTo(gameHeader.getPlayedDate()) < 0) {
            return false;
        }
        return true;
    }
}
