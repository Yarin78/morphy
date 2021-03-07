package se.yarin.morphy.entities;

import org.junit.Test;
import se.yarin.morphy.storage.InMemoryItemStorage;

import static org.junit.Assert.*;

public class EntityIndexTransactionTest {

    private FooEntityIndex createIndex() {
        return new FooEntityIndex(new InMemoryItemStorage<>(EntityIndexHeader.empty(FooEntityIndex.SERIALIZED_FOO_SIZE)));
    }

    @Test
    public void testBeginAndCommitEmptyTransaction() {
        EntityIndex<FooEntity> index = createIndex();

        EntityIndexTransaction<FooEntity> txn = index.beginTransaction();
        txn.commit();
    }

    @Test
    public void testAddEntityInTransaction() {
        EntityIndex<FooEntity> index = createIndex();

        EntityIndexTransaction<FooEntity> txn = index.beginTransaction();
        txn.addEntity(FooEntity.of("a"));
        assertEquals(0, index.count());
        txn.commit();
        assertEquals(1, index.count());
        assertEquals("a", index.get(0).key());
    }

    @Test(expected = IllegalStateException.class)
    public void testCommitSameTransactionTwice() {
        EntityIndex<FooEntity> index = createIndex();

        EntityIndexTransaction<FooEntity> txn = index.beginTransaction();
        txn.commit();
        txn.commit();
    }

    @Test
    public void testCommitTwoTransactionsSequentially() {
        EntityIndex<FooEntity> index = createIndex();

        EntityIndexTransaction<FooEntity> txn = index.beginTransaction();
        txn.addEntity(FooEntity.of("a"));
        txn.commit();

        txn = index.beginTransaction();
        txn.addEntity(FooEntity.of("b"));
        txn.commit();

        assertEquals(2, index.count());
        assertEquals("a", index.get(0).key());
        assertEquals("b", index.get(1).key());
    }

    @Test(expected = IllegalStateException.class)
    public void testCommitTwoTransactionsOnSameVersion() {
        EntityIndex<FooEntity> index = createIndex();

        EntityIndexTransaction<FooEntity> txn1 = index.beginTransaction();
        EntityIndexTransaction<FooEntity> txn2 = index.beginTransaction();
        txn1.commit();
        txn2.commit();
    }

    @Test
    public void testMultipleOperationsInSameTransaction() {
        EntityIndex<FooEntity> index = createIndex();

        EntityIndexTransaction<FooEntity> txn = index.beginTransaction();
        txn.addEntity(FooEntity.of("a"));
        txn.addEntity(FooEntity.of("b"));
        txn.addEntity(FooEntity.of("c"));
        txn.putEntityById(1, FooEntity.of("d"));
        txn.deleteEntity(FooEntity.of("c"));
        txn.commit();

        assertEquals(2, index.count());
        assertEquals("a", index.get(0).key());
        assertEquals("d", index.get(1).key());
        assertNull(index.get(2));
    }

    @Test
    public void testReadDataInsideTransaction() {
        EntityIndex<FooEntity> index = createIndex();
        index.add(FooEntity.of("a"));
        index.add(FooEntity.of("b"));

        EntityIndexTransaction<FooEntity> txn = index.beginTransaction();
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
    public void testReadDataOutsideTransaction() {
        EntityIndex<FooEntity> index = createIndex();
        index.add(FooEntity.of("a"));
        index.add(FooEntity.of("b"));

        EntityIndexTransaction<FooEntity> txn = index.beginTransaction();
        txn.deleteEntity(FooEntity.of("a"));
        txn.addEntity(FooEntity.of("c"));
        txn.addEntity(FooEntity.of("e"));
        txn.addEntity(FooEntity.of("f"));
        txn.deleteEntity(FooEntity.of("e"));


        assertEquals("b", index.get(1).key());
        assertNull(index.get(FooEntity.of("c")));
        assertNotNull(index.get(FooEntity.of("a")));
        assertNull(index.get(FooEntity.of("d")));

        assertEquals(2, index.count());
        assertEquals(2, index.capacity());
    }
}
