package se.yarin.morphy.entities;

import org.junit.Test;
import se.yarin.morphy.DatabaseConfig;
import se.yarin.morphy.DatabaseContext;
import se.yarin.morphy.storage.InMemoryItemStorage;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;

public class EntityIndexWriteTransactionTest {

    private FooEntityIndex createIndex() {
        return new FooEntityIndex(
                new InMemoryItemStorage<>(EntityIndexHeader.empty(FooEntityIndex.SERIALIZED_FOO_SIZE)),
                new DatabaseContext(new DatabaseConfig() {{
                    setWriteLockWaitTimeoutInSeconds(-1);
                    setReadLockWaitTimeoutInSeconds(-1);
                }})
            );
    }

    @Test
    public void testBeginAndCommitEmptyTransaction() {
        EntityIndex<FooEntity> index = createIndex();

        EntityIndexWriteTransaction<FooEntity> txn = index.beginWriteTransaction();
        txn.commit();
    }

    @Test
    public void testAddEntityInTransaction() {
        EntityIndex<FooEntity> index = createIndex();

        EntityIndexWriteTransaction<FooEntity> txn = index.beginWriteTransaction();
        txn.addEntity(FooEntity.of("a"));
        assertEquals(0, index.count());
        txn.commit();
        assertEquals(1, index.count());
        assertEquals("a", index.get(0).key());
    }

    @Test(expected = IllegalStateException.class)
    public void testCommitSameTransactionTwice() {
        EntityIndex<FooEntity> index = createIndex();

        EntityIndexWriteTransaction<FooEntity> txn = index.beginWriteTransaction();
        txn.commit();
        txn.commit();
    }

    @Test
    public void testCommitTwoTransactionsSequentially() {
        EntityIndex<FooEntity> index = createIndex();

        EntityIndexWriteTransaction<FooEntity> txn = index.beginWriteTransaction();
        txn.addEntity(FooEntity.of("a"));
        txn.commit();

        txn = index.beginWriteTransaction();
        txn.addEntity(FooEntity.of("b"));
        txn.commit();

        assertEquals(2, index.count());
        assertEquals("a", index.get(0).key());
        assertEquals("b", index.get(1).key());
    }

    @Test(expected = IllegalStateException.class)
    public void testCommitTwoTransactionsOnSameVersion() {
        EntityIndex<FooEntity> index = createIndex();

        // We can create the two write transactions because the locks are reentrant and we're in the same thread
        EntityIndexWriteTransaction<FooEntity> txn1 = index.beginWriteTransaction();
        EntityIndexWriteTransaction<FooEntity> txn2 = index.beginWriteTransaction();
        txn1.commit();
        txn2.commit();
    }

    @Test(expected = IllegalStateException.class)
    public void testBeginTwoWriteTransactionsInDifferentThreads() throws InterruptedException {
        EntityIndex<FooEntity> index = createIndex();

        CountDownLatch latch = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            index.beginWriteTransaction();
            latch.countDown();
        });
        thread.start();

        latch.await();
        index.beginWriteTransaction();
    }

    @Test
    public void testMultipleOperationsInSameTransaction() {
        EntityIndex<FooEntity> index = createIndex();

        EntityIndexWriteTransaction<FooEntity> txn = index.beginWriteTransaction();
        txn.addEntity(FooEntity.of("a"));
        txn.addEntity(FooEntity.of("b"));
        txn.addEntity(FooEntity.of("c"));
        txn.putEntityById(1, FooEntity.of("d"));
        txn.deleteEntity(FooEntity.of("c"));
        txn.commit();

        assertEquals(2, index.count());
        assertEquals("a", index.get(0).key());
        assertEquals("d", index.get(1).key());
        assertTrue(index.getNode(2).isDeleted());
    }

    @Test
    public void testReadDataInsideTransaction() {
        EntityIndex<FooEntity> index = createIndex();
        index.add(FooEntity.of("a"));
        index.add(FooEntity.of("b"));

        EntityIndexWriteTransaction<FooEntity> txn = index.beginWriteTransaction();
        txn.deleteEntity(FooEntity.of("a"));
        txn.addEntity(FooEntity.of("c"));
        txn.addEntity(FooEntity.of("e"));
        txn.addEntity(FooEntity.of("f"));
        txn.deleteEntity(FooEntity.of("e"));


        assertEquals("b", txn.get(1).key());
        assertNotNull(txn.get(FooEntity.of("c")));
        assertNull(txn.get(FooEntity.of("a")));
        assertNull(txn.get(FooEntity.of("d")));

        assertEquals(3, txn.header().numEntities());
        assertEquals(4, txn.header().capacity());
    }

    @Test
    public void testReadDataOutsideTransaction() throws InterruptedException {
        EntityIndex<FooEntity> index = createIndex();
        index.add(FooEntity.of("a"));
        index.add(FooEntity.of("b"));

        CountDownLatch latch = new CountDownLatch(1);

        Thread writeThread = new Thread(() -> {
            EntityIndexWriteTransaction<FooEntity> writeTxn = index.beginWriteTransaction();
            writeTxn.deleteEntity(FooEntity.of("a"));
            writeTxn.addEntity(FooEntity.of("c"));
            writeTxn.addEntity(FooEntity.of("e"));
            writeTxn.addEntity(FooEntity.of("f"));
            writeTxn.deleteEntity(FooEntity.of("e"));
            latch.countDown();
        });
        writeThread.start();
        latch.await();

        assertEquals("b", index.get(1).key());
        assertNull(index.get(FooEntity.of("c")));
        assertNotNull(index.get(FooEntity.of("a")));
        assertNull(index.get(FooEntity.of("d")));

        assertEquals(2, index.count());
        assertEquals(2, index.capacity());
    }
}
