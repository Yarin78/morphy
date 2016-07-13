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
        int id = storage.addEntity(new TestEntity("hello"));
        assertEquals(0, id);
        assertEquals(1, storage.getNumEntities());

        TestEntity entity = storage.getEntity(id);
        assertEquals("hello", entity.getValue());

        storage.validateStructure();
    }

    @Test
    public void testAddMultipleEntities() throws IOException, EntityStorageException {
        EntityStorageImpl<TestEntity> storage = createStorage();
        for (int i = 0; i < 20; i++) {
            int id = storage.addEntity(new TestEntity(nextRandomString()));
            assertEquals(i, id);
            assertEquals(i + 1, storage.getNumEntities());
            storage.validateStructure();
        }
    }

    @Test
    public void testDeleteEntity() throws IOException, EntityStorageException {
        EntityStorageImpl<TestEntity> storage = createStorage();
        int id1 = storage.addEntity(new TestEntity("hello"));
        int id2 = storage.addEntity(new TestEntity("world"));

        assertTrue(storage.deleteEntity(id1));

        assertEquals(1, storage.getNumEntities());
        assertNull(storage.getEntity(id1));
        assertNotNull(storage.getEntity(id2));

        storage.validateStructure();
    }

    @Test
    public void testDeleteDeletedEntity() throws IOException, EntityStorageException {
        EntityStorageImpl<TestEntity> storage = createStorage();
        int id1 = storage.addEntity(new TestEntity("hello"));
        assertTrue(storage.deleteEntity(id1));
        assertEquals(0, storage.getNumEntities());
        assertFalse(storage.deleteEntity(id1));
        assertEquals(0, storage.getNumEntities());

        storage.validateStructure();
    }

    @Test
    public void testDeleteNodeWithTwoChildren() throws IOException, EntityStorageException {
        EntityStorageImpl<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity("B"));
        storage.addEntity(new TestEntity("A"));
        storage.addEntity(new TestEntity("D"));
        storage.addEntity(new TestEntity("C"));
        storage.addEntity(new TestEntity("F"));
        storage.addEntity(new TestEntity("E"));
        storage.addEntity(new TestEntity("G"));

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
        storage.addEntity(new TestEntity("B"));
        storage.addEntity(new TestEntity("A"));
        storage.addEntity(new TestEntity("D"));
        storage.addEntity(new TestEntity("C"));
        storage.addEntity(new TestEntity("E"));
        storage.addEntity(new TestEntity("F"));

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
            storage.addEntity(new TestEntity(nextRandomString()));
        }
        for (int i = 0; i < count; i++) {
            storage.deleteEntity(i);
            assertEquals(count - i - 1, storage.getNumEntities());
            storage.validateStructure();
        }
    }

    // Test replace (both changing key and not)
}
