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
        private String value;

        TestEntity(int id, String value) {
            this.id = id;
            this.value = value;
        }

        public TestEntity(String value) {
            this(-1, value);
        }

        @Override
        public int compareTo(TestEntity o) {
            return value.compareTo(o.value);
        }
    }

    private static class TestEntitySerializer implements EntitySerializer<TestEntity> {
        @Override
        public ByteBuffer serialize(TestEntity entity) {
            ByteBuffer buf = ByteBuffer.allocate(20);
            ByteBufferUtil.putByteString(buf, entity.getValue(), 20);
            return buf;
        }

        @Override
        public TestEntity deserialize(int entityId, ByteBuffer buffer) {
            String value = ByteBufferUtil.getFixedSizeByteString(buffer, 20);
            return new TestEntity(entityId, value);
        }

        @Override
        public int getSerializedEntityLength() {
            return 20;
        }
    }

    private EntityStorageImpl<TestEntity> createStorage() throws IOException, EntityStorageException {
//        File file = folder.newFile();
//        file.delete();
//        return EntityStorageImpl.create(file, new TestEntitySerializer());
        return EntityStorageImpl.<TestEntity>createInMemory("inmem");
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
        EntityStorageImpl<TestEntity> storage = createStorage();
        assertEquals(0, storage.getNumEntities());
        storage.validateStructure();
    }

    @Test
    public void testAddEntity() throws IOException, EntityStorageException {
        EntityStorageImpl<TestEntity> storage = createStorage();
        int id = storage.addEntity(new TestEntity(0, "hello"));
        assertEquals(1, storage.getNumEntities());

        TestEntity entity = storage.getEntity(id);
        assertEquals("hello", entity.getValue());
        assertEquals(0, entity.getId());

        storage.validateStructure();
    }

    @Test
    public void testAddMultipleEntities() throws IOException, EntityStorageException {
        EntityStorageImpl<TestEntity> storage = createStorage();
        for (int i = 0; i < 20; i++) {
            int id = storage.addEntity(new TestEntity(i, nextRandomString()));
            assertEquals(i, id);
            assertEquals(i + 1, storage.getNumEntities());
            storage.validateStructure();
        }
    }

    @Test
    public void testDeleteEntity() throws IOException, EntityStorageException {
        EntityStorageImpl<TestEntity> storage = createStorage();
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
        EntityStorageImpl<TestEntity> storage = createStorage();
        int id1 = storage.addEntity(new TestEntity(0, "hello"));
        assertTrue(storage.deleteEntity(id1));
        assertEquals(0, storage.getNumEntities());
        assertFalse(storage.deleteEntity(id1));
        assertEquals(0, storage.getNumEntities());

        storage.validateStructure();
    }

    @Test
    public void testDeleteNodeWithTwoChildren() throws IOException, EntityStorageException {
        EntityStorageImpl<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity(0, "B"));
        storage.addEntity(new TestEntity(1, "A"));
        storage.addEntity(new TestEntity(2, "D"));
        storage.addEntity(new TestEntity(3, "C"));
        storage.addEntity(new TestEntity(4, "F"));
        storage.addEntity(new TestEntity(5, "E"));
        storage.addEntity(new TestEntity(6, "G"));

        storage.deleteEntity(2);
        assertNull(storage.getEntity(2));
        assertEquals("B", storage.getEntity(0).getValue());
        assertEquals("F", storage.getEntity(4).getValue());
        assertEquals("E", storage.getEntity(5).getValue());
        storage.validateStructure();
    }

    @Test
    public void testDeleteNodeWithTwoChildren2() throws IOException, EntityStorageException {
        // Special case when the code swaps two nodes that have parent-child relation
        EntityStorageImpl<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity(0, "B"));
        storage.addEntity(new TestEntity(1, "A"));
        storage.addEntity(new TestEntity(2, "D"));
        storage.addEntity(new TestEntity(3, "C"));
        storage.addEntity(new TestEntity(4, "E"));
        storage.addEntity(new TestEntity(5, "F"));

        storage.deleteEntity(2);
        assertNull(storage.getEntity(2));
        assertEquals("B", storage.getEntity(0).getValue());
        assertEquals("C", storage.getEntity(3).getValue());
        assertEquals("E", storage.getEntity(4).getValue());
        assertEquals("F", storage.getEntity(5).getValue());
        storage.validateStructure();
    }

    @Test
    public void testAddMultipleEntitiesAndThenDeleteThem() throws IOException, EntityStorageException {
        EntityStorageImpl<TestEntity> storage = createStorage();
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
        EntityStorageImpl<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity(0, "b"));
        storage.addEntity(new TestEntity(1, "a"));
        storage.addEntity(new TestEntity(2, "c"));

        storage.putEntity(0, new TestEntity(0, "b"));
        assertEquals(3, storage.getNumEntities());
        storage.validateStructure();
    }

    @Test
    public void testReplaceNodeWithNewKey() throws IOException, EntityStorageException {
        EntityStorageImpl<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity(0, "b"));
        storage.addEntity(new TestEntity(1, "a"));
        storage.addEntity(new TestEntity(2, "c"));

        storage.putEntity(0, new TestEntity(0, "d"));
        assertEquals(3, storage.getNumEntities());
        assertEquals("d", storage.getEntity(0).getValue());
        storage.validateStructure();
    }

    @Test
    public void testAscendingIterator() throws IOException, EntityStorageException {
        EntityStorageImpl<TestEntity> storage = createStorage();
        String[] values = {"d", "t", "b", "e", "q", "w", "a", "l", "c", "v"};
        for (int i = 0; i < values.length; i++) {
            storage.addEntity(new TestEntity(i, values[i]));
        }

        Iterator<TestEntity> iterator = storage.getOrderedAscendingIterator(null);
        StringBuilder sb = new StringBuilder();
        while (iterator.hasNext()) {
            sb.append(iterator.next().getValue());
        }
        assertEquals("abcdelqtvw", sb.toString());

        iterator = storage.getOrderedAscendingIterator(new TestEntity("f"));
        sb = new StringBuilder();
        while (iterator.hasNext()) {
            sb.append(iterator.next().getValue());
        }
        assertEquals("lqtvw", sb.toString());

        iterator = storage.getOrderedAscendingIterator(new TestEntity("q"));
        sb = new StringBuilder();
        while (iterator.hasNext()) {
            sb.append(iterator.next().getValue());
        }
        assertEquals("qtvw", sb.toString());
    }

    @Test
    public void testAscendingIterateOverEmptyStorage() throws IOException, EntityStorageException {
        EntityStorageImpl<TestEntity> storage = createStorage();

        Iterator<TestEntity> iterator = storage.getOrderedAscendingIterator(null);
        assertFalse(iterator.hasNext());

        iterator = storage.getOrderedAscendingIterator(new TestEntity("a"));
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testAscendingIterateOverSingleNodeStorage() throws IOException, EntityStorageException {
        EntityStorageImpl<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity(0, "e"));

        // No start marker
        Iterator<TestEntity> iterator = storage.getOrderedAscendingIterator(null);
        assertTrue(iterator.hasNext());
        assertEquals("e", iterator.next().getValue());
        assertFalse(iterator.hasNext());

        // Start before first
        iterator = storage.getOrderedAscendingIterator(new TestEntity("b"));
        assertTrue(iterator.hasNext());
        assertEquals("e", iterator.next().getValue());
        assertFalse(iterator.hasNext());

        // Start same as first
        iterator = storage.getOrderedAscendingIterator(new TestEntity("e"));
        assertTrue(iterator.hasNext());
        assertEquals("e", iterator.next().getValue());
        assertFalse(iterator.hasNext());

        // Start after as first
        iterator = storage.getOrderedAscendingIterator(new TestEntity("g"));
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testDescendingIterator() throws IOException, EntityStorageException {
        EntityStorageImpl<TestEntity> storage = createStorage();
        String[] values = {"d", "t", "b", "e", "q", "w", "a", "l", "c", "v"};
        for (int i = 0; i < values.length; i++) {
            storage.addEntity(new TestEntity(i, values[i]));
        }

        Iterator<TestEntity> iterator = storage.getOrderedDescendingIterator(null);
        StringBuilder sb = new StringBuilder();
        while (iterator.hasNext()) {
            sb.append(iterator.next().getValue());
        }
        assertEquals("wvtqledcba", sb.toString());

        iterator = storage.getOrderedDescendingIterator(new TestEntity("f"));
        sb = new StringBuilder();
        while (iterator.hasNext()) {
            sb.append(iterator.next().getValue());
        }
        assertEquals("edcba", sb.toString());

        iterator = storage.getOrderedDescendingIterator(new TestEntity("q"));
        sb = new StringBuilder();
        while (iterator.hasNext()) {
            sb.append(iterator.next().getValue());
        }
        assertEquals("qledcba", sb.toString());
    }
}
