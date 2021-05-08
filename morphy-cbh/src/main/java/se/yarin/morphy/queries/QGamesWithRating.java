package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.games.filters.RatingRangeFilter;

import java.util.stream.Stream;

public class QGamesWithRating extends ItemQuery<Game> {
    private final @NotNull RatingRangeFilter ratingRangeFilter;

    public QGamesWithRating(int minRating, int maxRating, @NotNull RatingRangeFilter.RatingColor color) {
        this.ratingRangeFilter = new RatingRangeFilter(minRating, maxRating, color);
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Game game) {
        return this.ratingRangeFilter.matches(game.header());
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        return INFINITE;
    }

    @Override
    public @NotNull Stream<Game> stream(@NotNull DatabaseReadTransaction txn) {
        return txn.stream(GameFilter.of(ratingRangeFilter, null));
    }
}
