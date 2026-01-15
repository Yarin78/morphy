package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Annotator;
import se.yarin.morphy.entities.filters.AnnotatorNameFilter;

import java.util.stream.Stream;

public class QAnnotatorsWithName extends ItemQuery<Annotator> {
  private final @NotNull AnnotatorNameFilter filter;

  public QAnnotatorsWithName(@NotNull AnnotatorNameFilter filter) {
    this.filter = filter;
  }

  @Override
  public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Annotator annotator) {
    return filter.matches(annotator);
  }

  @Override
  public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
    // TODO: if case sensitive, we can iterate alphabetically in the index and know if there are few
    // matching
    return INFINITE;
  }

  @Override
  public @NotNull Stream<Annotator> stream(@NotNull DatabaseReadTransaction txn) {
    // TODO: Serialization stream
    return txn.annotatorTransaction().stream().filter(annotator -> matches(txn, annotator));
  }
}
