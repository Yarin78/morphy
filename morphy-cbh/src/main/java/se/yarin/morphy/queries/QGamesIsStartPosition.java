package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;

import java.util.stream.Stream;

public class QGamesIsStartPosition extends ItemQuery<Game> {
    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Game game) {
        return !game.getModel().moves().isSetupPosition();
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        return INFINITE;
    }

    @Override
    public @NotNull Stream<Game> stream(@NotNull DatabaseReadTransaction txn) {
        return txn.stream().filter(game -> !game.guidingText() && matches(txn, game));
    }
}
