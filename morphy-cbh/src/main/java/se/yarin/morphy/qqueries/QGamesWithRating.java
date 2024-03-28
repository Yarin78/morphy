package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.games.filters.RatingRangeFilter;

import java.util.stream.Stream;

public class QGamesWithRating extends ItemQuery<Game> {
    private final @NotNull RatingRangeFilter filter;

    public QGamesWithRating(int minRating, int maxRating, @NotNull RatingRangeFilter.RatingColor color) {
        this(new RatingRangeFilter(minRating, maxRating, color));
    }

    public QGamesWithRating(@NotNull String ratingRange, @NotNull RatingRangeFilter.RatingColor color) {
        this(new RatingRangeFilter(ratingRange, color));
    }

    public QGamesWithRating(@NotNull RatingRangeFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Game game) {
        return this.filter.matches(game.header());
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
