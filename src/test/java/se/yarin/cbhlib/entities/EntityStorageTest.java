package se.yarin.cbhlib.entities;

import lombok.Getter;
import lombok.Setter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Random;

import static org.junit.Assert.*;

public class EntityStorageTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Random random;

    private static class TestEntity implements Entity, Comparable<TestEntity> {
        @Getter
        private int id;
        @Getter @Setter
        private String key;
        @Getter @Setter
        private int value;

        TestEntity(int id, String key) {
            this.id = id;
            this.key = key;
        }

        public TestEntity(String key) {
            this(-1, key);
        }

        @Override
        public int compareTo(TestEntity o) {
            return key.compareTo(o.key);
        }
    }

    private static class TestEntitySerializer implements EntitySerializer<TestEntity> {
        @Override
        public ByteBuffer serialize(TestEntity entity) {
            ByteBuffer buf = ByteBuffer.allocate(24);
            ByteBufferUtil.putByteString(buf, entity.getKey(), 20);
            ByteBufferUtil.putIntB(buf, entity.getValue());
            return buf;
        }

        @Override
        public TestEntity deserialize(int entityId, ByteBuffer buffer) {
            String value = ByteBufferUtil.getFixedSizeByteString(buffer, 20);
            TestEntity testEntity = new TestEntity(entityId, value);
            testEntity.setValue(ByteBufferUtil.getIntB(buffer));
            return testEntity;
        }

        @Override
        public int getSerializedEntityLength() {
            return 24;
        }
    }

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
}
