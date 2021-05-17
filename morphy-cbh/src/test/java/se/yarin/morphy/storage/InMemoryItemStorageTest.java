package se.yarin.morphy.storage;

import org.junit.Test;

import java.util.List;
import static org.junit.Assert.*;

public class InMemoryItemStorageTest {
    public void initItems(ItemStorage<FooBarItemHeader, FooBarItem> storage) {
        assertTrue(storage.isEmpty());
        storage.putItem(0, ImmutableFooBarItem.of("hello", 5));
        storage.putItem(1, ImmutableFooBarItem.of("world", 3));
        storage.putItem(2, ImmutableFooBarItem.of("next", 8));
        storage.putHeader(ImmutableFooBarItemHeader.of(1, 3));
    }

    @Test
    public void updateHeader() {
        InMemoryItemStorage<FooBarItemHeader, FooBarItem> storage = new InMemoryItemStorage<>(FooBarItemHeader.empty());

        assertEquals(FooBarItemHeader.empty(), storage.getHeader());
        storage.putHeader(ImmutableFooBarItemHeader.of(9, 13));

        assertEquals(ImmutableFooBarItemHeader.of(9, 13), storage.getHeader());
    }

    @Test
    public void getItems() {
        InMemoryItemStorage<FooBarItemHeader, FooBarItem> storage = new InMemoryItemStorage<>(FooBarItemHeader.empty());
        initItems(storage);

        List<FooBarItem> items = storage.getItems(1, 2);
        assertEquals(2, items.size());
        assertEquals(ImmutableFooBarItem.of("world", 3), items.get(0));
        assertEquals(ImmutableFooBarItem.of("next", 8), items.get(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getItemsOutsideRange() {
        InMemoryItemStorage<FooBarItemHeader, FooBarItem> storage = new InMemoryItemStorage<>(FooBarItemHeader.empty());
        initItems(storage);

        storage.getItems(1, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getItemAfterLast() {
        InMemoryItemStorage<FooBarItemHeader, FooBarItem> storage = new InMemoryItemStorage<>(FooBarItemHeader.empty());
        initItems(storage);

        storage.getItem(5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getItemBeforeFirst() {
        InMemoryItemStorage<FooBarItemHeader, FooBarItem> storage = new InMemoryItemStorage<>(FooBarItemHeader.empty());
        initItems(storage);

        storage.getItem(-1);
    }

    @Test
    public void getItemsOutsideRangeWithEmptyItem() {
        InMemoryItemStorage<FooBarItemHeader, FooBarItem> storage = new InMemoryItemStorage<>(FooBarItemHeader.empty(), FooBarItem.empty());
        initItems(storage);

        List<FooBarItem> items = storage.getItems(1, 3);
        assertEquals(3, items.size());
        assertEquals(ImmutableFooBarItem.of("world", 3), items.get(0));
        assertEquals(ImmutableFooBarItem.of("next", 8), items.get(1));
        assertEquals(ImmutableFooBarItem.of("", 0), items.get(2));

        items = storage.getItems(-1, 3);
        assertEquals(3, items.size());
        assertEquals(ImmutableFooBarItem.of("", 0), items.get(0));
        assertEquals(ImmutableFooBarItem.of("hello", 5), items.get(1));
        assertEquals(ImmutableFooBarItem.of("world", 3), items.get(2));
    }

    @Test
    public void getItemAfterLastWithEmptyItem() {
        InMemoryItemStorage<FooBarItemHeader, FooBarItem> storage = new InMemoryItemStorage<>(FooBarItemHeader.empty(), FooBarItem.empty());
        initItems(storage);

        assertEquals(FooBarItem.empty(), storage.getItem(5));
    }

    @Test
    public void getItemBeforeFirstWithEmptyItem() {
        InMemoryItemStorage<FooBarItemHeader, FooBarItem> storage = new InMemoryItemStorage<>(FooBarItemHeader.empty(), FooBarItem.empty());
        initItems(storage);

        assertEquals(FooBarItem.empty(), storage.getItem(-1));
    }

    @Test
    public void getItemsFromOneIndexedStorage() {
        InMemoryItemStorage<FooBarItemHeader, FooBarItem> storage = new InMemoryItemStorage<>(null, null, FooBarItemHeader.empty(), null, true);
        storage.putItem(1, ImmutableFooBarItem.of("hello", 5));
        storage.putItem(2, ImmutableFooBarItem.of("world", 3));
        storage.putItem(3, ImmutableFooBarItem.of("next", 8));

        assertEquals(ImmutableFooBarItem.of("hello", 5), storage.getItem(1));
        assertEquals(ImmutableFooBarItem.of("world", 3), storage.getItem(2));
        assertEquals(ImmutableFooBarItem.of("next", 8), storage.getItem(3));

        List<FooBarItem> items = storage.getItems(1, 3);
        assertEquals(ImmutableFooBarItem.of("hello", 5), items.get(0));
        assertEquals(ImmutableFooBarItem.of("world", 3), items.get(1));
        assertEquals(ImmutableFooBarItem.of("next", 8), items.get(2));
    }

    @Test
    public void getItemsFromOneIndexedStorageOutsideRangeInSafeMode() {
        InMemoryItemStorage<FooBarItemHeader, FooBarItem> storage = new InMemoryItemStorage<>(null, null, FooBarItemHeader.empty(), FooBarItem.empty(), true);
        storage.putItem(1, ImmutableFooBarItem.of("hello", 5));
        storage.putItem(2, ImmutableFooBarItem.of("world", 3));
        storage.putItem(3, ImmutableFooBarItem.of("next", 8));

        List<FooBarItem> items = storage.getItems(0, 5);
        assertEquals(FooBarItem.empty(), items.get(0));
        assertEquals(ImmutableFooBarItem.of("hello", 5), items.get(1));
        assertEquals(ImmutableFooBarItem.of("world", 3), items.get(2));
        assertEquals(ImmutableFooBarItem.of("next", 8), items.get(3));
        assertEquals(FooBarItem.empty(), items.get(4));
    }
}
