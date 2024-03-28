package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Date;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.filters.DateRangeFilter;
import se.yarin.morphy.games.filters.GameFilter;

import java.util.stream.Stream;

public class QGamesWithPlayedDate extends ItemQuery<Game> {
    private final @NotNull DateRangeFilter filter;

    public QGamesWithPlayedDate(@NotNull String dateRange) {
        this(new DateRangeFilter(dateRange));
    }

    public QGamesWithPlayedDate(@Nullable Date fromDate, @Nullable Date toDate) {
        this(new DateRangeFilter(fromDate, toDate));
    }

    public QGamesWithPlayedDate(@NotNull DateRangeFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Game game) {
        return filter.matches(game.header());
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        return INFINITE;
    }

    @Override
    public @NotNull Stream<Game> stream(@NotNull DatabaseReadTransaction txn) {
        return txn.stream(GameFilter.of(filter, null));
    }
}
