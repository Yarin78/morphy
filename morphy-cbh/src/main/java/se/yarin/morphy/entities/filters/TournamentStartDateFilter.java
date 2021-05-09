package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Date;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.games.filters.DateRangeFilter;
import se.yarin.morphy.storage.ItemStorageFilter;

public class TournamentStartDateFilter implements ItemStorageFilter<Tournament> {
    private final Date fromDate;
    private final Date toDate;

    public TournamentStartDateFilter(@NotNull String dateRange) {
        this(DateRangeFilter.parseFromDate(dateRange), DateRangeFilter.parseToDate(dateRange));
    }

    public TournamentStartDateFilter(@NotNull Date fromDate, @NotNull Date toDate) {
        this.fromDate = fromDate;
        this.toDate = toDate;
    }

    @Override
    public boolean matches(@NotNull Tournament tournament) {
        return (fromDate.isUnset() || fromDate.compareTo(tournament.date()) <= 0) &&
                (toDate.isUnset() || toDate.compareTo(tournament.date()) >= 0);
    }
}
