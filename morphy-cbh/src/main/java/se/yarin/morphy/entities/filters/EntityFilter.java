package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.queries.QueryPlanner;
import se.yarin.morphy.storage.ItemStorageFilter;

import java.nio.ByteBuffer;

public interface EntityFilter<T extends IdObject> extends ItemStorageFilter<T> {
  EntityType entityType();

  @Override
  default boolean matchesSerialized(@NotNull ByteBuffer buf) {
    return true;
  }

  default double expectedMatch(@NotNull QueryPlanner planner) {
    return 1.0;
  }
}
