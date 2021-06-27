package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Date;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.games.filters.DateRangeFilter;
import se.yarin.morphy.queries.QueryPlanner;
import se.yarin.morphy.util.CBUtil;
import se.yarin.util.ByteBufferUtil;

public class TournamentStartDateFilter implements EntityFilter<Tournament>  {
    private final @NotNull Date fromDate;
    private final @NotNull Date toDate;

    public @NotNull Date fromDate() {
        return fromDate;
    }

    public @NotNull Date toDate() {
        return toDate;
    }

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

    @Override
    public double expectedMatch(@NotNull QueryPlanner planner) {
        return planner.tournamentYearDistribution().ratioBetween(fromDate.year(), toDate.isUnset() ? 3000 : toDate.year());
    }

    @Override
    public EntityType entityType() {
        return EntityType.TOURNAMENT;
    }
}
