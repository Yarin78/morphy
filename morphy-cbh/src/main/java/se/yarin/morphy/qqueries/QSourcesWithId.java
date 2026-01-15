package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Source;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QSourcesWithId extends ItemQuery<Source> {
  private final @NotNull Set<Source> sources;
  private final @NotNull Set<Integer> sourceIds;

  public QSourcesWithId(@NotNull Collection<Source> sources) {
    this.sources = new HashSet<>(sources);
    this.sourceIds =
        sources.stream().map(Source::id).collect(Collectors.toCollection(HashSet::new));
  }

  @Override
  public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Source source) {
    return sourceIds.contains(source.id());
  }

  @Override
  public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
    return sources.size();
  }

  @Override
  public @NotNull Stream<Source> stream(@NotNull DatabaseReadTransaction txn) {
    return sources.stream();
  }
}
