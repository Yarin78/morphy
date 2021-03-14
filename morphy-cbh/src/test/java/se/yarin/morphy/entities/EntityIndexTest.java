package se.yarin.morphy.entities;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.morphy.storage.InMemoryItemStorage;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class EntityIndexTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Random random;

    private FooEntityIndex createIndex() {
        return new FooEntityIndex(new InMemoryItemStorage<>(EntityIndexHeader.empty(FooEntityIndex.SERIALIZED_FOO_SIZE)));
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
    public void testNewIndex() {
        FooEntityIndex index = createIndex();
        assertEquals(0, index.count());
        index.validateStructure();
    }

    @Test
    public void testAddEntity() {
        FooEntityIndex index = createIndex();

        FooEntity hello = FooEntity.of("hello", 7);
        int addedId = index.add(hello);
        assertEquals(0, addedId);
        assertEquals(1, index.count());

        FooEntity entity = index.get(0);
        assertEquals("hello", entity.key());
        assertEquals(0, entity.id());
        assertEquals(7, entity.value());

        index.validateStructure();
    }

    @Test
    public void testAddTwoEntities() {
        // Should cause no rotation
        FooEntityIndex index = createIndex();

        index.add(FooEntity.of("a"));
        index.add(FooEntity.of("b"));

        assertEquals(2, index.count());
        index.validateStructure();
    }

    @Test
    public void testAddThreeEntitiesSingleRotate() {
        // Should cause a left single rotation
        FooEntityIndex index = createIndex();

        index.add(FooEntity.of("a"));
        index.add(FooEntity.of("b"));
        index.add(FooEntity.of("c"));

        assertEquals(3, index.count());
        index.validateStructure();
    }

    @Test
    public void testAddThreeEntitiesDoubleRotate() {
        // Should cause a right-left double rotation
        FooEntityIndex index = createIndex();

        index.add(FooEntity.of("a"));
        index.add(FooEntity.of("c"));
        index.add(FooEntity.of("b"));

        assertEquals(3, index.count());
        index.validateStructure();
    }

    @Test
    public void testAddSixEntities() {
        FooEntityIndex index = createIndex();

        index.add(FooEntity.of("e"));
        index.add(FooEntity.of("f"));
        index.add(FooEntity.of("b"));
        index.add(FooEntity.of("d"));
        index.add(FooEntity.of("a"));
        index.add(FooEntity.of("c"));

        assertEquals(6, index.count());
        index.validateStructure();
    }

    @Test
    public void testGetEntityByKey() {
        FooEntityIndex index = createIndex();
        FooEntity hello = FooEntity.of("hello", 7);
        index.add(hello);

        FooEntity entity = index.get(FooEntity.of("hello", 0));
        assertEquals("hello", entity.key());
        assertEquals(0, entity.id());
        assertEquals(7, entity.value());
    }

    @Test
    public void testGetMissingEntityByKey() {
        FooEntityIndex index = createIndex();

        assertNull(index.get(FooEntity.of("world")));

        FooEntity hello = FooEntity.of("hello", 7);
        index.add(hello);

        assertNull(index.get(FooEntity.of("world")));
    }

    @Test
    public void testGetAnyEntityByKey() {
        FooEntityIndex index = createIndex();
        index.add(FooEntity.of("foo", 7));
        index.add(FooEntity.of("x", 2));
        index.add(FooEntity.of("bar", 5));
        index.add(FooEntity.of("y", 9));
        index.add(FooEntity.of("foo", 11));
        FooEntity foo = index.get(FooEntity.of("foo"));
        assertTrue(foo.value() == 7 || foo.value() == 11);
    }

    @Test
    public void testGetDuplicateEntitiesByKey() {
        FooEntityIndex index = createIndex();
        index.add(FooEntity.of("foo", 7));
        index.add(FooEntity.of("x", 2));
        index.add(FooEntity.of("bar", 5));
        index.add(FooEntity.of("y", 9));
        index.add(FooEntity.of("foo", 11));
        List<FooEntity> result = index.getAll(FooEntity.of("foo"));
        assertEquals(2, result.size());

        int v1 = result.get(0).value();
        int v2 = result.get(1).value();
        assertTrue(Math.min(v1, v2) == 7 && Math.max(v1, v2) == 11);
    }

    @Test
    public void testGetDuplicateEntitiesByKeyRandomInserts() {
        // Add 1000 random entities and ensure that when fetching entities by key, we get the correct amount each time.
        // Since all entities will have 1 of the 5 candidate keys, there will be lots and lots of duplicates.
        int noOps = 1000;
        String[] candidates = new String[] {"foo", "bar", "xyz", "a", "zzz"};
        int[] expectedCount = new int[candidates.length];
        FooEntityIndex storage = createIndex();
        Random random = new Random();
        for (int i = 0; i < noOps; i++) {
            int x = random.nextInt(candidates.length);
            storage.add(FooEntity.of(candidates[x]));
            expectedCount[x] += 1;

            for (int j = 0; j < candidates.length; j++) {
                int actual = storage.getAll(FooEntity.of(candidates[j])).size();
                assertEquals(expectedCount[j], actual);
            }
        }
    }

    @Test
    public void testAddMultipleEntities() {
        for (int iter = 0; iter < 100; iter++) {
            FooEntityIndex index = createIndex();
            for (int i = 0; i < 100; i++) {
                int id = index.add(FooEntity.of(nextRandomString()));
                assertEquals(i, id);
                assertEquals(i + 1, index.count());
                index.validateStructure();
            }
        }
    }

    @Test
    public void testUpdateEntityById() {
        FooEntityIndex index = createIndex();
        int id1 = index.add(FooEntity.of("foo", 7));
        int id2 = index.add(FooEntity.of("bar", 8));
        index.put(id1, FooEntity.of("FOO", 3));

        FooEntity x = index.get(id1);
        assertEquals("FOO", x.key());
        assertEquals(3, x.value());

        FooEntity y = index.get(id2);
        assertEquals("bar", y.key());
        assertEquals(8, y.value());
    }

    @Test
    public void testUpdateEntityByKey() {
        FooEntityIndex index = createIndex();
        int id1 = index.add(FooEntity.of("foo", 7));
        int id2 = index.add(FooEntity.of("bar", 8));
        index.put(FooEntity.of("foo", 3));

        FooEntity x = index.get(id1);
        assertEquals("foo", x.key());
        assertEquals(3, x.value());

        FooEntity y = index.get(id2);
        assertEquals("bar", y.key());
        assertEquals(8, y.value());
    }

    @Test
    public void testDeleteEntity() {
        FooEntityIndex index = createIndex();
        int id1 = index.add(FooEntity.of("hello"));
        int id2 = index.add(FooEntity.of("world"));

        assertTrue(index.delete(id1));

        assertEquals(1, index.count());
        FooEntity object = index.get(id1);
        assertNull(object);
        assertNotNull(index.get(id2));

        index.validateStructure();
    }

    @Test
    public void testRebalancingAfterDeleteEntity() {
        for (int i = 0; i < 7; i++) {
            FooEntityIndex index = createIndex();
            index.add(FooEntity.of("e"));
            index.add(FooEntity.of("b"));
            index.add(FooEntity.of("f"));
            index.add(FooEntity.of("a"));
            index.add(FooEntity.of("d"));
            index.add(FooEntity.of("g"));
            index.add(FooEntity.of("c"));
            index.delete(i);
            index.validateStructure();
        }
    }

    @Test
    public void testRebalancingAfterDeleteEntity2() {
        FooEntityIndex index = createIndex();
        index.add(FooEntity.of("d"));
        index.add(FooEntity.of("e"));
        index.add(FooEntity.of("b"));
        index.add(FooEntity.of("f"));
        index.add(FooEntity.of("a"));
        index.add(FooEntity.of("c"));
        index.delete(0);
        index.delete(2);
        index.validateStructure();
    }

    @Test
    public void testDeleteEntityByKey() {
        FooEntityIndex index = createIndex();
        int id1 = index.add(FooEntity.of("hello"));
        int id2 = index.add(FooEntity.of("world"));

        assertTrue(index.delete(FooEntity.of("world")));

        assertEquals(1, index.count());
        assertNull(index.get(id2));
        assertNotNull(index.get(id1));

        index.validateStructure();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteDuplicatedEntityByKey() {
        FooEntityIndex index = createIndex();
        index.add(FooEntity.of("foo"));
        index.add(FooEntity.of("bar"));
        index.add(FooEntity.of("foo"));

        index.delete(FooEntity.of("foo"));
    }

    @Test
    public void testDeleteDeletedEntity() {
        FooEntityIndex index = createIndex();
        int id1 = index.add(FooEntity.of("hello"));
        assertTrue(index.delete(id1));
        assertEquals(0, index.count());
        assertFalse(index.delete(id1));
        assertFalse(index.delete(FooEntity.of("hello")));
        assertEquals(0, index.count());

        index.validateStructure();
    }

    @Test
    public void testDeleteNodeWithTwoChildren() {
        FooEntityIndex index = createIndex();
        index.add(FooEntity.of("B"));
        index.add(FooEntity.of("A"));
        index.add(FooEntity.of("D"));
        index.add(FooEntity.of("C"));
        index.add(FooEntity.of("F"));
        index.add(FooEntity.of("E"));
        index.add(FooEntity.of("G"));

        index.delete(2);
        assertNull(index.get(2));
        assertEquals("B", index.get(0).key());
        assertEquals("F", index.get(4).key());
        assertEquals("E", index.get(5).key());
        index.validateStructure();
    }

    @Test
    public void testDeleteNodeWithTwoChildren2() {
        // Special case when the code swaps two nodes that have parent-child relation
        FooEntityIndex index = createIndex();
        index.add(FooEntity.of("B"));
        index.add(FooEntity.of("A"));
        index.add(FooEntity.of("D"));
        index.add(FooEntity.of("C"));
        index.add(FooEntity.of("E"));
        index.add(FooEntity.of("F"));

        index.delete(2);
        assertNull(index.get(2));
        assertEquals("B", index.get(0).key());
        assertEquals("C", index.get(3).key());
        assertEquals("E", index.get(4).key());
        assertEquals("F", index.get(5).key());
        index.validateStructure();
    }

    @Test
    public void testAddMultipleEntitiesAndThenDeleteThem() {
        for (int iter = 0; iter < 100; iter++) {
            FooEntityIndex index = createIndex();
            int count = 100;
            ArrayList<Integer> idsLeft = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                index.add(FooEntity.of(nextRandomString()));
                idsLeft.add(i);
            }
            Random random = new Random(0);
            for (int i = 0; i < count; i++) {
                int j = random.nextInt(idsLeft.size());
                int ix = idsLeft.get(j);
                idsLeft.remove(j);

                index.delete(ix);
                List<Integer> actualLeft = index.getAll().stream().map(FooEntity::id).collect(Collectors.toList());
                assertEquals(idsLeft, actualLeft);
                assertEquals(count - i - 1, index.count());
                index.validateStructure();
            }
        }
    }

    @Test
    public void testRandomlyAddAndDeleteEntities() {
        for (int iter = 0; iter < 10; iter++) {
            FooEntityIndex index = createIndex();
            ArrayList<String> list = new ArrayList<>();
            int noOps = 1000;
            for (int ops = 0; ops < noOps; ops++) {
                if (random.nextDouble() < 1-(double) ops/noOps || list.size() == 0) {
                    String key = nextRandomString();
                    list.add(key);
                    index.add(FooEntity.of(key));
                } else {
                    int i = random.nextInt(list.size());
                    String key = list.get(i);
                    index.delete(FooEntity.of(key));
                    list.remove(i);
                }
                assertEquals(list.size(), index.count());
                index.validateStructure();
            }
        }
    }

    @Test
    public void testAddRemoveDuplicateEntities() {
        // Test that adding and removing a lot of entities with duplicate keys don't cause problems
        // There was earlier assumptions in the code that all keys had to be unique
        int noOps = 200;
        FooEntityIndex index = createIndex();
        String[] candidates = {"foo", "bar", "abcde", "xyz", "test"};
        ArrayList<String> expected = new ArrayList<>();
        ArrayList<Integer> expectedIds = new ArrayList<>();
        HashMap<Integer, String> idEntityMap = new HashMap<>();
        for (int ops = 0; ops < noOps; ops++) {
            String key = candidates[random.nextInt(candidates.length)];
            int id = index.add(FooEntity.of(key));
            idEntityMap.put(id, key);
            expected.add(key);
            expectedIds.add(id);
            Collections.sort(expected);

            index.validateStructure();
            List<String> actual = index.getAllOrdered().stream().map(FooEntity::key).collect(Collectors.toList());
            assertEquals(expected, actual);
        }

        while (expectedIds.size() > 0) {
            int i = random.nextInt(expectedIds.size());
            int id = expectedIds.get(i);
            expected.remove(expected.indexOf(idEntityMap.get(id)));
            expectedIds.remove(i);
            // System.out.println("Deleting id " + id);
            index.delete(id);

            index.validateStructure();
            List<Integer> actualIds = index.getAll().stream().map(FooEntity::id).collect(Collectors.toList());
            assertEquals(expectedIds, actualIds);

            List<String> actual = index.getAllOrdered().stream().map(FooEntity::key).collect(Collectors.toList());
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testReplaceNodeWithSameKey() {
        FooEntityIndex index = createIndex();
        index.add(FooEntity.of("b"));
        index.add(FooEntity.of("a"));
        index.add(FooEntity.of("c"));

        index.put(FooEntity.of("b"));
        assertEquals(3, index.count());
        index.validateStructure();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReplaceNodeWithDuplicateKey() {
        FooEntityIndex index = createIndex();
        index.add(FooEntity.of("b"));
        index.add(FooEntity.of("a"));
        index.add(FooEntity.of("b"));
        index.add(FooEntity.of("c"));

        assertEquals(4, index.count());
        index.put(FooEntity.of("b"));
    }

    @Test
    public void testReplaceDuplicateKeyNodeById() {
        FooEntityIndex index = createIndex();
        index.add(FooEntity.of("b"));
        index.add(FooEntity.of("a"));
        int id = index.add(FooEntity.of("b"));
        index.add(FooEntity.of("c"));

        assertEquals(4, index.count());
        index.put(id, FooEntity.of("e"));
        List<String> result = index.getAllOrdered().stream().map(FooEntity::key).collect(Collectors.toList());
        assertEquals(Arrays.asList("a", "b", "c", "e"), result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReplaceNodeWithMissingKey() {
        FooEntityIndex index = createIndex();
        index.add(FooEntity.of("b"));

        index.put(FooEntity.of("c"));
    }

    @Test
    public void testReplaceNodeWithNewKey() {
        FooEntityIndex index = createIndex();
        index.add(FooEntity.of("b"));
        index.add(FooEntity.of("a"));
        index.add(FooEntity.of("c"));

        index.put(0, FooEntity.of("d"));
        assertEquals(3, index.count());
        assertEquals("d", index.get(0).key());
        index.validateStructure();
    }

    @Test
    public void testReplaceNodeWithNewKeyThatAlreadyExists() {
        FooEntityIndex index = createIndex();
        int id = index.add(FooEntity.of("b"));
        index.add(FooEntity.of("a"));

        FooEntity replaceEntity = FooEntity.of("a");
        index.put(id, replaceEntity);

        List<String> result = index.getAll().stream().map(FooEntity::key).collect(Collectors.toList());
        assertEquals(Arrays.asList("a", "a"), result);
    }

    @Test
    public void testStreamOrderedAscending() {
        FooEntityIndex index = createIndex();
        String[] values = {"d", "t", "b", "e", "q", "w", "a", "l", "c", "v"};
        for (String value : values) {
            index.add(FooEntity.of(value));
        }

        String result = index.streamOrderedAscending()
                .map(FooEntity::key)
                .collect(Collectors.joining());
        assertEquals("abcdelqtvw", result);

        result = index.streamOrderedAscending(FooEntity.of("f"))
                .map(FooEntity::key)
                .collect(Collectors.joining());
        assertEquals("lqtvw", result);

        result = index.streamOrderedAscending(FooEntity.of("q"))
                .map(FooEntity::key)
                .collect(Collectors.joining());
        assertEquals("qtvw", result);
    }

    @Test
    public void testAscendingStreamOverEmptyStorage() {
        FooEntityIndex storage = createIndex();

        assertEquals(0, storage.streamOrderedAscending().count());
        assertEquals(0, storage.streamOrderedAscending(FooEntity.of("a")).count());
    }

    @Test
    public void testAscendingStreamOverSingleNodeStorage() {
        FooEntityIndex storage = createIndex();
        storage.add(FooEntity.of("e"));

        // No start marker
        assertEquals("e", storage.streamOrderedAscending().map(FooEntity::key).collect(Collectors.joining()));

        // Start before first
        assertEquals("e", storage.streamOrderedAscending(FooEntity.of("b")).map(FooEntity::key).collect(Collectors.joining()));

        // Start same as first
        assertEquals("e", storage.streamOrderedAscending(FooEntity.of("e")).map(FooEntity::key).collect(Collectors.joining()));

        // Start after as first
        assertEquals("", storage.streamOrderedAscending(FooEntity.of("g")).map(FooEntity::key).collect(Collectors.joining()));
    }

    @Test
    public void testOrderedDescendingStream() {
        FooEntityIndex index = createIndex();
        String[] values = {"d", "t", "b", "e", "q", "w", "a", "l", "c", "v"};
        for (String value : values) {
            index.add(FooEntity.of(value));
        }

        assertEquals("wvtqledcba", index.streamOrderedDescending().map(FooEntity::key).collect(Collectors.joining()));

        assertEquals("edcba", index.streamOrderedDescending(FooEntity.of("f")).map(FooEntity::key).collect(Collectors.joining()));

        assertEquals("qledcba", index.streamOrderedDescending(FooEntity.of("q")).map(FooEntity::key).collect(Collectors.joining()));
    }

    @Test
    public void testStreamWithDuplicateEntries() {
        FooEntityIndex index = createIndex();
        String[] values = {"d", "e", "b", "e", "q", "w", "a", "l", "c", "v", "x", "e", "b", "t" };
        for (String value : values) {
            index.add(FooEntity.of(value));
        }

        assertEquals("abbcdeeelqtvwx", index.streamOrderedAscending().map(FooEntity::key).collect(Collectors.joining()));
        assertEquals("xwvtqleeedcbba", index.streamOrderedDescending().map(FooEntity::key).collect(Collectors.joining()));
    }

    @Test
    public void testStreamOverEmptyStorage() {
        FooEntityIndex index = createIndex();
        assertEquals(0, index.stream().count());

        index.add(FooEntity.of("hello"));
        index.delete(0);

        assertEquals(0, index.stream().count());
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

        assertEquals("dtbqwalv", index.stream().map(FooEntity::key).collect(Collectors.joining()));
    }

    @Test
    public void testDefaultIteratorWhenStorageChanges() {
        // This is ok because we're not depending on the node tree so we don't have
        // to be worried about doing weird things in case of a race with a transaction
        FooEntityIndex index = createIndex();
        index.add(FooEntity.of("a"));
        index.add(FooEntity.of("b"));
        Iterator<FooEntity> iterator = index.iterable().iterator();
        assertEquals("a", iterator.next().key());
        index.add(FooEntity.of("c"));
        iterator.next();
    }

    @Test(expected = IllegalStateException.class)
    public void testOrderedIteratorWhenStorageChanges() {
        FooEntityIndex index = createIndex();
        index.add(FooEntity.of("a"));
        index.add(FooEntity.of("b"));
        Iterator<FooEntity> iterator = index.streamOrderedAscending().iterator();
        assertEquals("a", iterator.next().key());
        index.add(FooEntity.of("c"));
        iterator.next();
    }

    @Test
    public void testIteratorOverMultipleBatches() {
        FooEntityIndex storage = createIndex();
        int expected = 3876;
        for (int i = 0; i < expected; i++) {
            storage.add(FooEntity.of(nextRandomString()));
        }

        assertEquals(expected, storage.stream().count());
    }
/*
    @Test
    public void testOpenInMemory() {
        File file = folder.newFile();
        file.delete();
        EntityIndex<FooEntity> diskIndex = EntityStorageImpl.create(file, new TestEntitySerializer());
        String[] values = {"d", "t", "b", "e", "q", "w", "a", "l", "c", "v"};
        for (String value : values) {
            diskIndex.addEntity(FooEntity.of(value));
        }
        diskIndex.delete(3);
        diskIndex.delete(8);
        diskIndex.close();

        EntityIndex<FooEntity> index = EntityStorageImpl.openInMemory(file, new TestEntitySerializer());
        assertEquals("d", index.get(0).key());
        assertEquals("b", index.get(2).key());
        assertEquals("q", index.get(4).key());
        assertEquals("l", index.get(7).key());
        assertEquals("v", index.get(9).key());
        assertNull(index.get(3));
        assertNull(index.get(8));
        assertEquals(values.length - 2, index.count());
    }


    @Test
    public void testUseStorageWithShorterEntityLength() {
        // Test that we can open and work with a storage where the entity size is different than expected
        // This corresponds to having a new version of the database reading older database files.
        // The expected outcome is that new fields will have their new fields truncated when written.
        File file = folder.newFile();
        file.delete();

        // Add two entities as an old database with shorter entity size
        EntityIndex<FooEntity> oldDb = EntityStorageImpl.create(file, new TestEntitySerializer());
        FooEntity key = FooEntity.builder().key("a").value(7).build();
        oldDb.add(key);
        key = FooEntity.builder().key("b").value(9).build();
        oldDb.add(key);
        oldDb.close();

        // Open as a new database with longer entity size and check that we can read the data
        EntityIndex<FooBarEntity> newDb = EntityStorageImpl.open(file, new TestEntityv2Serializer());
        assertEquals(2, newDb.count());
        FooBarEntity e2 = newDb.get(0);
        assertEquals(e2.key(), "a");
        assertEquals(e2.value(), 7);
        assertEquals(e2.extraValue(), 0);
        assertEquals(e2.extraString(), "");

        FooBarEntity e3 = newDb.get(1);
        assertEquals(e3.key(), "b");
        assertEquals(e3.value(), 9);
        assertEquals(e3.extraValue(), 0);
        assertEquals(e3.extraString(), "");

        // Add one new entity, replace one and deleted one as the new database
        FooBarEntity key2 = FooBarEntity.builder().key("c").value(12).extraValue(19).extraString("truncated").build();
        newDb.add(key2);
        key2 = e2.toBuilder().value(-8).extraValue(100).extraString("also removed").build();
        newDb.putEntityById(0, key2);
        newDb.delete(1);

        FooBarEntity key3 = newDb.get(0);
        assertEquals(-8, key3.value());
        assertEquals(0, key3.extraValue());
        assertEquals("", key3.extraString());
        key3 = newDb.get(2);
        assertEquals(12, key3.value());
        assertEquals(0, key3.extraValue());
        assertEquals("", key3.extraString());

        newDb.close();

        // Check that we can read the new entities as the old database
        oldDb = EntityStorageImpl.open(file, new TestEntitySerializer());
        FooEntity key5 = oldDb.get(0);
        assertEquals("a", key5.key());
        assertEquals(-8, key5.value());
        key5 = oldDb.get(2);
        assertEquals("c", key5.key());
        assertEquals(12, key5.value());
        assertNull(oldDb.get(1));
        oldDb.close();
    }

    @Test
    public void testUseStorageWithLongerEntityLength() {
        // Test that we can open and work with a storage where the entity size is larger than expected
        // This corresponds to having an old version of the database reading and working with
        // database files from a newer version.
        // The expected outcome is that unknown fields will be untouched when updating entities,
        // or empty if writing new ones.

        File file = folder.newFile();
        file.delete();

        // Add two entities as a new database with longer entity size
        EntityIndex<FooBarEntity> newDb = EntityStorageImpl.create(file, new TestEntityv2Serializer());
        FooBarEntity key = FooBarEntity.builder().key("a").value(7).extraValue(10).extraString("hello").build();
        newDb.add(key);
        key = FooBarEntity.builder().key("b").value(9).extraValue(1).extraString("world").build();
        newDb.add(key);
        newDb.close();

        // Open as an old database with shorter entity size and check that we can read the data
        EntityIndex<FooEntity> oldDb = EntityStorageImpl.open(file, new TestEntitySerializer());
        assertEquals(2, oldDb.count());
        FooEntity e2 = oldDb.get(0);
        assertEquals(e2.key(), "a");
        assertEquals(e2.value(), 7);

        FooEntity e3 = oldDb.get(1);
        assertEquals(e3.key(), "b");
        assertEquals(e3.value(), 9);

        // Add one new entity, replace one and delete one as the old database
        FooEntity key2 = FooEntity.builder().key("c").value(12).build();
        oldDb.add(key2);
        key2 = e2.toBuilder().value(-8).build();
        oldDb.putEntityById(0, key2);
        oldDb.delete(1);

        FooEntity key3 = oldDb.get(0);
        assertEquals(-8, key3.value());

        oldDb.close();

        // Check that we can read the new entities as the new database
        // Also check that the entity that was updated didn't get the extra field modified
        newDb = EntityStorageImpl.open(file, new TestEntityv2Serializer());
        FooBarEntity key5 = newDb.get(0);
        assertEquals("a", key5.key());
        assertEquals(-8, key5.value());
        assertEquals(10, key5.extraValue());
        assertEquals("hello", key5.extraString());
        key5 = newDb.get(2);
        assertEquals("c", key5.key());
        assertEquals(12, key5.value());
        assertEquals(0, key5.extraValue());
        assertEquals("", key5.extraString());
        assertNull(newDb.get(1));
        newDb.close();
    }

    @Test
    public void testUseStorageWithDifferentHeaderSize() {
        File file = folder.newFile();
        file.delete();

        EntityIndex<FooEntity> oldDb = EntityStorageImpl.create(file, new TestEntitySerializer(), 28);
        FooEntity hello = FooEntity.builder().key("hello").value(5).build();
        oldDb.add(hello);
        oldDb.close();

        EntityIndex<FooEntity> newDb = EntityStorageImpl.open(file, new TestEntitySerializer());
        FooEntity entity = newDb.get(0);
        assertEquals("hello", entity.key());
        assertEquals(5, entity.value());
    }

    @Test
    public void testDuplicate() {
        EntityIndex<FooEntity> storage = createIndex();
        storage.add(FooEntity.of("foo"));
        storage.add(FooEntity.of("bar"));
        storage.add(FooEntity.of("xyz"));
        storage.add(FooEntity.of("abc"));
        storage.add(FooEntity.of("chess"));

        storage.validateStructure();

        List<String> collect = storage.getAllEntities(true).stream().map(FooEntity::key).collect(Collectors.toList());
        assertEquals(Arrays.asList("abc", "bar", "chess", "foo", "xyz"), collect);

        File file = folder.newFile();
        file.delete();

        EntityIndex<FooEntity> duplicateStorage = storage.duplicate(file, new TestEntitySerializer());
        duplicateStorage.validateStructure();
        List<String> collect2 = duplicateStorage.getAllEntities(true).stream().map(FooEntity::key).collect(Collectors.toList());
        assertEquals(Arrays.asList("abc", "bar", "chess", "foo", "xyz"), collect2);

        // Had a bug that caused the return storage from duplicate be okay but not when loading from file! (metadata not being saved)
        EntityIndex<FooEntity> clonedStorage = EntityStorageImpl.open(file, new TestEntitySerializer());
        clonedStorage.validateStructure();
        List<String> collect3 = clonedStorage.getAllEntities(true).stream().map(FooEntity::key).collect(Collectors.toList());
        assertEquals(Arrays.asList("abc", "bar", "chess", "foo", "xyz"), collect3);
    }
     */
}
