package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Source;
import se.yarin.morphy.entities.filters.SourceTitleFilter;

import java.util.stream.Stream;

public class QSourcesWithTitle extends ItemQuery<Source> {
    private final @NotNull SourceTitleFilter filter;

    public QSourcesWithTitle(@NotNull SourceTitleFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Source source) {
        return filter.matches(source);
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        // TODO: if case sensitive, we can iterate alphabetically in the index and know if there are few matching
        return INFINITE;
    }

    @Override
    public @NotNull Stream<Source> stream(@NotNull DatabaseReadTransaction txn) {
        // TODO: Serialization stream
        return txn.sourceTransaction().stream().filter(source -> matches(txn, source));
    }
}
