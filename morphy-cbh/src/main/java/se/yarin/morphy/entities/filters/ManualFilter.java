package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.entities.EntityType;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ManualFilter<T extends IdObject> implements EntityFilter<T> {
  private final @NotNull EntityType entityType;

  private final @NotNull Set<Integer> ids;

  public ManualFilter(@NotNull List<T> items, @NotNull EntityType entityType) {
    this.ids = items.stream().map(IdObject::id).collect(Collectors.toUnmodifiableSet());
    this.entityType = entityType;
  }

  public ManualFilter(@NotNull int[] ids, @NotNull EntityType entityType) {
    this.ids = Arrays.stream(ids).boxed().collect(Collectors.toUnmodifiableSet());
    this.entityType = entityType;
  }

  public Set<Integer> ids() {
    return ids;
  }

  public int minId() {
    return ids.stream().mapToInt(Integer::intValue).min().orElse(0);
  }

  public int maxId() {
    return ids.stream().mapToInt(Integer::intValue).max().orElse(Integer.MAX_VALUE);
  }

  @Override
  public boolean matches(@NotNull T item) {
    return ids.contains(item.id());
  }

  @Override
  public EntityType entityType() {
    return this.entityType;
  }

  @Override
  public String toString() {
    String commaList = ids.stream().map(Object::toString).collect(Collectors.joining(", "));
    return "id IN (" + commaList + ")";
  }
}
