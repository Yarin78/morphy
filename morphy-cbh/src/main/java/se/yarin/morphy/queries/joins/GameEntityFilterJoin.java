package se.yarin.morphy.queries.joins;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.filters.EntityFilter;

public abstract class GameEntityFilterJoin<T extends Entity & Comparable<T>> {
  private @NotNull final EntityFilter<T> entityFilter;

  public @NotNull EntityFilter<T> getEntityFilter() {
    return entityFilter;
  }

  public GameEntityFilterJoin(@NotNull EntityFilter<T> entityFilter) {
    this.entityFilter = entityFilter;
  }

  public abstract boolean gameFilter(
      @NotNull Game game, @NotNull EntityIndexReadTransaction<T> txn);
}
