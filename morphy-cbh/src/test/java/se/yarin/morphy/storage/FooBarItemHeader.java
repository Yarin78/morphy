package se.yarin.morphy.storage;

import org.immutables.value.Value;

@Value.Immutable
public interface FooBarItemHeader {
    @Value.Parameter
    int version();

    @Value.Parameter
    int numItems();

    static FooBarItemHeader empty() {
        return ImmutableFooBarItemHeader.builder().version(1).numItems(0).build();
    }
}
