package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Date;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.games.filters.DateRangeFilter;
import se.yarin.morphy.util.CBUtil;
import se.yarin.util.ByteBufferUtil;

public class TournamentStartDateFilter implements EntityFilter<Tournament>  {
    private final Date fromDate;
    private final Date toDate;

    public TournamentStartDateFilter(@NotNull String dateRange) {
        this(DateRangeFilter.parseFromDate(dateRange), DateRangeFilter.parseToDate(dateRange));
    }

    public TournamentStartDateFilter(@NotNull Date fromDate, @NotNull Date toDate) {
        this.fromDate = fromDate;
        this.toDate = toDate;
    }

    private boolean matches(@NotNull Date date) {
        return (fromDate.isUnset() || fromDate.compareTo(date) <= 0) &&
                (toDate.isUnset() || toDate.compareTo(date) >= 0);
    }

    @Override
    public boolean matches(@NotNull Tournament tournament) {
        return matches(tournament.date());
    }

    @Override
    public boolean matchesSerialized(byte[] serializedItem) {
        return matches(CBUtil.decodeDate(ByteBufferUtil.getIntL(serializedItem, 70)));
    }
}
