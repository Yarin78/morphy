package se.yarin.morphy.entities.filters;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityType;

public class EntityCountFilter<T extends Entity> implements EntityFilter<T> {
  private final @NotNull EntityType entityType;
  private final int min;
  private final int max;

  public EntityCountFilter(@NotNull EntityType entityType, int min, int max) {
    this.entityType = entityType;
    this.min = min;
    this.max = max;
  }

  @Override
  public boolean matches(@NotNull T entity) {
    return entity.count() >= min && entity.count() <= max;
  }

  @Override
  public String toString() {
    if (min == 0) {
      return "count <= " + max;
    } else if (max == Integer.MAX_VALUE) {
      return "count >= " + min;
    } else if (min == max) {
      return "count = " + min;
    } else {
      return "count >= " + min + " and count <= " + max;
    }
  }

  @Override
  public EntityType entityType() {
    return entityType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EntityCountFilter<?> that = (EntityCountFilter<?>) o;
    return min == that.min && max == that.max && entityType == that.entityType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(entityType, min, max);
  }
}
