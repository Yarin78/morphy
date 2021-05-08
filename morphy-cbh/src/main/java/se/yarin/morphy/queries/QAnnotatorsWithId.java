package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Annotator;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QAnnotatorsWithId extends ItemQuery<Annotator> {
    private final @NotNull Set<Annotator> annotators;
    private final @NotNull Set<Integer> annotatorIds;

    public QAnnotatorsWithId(@NotNull Collection<Annotator> annotators) {
        this.annotators = new HashSet<>(annotators);
        this.annotatorIds = annotators.stream().map(Annotator::id).collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Annotator annotator) {
        return annotatorIds.contains(annotator.id());
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        return annotators.size();
    }

    @Override
    public @NotNull Stream<Annotator> stream(@NotNull DatabaseReadTransaction txn) {
        return annotators.stream();
    }
}
