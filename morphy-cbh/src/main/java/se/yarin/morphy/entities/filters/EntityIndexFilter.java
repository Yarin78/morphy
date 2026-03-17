package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.IdObject;

public interface EntityIndexFilter<T extends IdObject> extends EntityFilter<T> {
  @Nullable
  T start();

  @Nullable
  T end();
}
