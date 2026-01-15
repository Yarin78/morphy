package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseContext;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.boosters.GameEntityIndex;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityIndex;
import se.yarin.morphy.entities.EntityType;

public class QueryContext {
  private final @NotNull DatabaseReadTransaction txn;
  private final boolean traceCost;

  public QueryContext(@NotNull DatabaseReadTransaction txn, boolean traceCost) {
    this.txn = txn;
    this.traceCost = traceCost;
  }

  public @NotNull DatabaseReadTransaction transaction() {
    return txn;
  }

  public @NotNull DatabaseContext databaseContext() {
    return txn.database().context();
  }

  public <T extends Entity & Comparable<T>> @NotNull EntityIndex<T> entityIndex(
      @NotNull EntityType entityType) {
    return (EntityIndex<T>) txn.database().entityIndex(entityType);
  }

  public GameEntityIndex gameEntityIndex(@NotNull EntityType entityType) {
    return txn.database().gameEntityIndex(entityType);
  }

  public @NotNull Database database() {
    return txn.database();
  }

  public @NotNull QueryPlanner queryPlanner() {
    return database().queryPlanner();
  }

  public boolean traceCost() {
    return traceCost;
  }
}
