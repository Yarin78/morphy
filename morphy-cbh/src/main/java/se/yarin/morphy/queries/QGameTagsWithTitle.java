package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.GameTag;
import se.yarin.morphy.entities.filters.GameTagTitleFilter;

import java.util.stream.Stream;

public class QGameTagsWithTitle extends ItemQuery<GameTag> {
    private final @NotNull GameTagTitleFilter filter;

    public QGameTagsWithTitle(@NotNull GameTagTitleFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull GameTag gameTag) {
        return filter.matches(gameTag);
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        // TODO: if case sensitive, we can iterate alphabetically in the index and know if there are few matching
        return INFINITE;
    }

    @Override
    public @NotNull Stream<GameTag> stream(@NotNull DatabaseReadTransaction txn) {
        // TODO: Serialization stream
        return txn.gameTagTransaction().stream().filter(gameTag -> matches(txn, gameTag));
    }
}
