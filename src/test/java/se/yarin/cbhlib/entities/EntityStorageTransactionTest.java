package se.yarin.cbhlib.entities;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class EntityStorageTransactionTest {

    @Test
    public void testBeginAndCommitEmptyTransaction()
            throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = EntityStorageImpl.createInMemory();

        EntityStorageTransaction<TestEntity> txn = storage.beginTransaction();
        txn.commit();
    }

    @Test
    public void testAddEntityInTransaction()
            throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = EntityStorageImpl.createInMemory();

        EntityStorageTransaction<TestEntity> txn = storage.beginTransaction();
        txn.addEntity(new TestEntity("a"));
        assertEquals(0, storage.getNumEntities());
        assertNull(storage.getEntity(0));
        txn.commit();
        assertEquals(1, storage.getNumEntities());
        assertEquals("a", storage.getEntity(0).getKey());
    }

    @Test(expected = IllegalStateException.class)
    public void testCommitSameTransactionTwice()
            throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = EntityStorageImpl.createInMemory();

        EntityStorageTransaction<TestEntity> txn = storage.beginTransaction();
        txn.commit();
        txn.commit();
    }

    @Test
    public void testCommitTwoTransactionsSequentially() throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = EntityStorageImpl.createInMemory();

        EntityStorageTransaction<TestEntity> txn = storage.beginTransaction();
        txn.addEntity(new TestEntity("a"));
        txn.commit();

        txn = storage.beginTransaction();
        txn.addEntity(new TestEntity("b"));
        txn.commit();

        assertEquals(2, storage.getNumEntities());
        assertEquals("a", storage.getEntity(0).getKey());
        assertEquals("b", storage.getEntity(1).getKey());
    }

    @Test(expected = EntityStorageException.class)
    public void testCommitTwoTransactionsOnSameVersion()
            throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = EntityStorageImpl.createInMemory();

        EntityStorageTransaction<TestEntity> txn1 = storage.beginTransaction();
        EntityStorageTransaction<TestEntity> txn2 = storage.beginTransaction();
        txn1.commit();
        txn2.commit();
    }

    @Test
    public void testMultipleOperationsInSameTransaction() throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = EntityStorageImpl.createInMemory();

        EntityStorageTransaction<TestEntity> txn = storage.beginTransaction();
        txn.addEntity(new TestEntity("a"));
        txn.addEntity(new TestEntity("b"));
        txn.addEntity(new TestEntity("c"));
        txn.putEntityById(1, new TestEntity("d"));
        txn.deleteEntity(new TestEntity("c"));
        txn.commit();

        assertEquals(2, storage.getNumEntities());
        assertEquals("a", storage.getEntity(0).getKey());
        assertEquals("d", storage.getEntity(1).getKey());
        assertNull(storage.getEntity(2));
    }

    @Test
    public void testReadDataInsideTransaction() throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = EntityStorageImpl.createInMemory();
        storage.addEntity(new TestEntity("a"));
        storage.addEntity(new TestEntity("b"));

        EntityStorageTransaction<TestEntity> txn = storage.beginTransaction();
        txn.deleteEntity(new TestEntity("a"));
        txn.addEntity(new TestEntity("c"));
        txn.addEntity(new TestEntity("e"));
        txn.addEntity(new TestEntity("f"));
        txn.deleteEntity(new TestEntity("e"));


        assertEquals("b", txn.getEntity(1).getKey());
        assertNotNull(txn.getEntity(new TestEntity("c")));
        assertNull(txn.getEntity(new TestEntity("a")));
        assertNull(txn.getEntity(new TestEntity("d")));

        assertEquals(3, txn.getNumEntities());
        assertEquals(4, txn.getCapacity());
    }

    @Test
    public void testReadDataOutsideTransaction() throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = EntityStorageImpl.createInMemory();
        storage.addEntity(new TestEntity("a"));
        storage.addEntity(new TestEntity("b"));

        EntityStorageTransaction<TestEntity> txn = storage.beginTransaction();
        txn.deleteEntity(new TestEntity("a"));
        txn.addEntity(new TestEntity("c"));
        txn.addEntity(new TestEntity("e"));
        txn.addEntity(new TestEntity("f"));
        txn.deleteEntity(new TestEntity("e"));


        assertEquals("b", storage.getEntity(1).getKey());
        assertNull(storage.getEntity(new TestEntity("c")));
        assertNotNull(storage.getEntity(new TestEntity("a")));
        assertNull(storage.getEntity(new TestEntity("d")));

        assertEquals(2, storage.getNumEntities());
        assertEquals(2, storage.getCapacity());
    }
}
