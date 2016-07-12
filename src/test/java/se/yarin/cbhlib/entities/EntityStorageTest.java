package se.yarin.cbhlib.entities;

import lombok.Getter;
import lombok.Setter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.ByteBufferUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.Assert.assertEquals;

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
        return EntityStorageImpl.createInMemory("inmem", new TestEntitySerializer());
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

    // Test replace (both changing key and not)
}
