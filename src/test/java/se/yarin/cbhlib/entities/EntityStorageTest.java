package se.yarin.cbhlib.entities;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Random;

import static org.junit.Assert.*;

public class EntityStorageTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Random random;

    private EntityStorage<TestEntity> createStorage() throws IOException, EntityStorageException {
//        File file = folder.newFile();
//        file.delete();
//        return EntityStorageImpl.create(file, new TestEntitySerializer());
        return EntityStorageImpl.createInMemory();
    }

    @Before
    public void setupRandom() {
        random = new Random(0);
    }

    private String nextRandomString() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < sb.capacity(); i++) {
            sb.append((char)('a' + random.nextInt(26)));
        }
        return sb.toString();
    }

    @Test
    public void testCreateStorage() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        assertEquals(0, storage.getNumEntities());
        storage.validateStructure();
    }

    @Test
    public void testAddEntity() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        TestEntity hello = new TestEntity(0, "hello");
        hello.setValue(7);
        int id = storage.addEntity(hello);
        assertEquals(1, storage.getNumEntities());

        TestEntity entity = storage.getEntity(id);
        assertEquals("hello", entity.getKey());
        assertEquals(0, entity.getId());
        assertEquals(7, entity.getValue());

        storage.validateStructure();
    }

    @Test
    public void testGetEntityByKey() throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = createStorage();
        TestEntity hello = new TestEntity(0, "hello");
        hello.setValue(7);
        storage.addEntity(hello);

        TestEntity entity = storage.getEntity(new TestEntity("hello"));
        assertEquals("hello", entity.getKey());
        assertEquals(0, entity.getId());
        assertEquals(7, entity.getValue());

        storage.validateStructure();
    }

    @Test
    public void testAddMultipleEntities() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        for (int i = 0; i < 20; i++) {
            int id = storage.addEntity(new TestEntity(i, nextRandomString()));
            assertEquals(i, id);
            assertEquals(i + 1, storage.getNumEntities());
            storage.validateStructure();
        }
    }

    @Test
    public void testDeleteEntity() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        int id1 = storage.addEntity(new TestEntity(0, "hello"));
        int id2 = storage.addEntity(new TestEntity(1, "world"));

        assertTrue(storage.deleteEntity(id1));

        assertEquals(1, storage.getNumEntities());
        assertNull(storage.getEntity(id1));
        assertNotNull(storage.getEntity(id2));

        storage.validateStructure();
    }

    @Test
    public void testDeleteDeletedEntity() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        int id1 = storage.addEntity(new TestEntity(0, "hello"));
        assertTrue(storage.deleteEntity(id1));
        assertEquals(0, storage.getNumEntities());
        assertFalse(storage.deleteEntity(id1));
        assertEquals(0, storage.getNumEntities());

        storage.validateStructure();
    }

    @Test
    public void testDeleteNodeWithTwoChildren() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity(0, "B"));
        storage.addEntity(new TestEntity(1, "A"));
        storage.addEntity(new TestEntity(2, "D"));
        storage.addEntity(new TestEntity(3, "C"));
        storage.addEntity(new TestEntity(4, "F"));
        storage.addEntity(new TestEntity(5, "E"));
        storage.addEntity(new TestEntity(6, "G"));

        storage.deleteEntity(2);
        assertNull(storage.getEntity(2));
        assertEquals("B", storage.getEntity(0).getKey());
        assertEquals("F", storage.getEntity(4).getKey());
        assertEquals("E", storage.getEntity(5).getKey());
        storage.validateStructure();
    }

    @Test
    public void testDeleteNodeWithTwoChildren2() throws IOException, EntityStorageException {
        // Special case when the code swaps two nodes that have parent-child relation
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity(0, "B"));
        storage.addEntity(new TestEntity(1, "A"));
        storage.addEntity(new TestEntity(2, "D"));
        storage.addEntity(new TestEntity(3, "C"));
        storage.addEntity(new TestEntity(4, "E"));
        storage.addEntity(new TestEntity(5, "F"));

        storage.deleteEntity(2);
        assertNull(storage.getEntity(2));
        assertEquals("B", storage.getEntity(0).getKey());
        assertEquals("C", storage.getEntity(3).getKey());
        assertEquals("E", storage.getEntity(4).getKey());
        assertEquals("F", storage.getEntity(5).getKey());
        storage.validateStructure();
    }

    @Test
    public void testAddMultipleEntitiesAndThenDeleteThem() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        int count = 500;
        for (int i = 0; i < count; i++) {
            storage.addEntity(new TestEntity(i, nextRandomString()));
        }
        for (int i = 0; i < count; i++) {
            storage.deleteEntity(i);
            assertEquals(count - i - 1, storage.getNumEntities());
            storage.validateStructure();
        }
    }

    @Test
    public void testReplaceNodeWithSameKey() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity(0, "b"));
        storage.addEntity(new TestEntity(1, "a"));
        storage.addEntity(new TestEntity(2, "c"));

        storage.putEntity(0, new TestEntity(0, "b"));
        assertEquals(3, storage.getNumEntities());
        storage.validateStructure();
    }

    @Test
    public void testReplaceNodeWithNewKey() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity(0, "b"));
        storage.addEntity(new TestEntity(1, "a"));
        storage.addEntity(new TestEntity(2, "c"));

        storage.putEntity(0, new TestEntity(0, "d"));
        assertEquals(3, storage.getNumEntities());
        assertEquals("d", storage.getEntity(0).getKey());
        storage.validateStructure();
    }

    @Test
    public void testOrderedAscendingIterator() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        String[] values = {"d", "t", "b", "e", "q", "w", "a", "l", "c", "v"};
        for (int i = 0; i < values.length; i++) {
            storage.addEntity(new TestEntity(i, values[i]));
        }

        Iterator<TestEntity> iterator = storage.getOrderedAscendingIterator(null);
        StringBuilder sb = new StringBuilder();
        while (iterator.hasNext()) {
            sb.append(iterator.next().getKey());
        }
        assertEquals("abcdelqtvw", sb.toString());

        iterator = storage.getOrderedAscendingIterator(new TestEntity("f"));
        sb = new StringBuilder();
        while (iterator.hasNext()) {
            sb.append(iterator.next().getKey());
        }
        assertEquals("lqtvw", sb.toString());

        iterator = storage.getOrderedAscendingIterator(new TestEntity("q"));
        sb = new StringBuilder();
        while (iterator.hasNext()) {
            sb.append(iterator.next().getKey());
        }
        assertEquals("qtvw", sb.toString());
    }

    @Test
    public void testAscendingIterateOverEmptyStorage() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();

        Iterator<TestEntity> iterator = storage.getOrderedAscendingIterator(null);
        assertFalse(iterator.hasNext());

        iterator = storage.getOrderedAscendingIterator(new TestEntity("a"));
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testAscendingIterateOverSingleNodeStorage() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity(0, "e"));

        // No start marker
        Iterator<TestEntity> iterator = storage.getOrderedAscendingIterator(null);
        assertTrue(iterator.hasNext());
        assertEquals("e", iterator.next().getKey());
        assertFalse(iterator.hasNext());

        // Start before first
        iterator = storage.getOrderedAscendingIterator(new TestEntity("b"));
        assertTrue(iterator.hasNext());
        assertEquals("e", iterator.next().getKey());
        assertFalse(iterator.hasNext());

        // Start same as first
        iterator = storage.getOrderedAscendingIterator(new TestEntity("e"));
        assertTrue(iterator.hasNext());
        assertEquals("e", iterator.next().getKey());
        assertFalse(iterator.hasNext());

        // Start after as first
        iterator = storage.getOrderedAscendingIterator(new TestEntity("g"));
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testOrderedDescendingIterator() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        String[] values = {"d", "t", "b", "e", "q", "w", "a", "l", "c", "v"};
        for (int i = 0; i < values.length; i++) {
            storage.addEntity(new TestEntity(i, values[i]));
        }

        Iterator<TestEntity> iterator = storage.getOrderedDescendingIterator(null);
        StringBuilder sb = new StringBuilder();
        while (iterator.hasNext()) {
            sb.append(iterator.next().getKey());
        }
        assertEquals("wvtqledcba", sb.toString());

        iterator = storage.getOrderedDescendingIterator(new TestEntity("f"));
        sb = new StringBuilder();
        while (iterator.hasNext()) {
            sb.append(iterator.next().getKey());
        }
        assertEquals("edcba", sb.toString());

        iterator = storage.getOrderedDescendingIterator(new TestEntity("q"));
        sb = new StringBuilder();
        while (iterator.hasNext()) {
            sb.append(iterator.next().getKey());
        }
        assertEquals("qledcba", sb.toString());
    }

    @Test
    public void testIteratorOverEmptyStorage() throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = createStorage();
        int count = 0;
        for (TestEntity ignored : storage) {
            count++;
        }
        assertEquals(0, count);

        storage.addEntity(new TestEntity(0, "hello"));
        storage.deleteEntity(0);

        count = 0;
        for (TestEntity ignored : storage) {
            count++;
        }
        assertEquals(0, count);
    }

    @Test
    public void testIterator() throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = createStorage();
        String[] values = {"d", "t", "b", "e", "q", "w", "a", "l", "c", "v"};
        for (int i = 0; i < values.length; i++) {
            storage.addEntity(new TestEntity(i, values[i]));
        }
        storage.deleteEntity(3);
        storage.deleteEntity(8);

        StringBuilder sb = new StringBuilder();
        for (TestEntity testEntity : storage) {
            sb.append(testEntity.getKey());
        }
        assertEquals("dtbqwalv", sb.toString());
    }

    @Test
    public void testIteratorOverMultipleBatches() throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = createStorage();
        int expected = 3876;
        for (int i = 0; i < expected; i++) {
            storage.addEntity(new TestEntity(i, nextRandomString()));
        }

        int count = 0;
        for (TestEntity ignored : storage) {
            count++;
        }
        assertEquals(expected, count);
    }

    @Test
    public void testOpenInMemory() throws IOException, EntityStorageException {
        File file = folder.newFile();
        file.delete();
        EntityStorage<TestEntity> diskStorage = EntityStorageImpl.create(file, new TestEntitySerializer());
        String[] values = {"d", "t", "b", "e", "q", "w", "a", "l", "c", "v"};
        for (int i = 0; i < values.length; i++) {
            diskStorage.addEntity(new TestEntity(i, values[i]));
        }
        diskStorage.deleteEntity(3);
        diskStorage.deleteEntity(8);
        diskStorage.close();

        EntityStorage<TestEntity> storage = EntityStorageImpl.openInMemory(file, new TestEntitySerializer());
        assertEquals("d", storage.getEntity(0).getKey());
        assertEquals("b", storage.getEntity(2).getKey());
        assertEquals("q", storage.getEntity(4).getKey());
        assertEquals("l", storage.getEntity(7).getKey());
        assertEquals("v", storage.getEntity(9).getKey());
        assertNull(storage.getEntity(3));
        assertNull(storage.getEntity(8));
        assertEquals(values.length - 2, storage.getNumEntities());
    }


    @Test
    public void testUseStorageWithShorterEntityLength() throws IOException, EntityStorageException {
        // Test that we can open and work with a storage where the entity size is different than expected
        // This corresponds to having a new version of the database reading older database files.
        // The expected outcome is that new fields will have their new fields truncated when written.
        File file = folder.newFile();
        file.delete();

        // Add two entities as an old database with shorter entity size
        EntityStorage<TestEntity> oldDb = EntityStorageImpl.create(file, new TestEntitySerializer());
        TestEntity key = new TestEntity("a");
        key.setValue(7);
        oldDb.addEntity(key);
        key = new TestEntity("b");
        key.setValue(9);
        oldDb.addEntity(key);
        oldDb.close();

        // Open as a new database with longer entity size and check that we can read the data
        EntityStorage<TestEntityv2> newDb = EntityStorageImpl.open(file, new TestEntityv2Serializer());
        assertEquals(2, newDb.getNumEntities());
        TestEntityv2 e2 = newDb.getEntity(0);
        assertEquals(e2.getKey(), "a");
        assertEquals(e2.getValue(), 7);
        assertEquals(e2.getExtraValue(), 0);
        assertEquals(e2.getExtraString(), "");

        e2 = newDb.getEntity(1);
        assertEquals(e2.getKey(), "b");
        assertEquals(e2.getValue(), 9);
        assertEquals(e2.getExtraValue(), 0);
        assertEquals(e2.getExtraString(), "");

        // Add one new entity, replace one and deleted one as the new database
        TestEntityv2 key2 = new TestEntityv2("c");
        key2.setValue(12);
        key2.setExtraValue(19);
        key2.setExtraString("truncated");
        newDb.addEntity(key2);
        key2 = new TestEntityv2("a");
        key2.setValue(-8);
        key2.setExtraValue(100);
        key2.setExtraString("also removed");
        newDb.putEntity(0, key2);
        newDb.deleteEntity(1);

        TestEntityv2 key3 = newDb.getEntity(0);
        assertEquals(-8, key3.getValue());
        assertEquals(0, key3.getExtraValue());
        assertEquals("", key3.getExtraString());
        key3 = newDb.getEntity(2);
        assertEquals(12, key3.getValue());
        assertEquals(0, key3.getExtraValue());
        assertEquals("", key3.getExtraString());

        newDb.close();

        // Check that we can read the new entities as the old database
        oldDb = EntityStorageImpl.open(file, new TestEntitySerializer());
        TestEntity key5 = oldDb.getEntity(0);
        assertEquals("a", key5.getKey());
        assertEquals(-8, key5.getValue());
        key5 = oldDb.getEntity(2);
        assertEquals("c", key5.getKey());
        assertEquals(12, key5.getValue());
        assertNull(oldDb.getEntity(1));
        oldDb.close();
    }

    @Test
    public void testUseStorageWithLongerEntityLength() throws IOException, EntityStorageException {
        // Test that we can open and work with a storage where the entity size is larger than expected
        // This corresponds to having an old version of the database reading and working with
        // database files from a newer version.
        // The expected outcome is that unknown fields will be untouched when updating entities,
        // or empty if writing new ones.

        File file = folder.newFile();
        file.delete();

        // Add two entities as a new database with longer entity size
        EntityStorage<TestEntityv2> newDb = EntityStorageImpl.create(file, new TestEntityv2Serializer());
        TestEntityv2 key = new TestEntityv2("a");
        key.setValue(7);
        key.setExtraValue(10);
        key.setExtraString("hello");
        newDb.addEntity(key);
        key = new TestEntityv2("b");
        key.setValue(9);
        key.setExtraValue(1);
        key.setExtraString("world");
        newDb.addEntity(key);
        newDb.close();

        // Open as an old database with shorter entity size and check that we can read the data
        EntityStorage<TestEntity> oldDb = EntityStorageImpl.open(file, new TestEntitySerializer());
        assertEquals(2, oldDb.getNumEntities());
        TestEntity e2 = oldDb.getEntity(0);
        assertEquals(e2.getKey(), "a");
        assertEquals(e2.getValue(), 7);

        e2 = oldDb.getEntity(1);
        assertEquals(e2.getKey(), "b");
        assertEquals(e2.getValue(), 9);

        // Add one new entity, replace one and delete one as the old database
        TestEntity key2 = new TestEntity("c");
        key2.setValue(12);
        oldDb.addEntity(key2);
        key2 = new TestEntity("a");
        key2.setValue(-8);
        oldDb.putEntity(0, key2);
        oldDb.deleteEntity(1);

        TestEntity key3 = oldDb.getEntity(0);
        assertEquals(-8, key3.getValue());

        oldDb.close();

        // Check that we can read the new entities as the new database
        // Also check that the entity that was updated didn't get the extra field modified
        newDb = EntityStorageImpl.open(file, new TestEntityv2Serializer());
        TestEntityv2 key5 = newDb.getEntity(0);
        assertEquals("a", key5.getKey());
        assertEquals(-8, key5.getValue());
        assertEquals(10, key5.getExtraValue());
        assertEquals("hello", key5.getExtraString());
        key5 = newDb.getEntity(2);
        assertEquals("c", key5.getKey());
        assertEquals(12, key5.getValue());
        assertEquals(0, key5.getExtraValue());
        assertEquals("", key5.getExtraString());
        assertNull(newDb.getEntity(1));
        newDb.close();
    }

    @Test
    public void testUseStorageWithDifferentHeaderSize() throws IOException, EntityStorageException {
        File file = folder.newFile();
        file.delete();

        EntityStorage<TestEntity> oldDb = EntityStorageImpl.create(file, new TestEntitySerializer(), 28);
        TestEntity hello = new TestEntity(0, "hello");
        hello.setValue(5);
        oldDb.addEntity(hello);
        oldDb.close();

        EntityStorage<TestEntity> newDb = EntityStorageImpl.open(file, new TestEntitySerializer());
        TestEntity entity = newDb.getEntity(0);
        assertEquals("hello", entity.getKey());
        assertEquals(5, entity.getValue());
    }
}
