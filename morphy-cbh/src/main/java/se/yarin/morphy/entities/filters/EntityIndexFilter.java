package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.Nullable;

public interface EntityIndexFilter<T> extends EntityFilter<T> {
    @Nullable T start();

    @Nullable T end();
}
