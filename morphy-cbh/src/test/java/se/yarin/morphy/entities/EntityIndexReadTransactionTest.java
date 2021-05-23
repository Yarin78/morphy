package se.yarin.morphy.entities;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.morphy.storage.InMemoryItemStorage;

import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class EntityIndexReadTransactionTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Random random = new Random();

    private FooEntityIndex createIndex() {
        return new FooEntityIndex(new InMemoryItemStorage<>(EntityIndexHeader.empty(FooEntityIndex.SERIALIZED_FOO_SIZE)));
    }

    private String nextRandomString() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < sb.capacity(); i++) {
            sb.append((char)('a' + random.nextInt(26)));
        }
        return sb.toString();
    }

    @Test
    public void testStreamOrderedAscending() {
        FooEntityIndex index = createIndex();
        String[] values = {"d", "t", "b", "e", "q", "w", "a", "l", "c", "v"};
        for (String value : values) {
            index.add(FooEntity.of(value));
        }

        EntityIndexReadTransaction<FooEntity> txn = index.beginReadTransaction();

        String result = txn.streamOrderedAscending()
                .map(FooEntity::key)
                .collect(Collectors.joining());
        assertEquals("abcdelqtvw", result);

        result = txn.streamOrderedAscending(FooEntity.of("f"))
                .map(FooEntity::key)
                .collect(Collectors.joining());
        assertEquals("lqtvw", result);

        result = txn.streamOrderedAscending(FooEntity.of("q"))
                .map(FooEntity::key)
                .collect(Collectors.joining());
        assertEquals("qtvw", result);
    }

    @Test
    public void testAscendingStreamOverEmptyIndex() {
        FooEntityIndex index = createIndex();
        EntityIndexReadTransaction<FooEntity> txn = index.beginReadTransaction();

        assertEquals(0, txn.streamOrderedAscending().count());
        assertEquals(0, txn.streamOrderedAscending(FooEntity.of("a")).count());
    }

    @Test
    public void testAscendingStreamOverSingleNodeIndex() {
        FooEntityIndex index = createIndex();
        index.add(FooEntity.of("e"));
        EntityIndexReadTransaction<FooEntity> txn = index.beginReadTransaction();

        // No start marker
        assertEquals("e", txn.streamOrderedAscending().map(FooEntity::key).collect(Collectors.joining()));

        // Start before first
        assertEquals("e", txn.streamOrderedAscending(FooEntity.of("b")).map(FooEntity::key).collect(Collectors.joining()));

        // Start same as first
        assertEquals("e", txn.streamOrderedAscending(FooEntity.of("e")).map(FooEntity::key).collect(Collectors.joining()));

        // Start after as first
        assertEquals("", txn.streamOrderedAscending(FooEntity.of("g")).map(FooEntity::key).collect(Collectors.joining()));
    }

    @Test
    public void testOrderedDescendingStream() {
        FooEntityIndex index = createIndex();
        String[] values = {"d", "t", "b", "e", "q", "w", "a", "l", "c", "v"};
        for (String value : values) {
            index.add(FooEntity.of(value));
        }
        EntityIndexReadTransaction<FooEntity> txn = index.beginReadTransaction();

        assertEquals("wvtqledcba", txn.streamOrderedDescending().map(FooEntity::key).collect(Collectors.joining()));

        assertEquals("edcba", txn.streamOrderedDescending(FooEntity.of("f")).map(FooEntity::key).collect(Collectors.joining()));

        assertEquals("qledcba", txn.streamOrderedDescending(FooEntity.of("q")).map(FooEntity::key).collect(Collectors.joining()));
    }

    @Test
    public void testStreamWithDuplicateEntries() {
        FooEntityIndex index = createIndex();
        String[] values = {"d", "e", "b", "e", "q", "w", "a", "l", "c", "v", "x", "e", "b", "t" };
        for (String value : values) {
            index.add(FooEntity.of(value));
        }
        EntityIndexReadTransaction<FooEntity> txn = index.beginReadTransaction();

        assertEquals("abbcdeeelqtvwx", txn.streamOrderedAscending().map(FooEntity::key).collect(Collectors.joining()));
        assertEquals("xwvtqleeedcbba", txn.streamOrderedDescending().map(FooEntity::key).collect(Collectors.joining()));
    }

    @Test
    public void testStreamOverEmptyIndex() {
        FooEntityIndex index = createIndex();
        EntityIndexReadTransaction<FooEntity> txn = index.beginReadTransaction();
        assertEquals(0, txn.stream().count());
        txn.close();

        index.add(FooEntity.of("hello"));
        index.delete(0);

        txn = index.beginReadTransaction();
        assertEquals(0, txn.stream().count());
        txn.close();
    }

    @Test
    public void testStream() {
        FooEntityIndex index = createIndex();
        String[] values = {"d", "t", "b", "e", "q", "w", "a", "l", "c", "v"};
        for (String value : values) {
            index.add(FooEntity.of(value));
        }
        index.delete(3);
        index.delete(8);

        EntityIndexReadTransaction<FooEntity> txn = index.beginReadTransaction();
        assertEquals("dtbqwalv", txn.stream().map(FooEntity::key).collect(Collectors.joining()));
    }

    @Test(expected = IllegalStateException.class)
    public void failCreateReadTransactionAfterWriteTransactionInSameThread() {
        FooEntityIndex index = createIndex();
        index.beginWriteTransaction();
        index.beginReadTransaction();
    }

    @Test(expected = IllegalStateException.class)
    public void failCreateWriteTransactionAfterReadTransactionInSameThread() {
        FooEntityIndex index = createIndex();
        index.beginReadTransaction();
        index.beginWriteTransaction();
    }

    @Test
    public void testIteratorOverMultipleBatches() {
        FooEntityIndex index = createIndex();
        int expected = 3876;
        for (int i = 0; i < expected; i++) {
            index.add(FooEntity.of(nextRandomString()));
        }

        EntityIndexReadTransaction<FooEntity> txn = index.beginReadTransaction();

        assertEquals(expected, txn.stream().count());
    }


    @Test
    public void testStreamWithFilter() {
        FooEntityIndex index = createIndex();
        int numItems = 10000;
        for (int i = 0; i < numItems; i++) {
            index.add(FooEntity.of(nextRandomString(), i));
        }

        EntityIndexReadTransaction<FooEntity> txn = index.beginReadTransaction();
        assertEquals((int) Math.ceil(numItems / 7.0), txn.streamOrderedAscending(item -> item.value() % 7 == 0).count());
        assertEquals((int) Math.ceil(numItems / 7.0), txn.streamOrderedDescending(item -> item.value() % 7 == 0).count());
        assertEquals((int) Math.ceil(numItems / 7.0), txn.stream(item -> item.value() % 7 == 0).count());
    }

}
