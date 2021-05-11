package se.yarin.morphy.storage;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.morphy.DatabaseContext;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static java.nio.file.StandardOpenOption.*;
import static org.junit.Assert.*;
import static se.yarin.morphy.storage.MorphyOpenOption.*;

public class FileItemStorageTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final DatabaseContext context = new DatabaseContext();

    private File storageFile() throws IOException {
        File file = folder.newFile();
        file.delete();
        return file;
    }

    private File initStorage() throws IOException {
        File file = storageFile();
        FileItemStorage<FooBarItemHeader, FooBarItem> storage = new FileItemStorage<>(file, context, new FooBarItemSerializer(), FooBarItemHeader.empty(), Set.of(READ, WRITE, CREATE_NEW));

        assertTrue(storage.isEmpty());
        storage.putItem(0, ImmutableFooBarItem.of("hello", 5));
        storage.putItem(1, ImmutableFooBarItem.of("world", 3));
        storage.putItem(2, ImmutableFooBarItem.of("next", 8));
        storage.putHeader(ImmutableFooBarItemHeader.of(1, 3));
        return file;
    }

    @Test
    public void updateHeader() throws IOException {
        File file = storageFile();
        FileItemStorage<FooBarItemHeader, FooBarItem> storage = new FileItemStorage<>(file, context, new FooBarItemSerializer(), FooBarItemHeader.empty(), Set.of(READ, WRITE, CREATE_NEW));

        assertEquals(FooBarItemHeader.empty(), storage.getHeader());
        storage.putHeader(ImmutableFooBarItemHeader.of(9, 13));
        storage.close();

        assertTrue(file.exists());
        assertEquals(8, file.length());

        // The header is only parsed when opening the storage
        storage = new FileItemStorage<>(file, context, new FooBarItemSerializer(), FooBarItemHeader.empty(), Set.of(READ));
        assertEquals(ImmutableFooBarItemHeader.of(9, 13), storage.getHeader());
    }

    @Test
    public void getItem() throws IOException {
        FileItemStorage<FooBarItemHeader, FooBarItem> storage = new FileItemStorage<>(initStorage(), context, new FooBarItemSerializer(), FooBarItemHeader.empty(), Set.of(READ));

        assertEquals(ImmutableFooBarItem.of("world", 3), storage.getItem(1));
    }

    @Test
    public void putItem() throws IOException {
        File file = storageFile();
        FileItemStorage<FooBarItemHeader, FooBarItem> storage = new FileItemStorage<>(file, context, new FooBarItemSerializer(), FooBarItemHeader.empty(), Set.of(READ, WRITE, CREATE_NEW));

        assertTrue(storage.isEmpty());
        storage.putItem(0, ImmutableFooBarItem.of("foobar", 73));
        storage.close();

        assertEquals(42, file.length());
        storage = new FileItemStorage<>(file, context, new FooBarItemSerializer(), FooBarItemHeader.empty(), Set.of(READ));
        assertFalse(storage.isEmpty());
        assertEquals(ImmutableFooBarItem.of("foobar", 73), storage.getItem(0));
    }

    @Test
    public void getItems() throws IOException {
        FileItemStorage<FooBarItemHeader, FooBarItem> storage = new FileItemStorage<>(initStorage(), context, new FooBarItemSerializer(), FooBarItemHeader.empty(), Set.of(READ));

        List<FooBarItem> items = storage.getItems(1, 2);
        assertEquals(2, items.size());
        assertEquals(ImmutableFooBarItem.of("world", 3), items.get(0));
        assertEquals(ImmutableFooBarItem.of("next", 8), items.get(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getItemsOutsideRange() throws IOException {
        FileItemStorage<FooBarItemHeader, FooBarItem> storage = new FileItemStorage<>(initStorage(), context, new FooBarItemSerializer(), FooBarItemHeader.empty(), Set.of(READ));

        storage.getItems(1, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getItemAfterLast() throws IOException {
        FileItemStorage<FooBarItemHeader, FooBarItem> storage = new FileItemStorage<>(initStorage(), context, new FooBarItemSerializer(), FooBarItemHeader.empty(), Set.of(READ));

        storage.getItem(5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getItemBeforeFirst() throws IOException {
        FileItemStorage<FooBarItemHeader, FooBarItem> storage = new FileItemStorage<>(initStorage(), context, new FooBarItemSerializer(), FooBarItemHeader.empty(), Set.of(READ));

        storage.getItem(-1);
    }

    @Test
    public void getItemsOutsideRangeLaxMode() throws IOException {
        FileItemStorage<FooBarItemHeader, FooBarItem> storage = new FileItemStorage<>(initStorage(), context, new FooBarItemSerializer(), FooBarItemHeader.empty(), Set.of(READ, IGNORE_NON_CRITICAL_ERRORS));

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
    public void getItemAfterLastLaxMode() throws IOException {
        FileItemStorage<FooBarItemHeader, FooBarItem> storage = new FileItemStorage<>(initStorage(), context, new FooBarItemSerializer(), FooBarItemHeader.empty(), Set.of(READ, IGNORE_NON_CRITICAL_ERRORS));

        assertEquals(FooBarItem.empty(), storage.getItem(5));
    }

    @Test
    public void getItemBeforeFirstLaxMode() throws IOException {
        FileItemStorage<FooBarItemHeader, FooBarItem> storage = new FileItemStorage<>(initStorage(), context, new FooBarItemSerializer(), FooBarItemHeader.empty(), Set.of(READ, IGNORE_NON_CRITICAL_ERRORS));

        assertEquals(FooBarItem.empty(), storage.getItem(-1));
    }
}
