package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.games.filters.RawExtendedHeaderFilter;
import se.yarin.morphy.games.filters.RawGameHeaderFilter;

import java.util.stream.Stream;

public class QGamesWithRaw extends ItemQuery<Game> {
    private final @Nullable RawGameHeaderFilter headerFilter;
    private final @Nullable RawExtendedHeaderFilter extendedHeaderFilter;

    public QGamesWithRaw(@Nullable RawGameHeaderFilter headerFilter, @Nullable RawExtendedHeaderFilter extendedHeaderFilter) {
        this.headerFilter = headerFilter;
        this.extendedHeaderFilter = extendedHeaderFilter;
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Game game) {
        // This actually matches everything
        // TODO: Force match serialized?
        return (headerFilter == null || headerFilter.matches(game.header())) &&
                (extendedHeaderFilter == null || extendedHeaderFilter.matches(game.extendedHeader()));
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        return INFINITE;
    }

    @Override
    public @NotNull Stream<Game> stream(@NotNull DatabaseReadTransaction txn) {
        return txn.stream(GameFilter.of(headerFilter, extendedHeaderFilter));
    }
}
