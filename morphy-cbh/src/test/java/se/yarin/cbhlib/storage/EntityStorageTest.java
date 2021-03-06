package se.yarin.cbhlib.storage;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.storage.transaction.EntityStorage;
import se.yarin.cbhlib.storage.transaction.EntityStorageImpl;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        TestEntity hello = TestEntity.builder().key("hello").value(7).build();
        int id = storage.addEntity(hello);
        assertEquals(1, storage.getNumEntities());

        TestEntity entity = storage.getEntity(id);
        assertEquals("hello", entity.getKey());
        assertEquals(0, entity.getId());
        assertEquals(7, entity.getValue());

        storage.validateStructure();
    }

    @Test
    public void testAddTwoEntities() throws IOException, EntityStorageException {
        // Should cause no rotation
        EntityStorage<TestEntity> storage = createStorage();

        storage.addEntity(TestEntity.builder().key("a").build());
        storage.addEntity(TestEntity.builder().key("b").build());

        storage.validateStructure();
    }

    @Test
    public void testAddThreeEntitiesSingleRotate() throws IOException, EntityStorageException {
        // Should cause a left single rotation
        EntityStorage<TestEntity> storage = createStorage();

        storage.addEntity(TestEntity.builder().key("a").build());
        storage.addEntity(TestEntity.builder().key("b").build());
        storage.addEntity(TestEntity.builder().key("c").build());

        storage.validateStructure();
    }

    @Test
    public void testAddThreeEntitiesDoubleRotate() throws IOException, EntityStorageException {
        // Should cause a right-left double rotation
        EntityStorage<TestEntity> storage = createStorage();

        storage.addEntity(TestEntity.builder().key("a").build());
        storage.addEntity(TestEntity.builder().key("c").build());
        storage.addEntity(TestEntity.builder().key("b").build());

        storage.validateStructure();
    }

    @Test
    public void testAddSixEntities() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();

        storage.addEntity(TestEntity.builder().key("e").build());
        storage.addEntity(TestEntity.builder().key("f").build());
        storage.addEntity(TestEntity.builder().key("b").build());
        storage.addEntity(TestEntity.builder().key("d").build());
        storage.addEntity(TestEntity.builder().key("a").build());
        storage.addEntity(TestEntity.builder().key("c").build());

        storage.validateStructure();
    }

    @Test
    public void testGetEntityByKey() throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = createStorage();
        TestEntity hello = TestEntity.builder().key("hello").value(7).build();
        storage.addEntity(hello);

        TestEntity entity = storage.getEntity(new TestEntity("hello"));
        assertEquals("hello", entity.getKey());
        assertEquals(0, entity.getId());
        assertEquals(7, entity.getValue());
    }

    @Test
    public void testGetMissingEntityByKey() throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = createStorage();

        assertNull(storage.getEntity(new TestEntity("world")));

        TestEntity hello = TestEntity.builder().key("hello").value(7).build();
        storage.addEntity(hello);

        assertNull(storage.getEntity(new TestEntity("world")));
    }

    @Test
    public void testGetAnyEntityByKey() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(TestEntity.builder().key("foo").value(7).build());
        storage.addEntity(TestEntity.builder().key("x").value(2).build());
        storage.addEntity(TestEntity.builder().key("bar").value(5).build());
        storage.addEntity(TestEntity.builder().key("y").value(9).build());
        storage.addEntity(TestEntity.builder().key("foo").value(11).build());
        TestEntity foo = storage.getAnyEntity(new TestEntity("foo"));
        assertTrue(foo.getValue() == 7 || foo.getValue() == 11);
    }

    @Test(expected = EntityStorageDuplicateKeyException.class)
    public void testGetDuplicateEntityByKey() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(TestEntity.builder().key("foo").value(7).build());
        storage.addEntity(TestEntity.builder().key("x").value(2).build());
        storage.addEntity(TestEntity.builder().key("bar").value(5).build());
        storage.addEntity(TestEntity.builder().key("y").value(9).build());
        storage.addEntity(TestEntity.builder().key("foo").value(11).build());
        storage.getEntity(new TestEntity("foo"));
    }

    @Test
    public void testGetDuplicateEntitiesByKey() throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(TestEntity.builder().key("foo").value(7).build());
        storage.addEntity(TestEntity.builder().key("x").value(2).build());
        storage.addEntity(TestEntity.builder().key("bar").value(5).build());
        storage.addEntity(TestEntity.builder().key("y").value(9).build());
        storage.addEntity(TestEntity.builder().key("foo").value(11).build());
        List<TestEntity> result = storage.getEntities(new TestEntity("foo"));
        assertEquals(2, result.size());

        int v1 = result.get(0).getValue();
        int v2 = result.get(1).getValue();
        assertTrue(Math.min(v1, v2) == 7 && Math.max(v1, v2) == 11);
    }

    @Test
    public void testGetDuplicateEntitiesByKeyRandomInserts() throws IOException, EntityStorageException {
        // Add 1000 random entities and ensure that when fetching entities by key, we get the correct amount each time.
        // Since all entities will have 1 of the 5 candidate keys, there will be lots and lots of duplicates.
        int noOps = 1000;
        String[] candidates = new String[] {"foo", "bar", "xyz", "a", "zzz"};
        int[] expectedCount = new int[candidates.length];
        EntityStorage<TestEntity> storage = createStorage();
        Random random = new Random();
        for (int i = 0; i < noOps; i++) {
            int x = random.nextInt(candidates.length);
            storage.addEntity(new TestEntity(candidates[x]));
            expectedCount[x] += 1;

            for (int j = 0; j < candidates.length; j++) {
                int actual = storage.getEntities(new TestEntity(candidates[j])).size();
                assertEquals(expectedCount[j], actual);
            }
        }
    }

    @Test
    public void testAddMultipleEntities() throws IOException, EntityStorageException {
        for (int iter = 0; iter < 100; iter++) {
            EntityStorage<TestEntity> storage = createStorage();
            for (int i = 0; i < 100; i++) {
                int id = storage.addEntity(new TestEntity(nextRandomString()));
                assertEquals(i, id);
                assertEquals(i + 1, storage.getNumEntities());
                storage.validateStructure();
            }
        }
    }

    @Test
    public void testDeleteEntity() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        int id1 = storage.addEntity(new TestEntity("hello"));
        int id2 = storage.addEntity(new TestEntity("world"));

        assertTrue(storage.deleteEntity(id1));

        assertEquals(1, storage.getNumEntities());
        assertNull(storage.getEntity(id1));
        assertNotNull(storage.getEntity(id2));

        storage.validateStructure();
    }

    @Test
    public void testRebalancingAfterDeleteEntity() throws IOException, EntityStorageException {
        for (int i = 0; i < 7; i++) {
            EntityStorage<TestEntity> storage = createStorage();
            storage.addEntity(new TestEntity("e"));
            storage.addEntity(new TestEntity("b"));
            storage.addEntity(new TestEntity("f"));
            storage.addEntity(new TestEntity("a"));
            storage.addEntity(new TestEntity("d"));
            storage.addEntity(new TestEntity("g"));
            storage.addEntity(new TestEntity("c"));
            storage.deleteEntity(i);
            storage.validateStructure();
        }
    }

    @Test
    public void testRebalancingAfterDeleteEntity2() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity("d"));
        storage.addEntity(new TestEntity("e"));
        storage.addEntity(new TestEntity("b"));
        storage.addEntity(new TestEntity("f"));
        storage.addEntity(new TestEntity("a"));
        storage.addEntity(new TestEntity("c"));
        storage.deleteEntity(0);
        storage.deleteEntity(2);
        storage.validateStructure();
    }

    @Test
    public void testDeleteEntityByKey() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        int id1 = storage.addEntity(new TestEntity("hello"));
        int id2 = storage.addEntity(new TestEntity("world"));

        assertTrue(storage.deleteEntity(new TestEntity("world")));

        assertEquals(1, storage.getNumEntities());
        assertNull(storage.getEntity(id2));
        assertNotNull(storage.getEntity(id1));

        storage.validateStructure();
    }

    @Test(expected = EntityStorageDuplicateKeyException.class)
    public void testDeleteDuplicatedEntityByKey() throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity("foo"));
        storage.addEntity(new TestEntity("bar"));
        storage.addEntity(new TestEntity("foo"));

        storage.deleteEntity(new TestEntity("foo"));
    }

    @Test
    public void testDeleteDeletedEntity() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        int id1 = storage.addEntity(new TestEntity("hello"));
        assertTrue(storage.deleteEntity(id1));
        assertEquals(0, storage.getNumEntities());
        assertFalse(storage.deleteEntity(id1));
        assertFalse(storage.deleteEntity(new TestEntity("hello")));
        assertEquals(0, storage.getNumEntities());

        storage.validateStructure();
    }

    @Test
    public void testDeleteNodeWithTwoChildren() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity("B"));
        storage.addEntity(new TestEntity("A"));
        storage.addEntity(new TestEntity("D"));
        storage.addEntity(new TestEntity("C"));
        storage.addEntity(new TestEntity("F"));
        storage.addEntity(new TestEntity("E"));
        storage.addEntity(new TestEntity("G"));

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
        storage.addEntity(new TestEntity("B"));
        storage.addEntity(new TestEntity("A"));
        storage.addEntity(new TestEntity("D"));
        storage.addEntity(new TestEntity("C"));
        storage.addEntity(new TestEntity("E"));
        storage.addEntity(new TestEntity("F"));

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
        for (int iter = 0; iter < 100; iter++) {
            EntityStorage<TestEntity> storage = createStorage();
            int count = 100;
            ArrayList<Integer> idsLeft = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                storage.addEntity(new TestEntity(nextRandomString()));
                idsLeft.add(i);
            }
            Random random = new Random(0);
            for (int i = 0; i < count; i++) {
                int j = random.nextInt(idsLeft.size());
                int ix = idsLeft.get(j);
                idsLeft.remove(j);

                storage.deleteEntity(ix);
                List<Integer> actualLeft = storage.getAllEntities(false).stream().map(TestEntity::getId).collect(Collectors.toList());
                assertEquals(idsLeft, actualLeft);
                assertEquals(count - i - 1, storage.getNumEntities());
                storage.validateStructure();
            }
        }
    }

    @Test
    public void testRandomlyAddAndDeleteEntities() throws EntityStorageException, IOException {
        for (int iter = 0; iter < 10; iter++) {
            EntityStorage<TestEntity> storage = createStorage();
            ArrayList<String> list = new ArrayList<>();
            int noOps = 1000;
            for (int ops = 0; ops < noOps; ops++) {
                if (random.nextDouble() < 1-(double) ops/noOps || list.size() == 0) {
                    String key = nextRandomString();
                    list.add(key);
                    storage.addEntity(new TestEntity(key));
                } else {
                    int i = random.nextInt(list.size());
                    String key = list.get(i);
                    storage.deleteEntity(new TestEntity(key));
                    list.remove(i);
                }
                assertEquals(list.size(), storage.getNumEntities());
                storage.validateStructure();
            }
        }
    }

    @Test
    public void testAddRemoveDuplicateEntities() throws IOException, EntityStorageException {
        // Test that adding and removing a lot of entities with duplicate keys don't cause problems
        // There was earlier assumptions in the code that all keys had to be unique
        int noOps = 200;
        EntityStorage<TestEntity> storage = createStorage();
        String[] candidates = {"foo", "bar", "abcde", "xyz", "test"};
        ArrayList<String> expected = new ArrayList<>();
        ArrayList<Integer> expectedIds = new ArrayList<>();
        HashMap<Integer, String> idEntityMap = new HashMap<>();
        for (int ops = 0; ops < noOps; ops++) {
            String key = candidates[random.nextInt(candidates.length)];
            int id = storage.addEntity(new TestEntity(key));
            idEntityMap.put(id, key);
            expected.add(key);
            expectedIds.add(id);
            Collections.sort(expected);

            storage.validateStructure();
            List<String> actual = storage.getAllEntities(true).stream().map(TestEntity::getKey).collect(Collectors.toList());
            assertEquals(expected, actual);
        }

        while (expectedIds.size() > 0) {
            int i = random.nextInt(expectedIds.size());
            int id = expectedIds.get(i);
            expected.remove(expected.indexOf(idEntityMap.get(id)));
            expectedIds.remove(i);
            // System.out.println("Deleting id " + id);
            storage.deleteEntity(id);

            storage.validateStructure();
            List<Integer> actualIds = storage.getAllEntities(false).stream().map(TestEntity::getId).collect(Collectors.toList());
            assertEquals(expectedIds, actualIds);

            List<String> actual = storage.getAllEntities(true).stream().map(TestEntity::getKey).collect(Collectors.toList());
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testReplaceNodeWithSameKey() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity("b"));
        storage.addEntity(new TestEntity("a"));
        storage.addEntity(new TestEntity("c"));

        storage.putEntityByKey(new TestEntity("b"));
        assertEquals(3, storage.getNumEntities());
        storage.validateStructure();
    }

    @Test(expected = EntityStorageDuplicateKeyException.class)
    public void testReplaceNodeWithDuplicateKey() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity("b"));
        storage.addEntity(new TestEntity("a"));
        storage.addEntity(new TestEntity("b"));
        storage.addEntity(new TestEntity("c"));

        assertEquals(4, storage.getNumEntities());
        storage.putEntityByKey(new TestEntity("b"));
    }

    @Test
    public void testReplaceDuplicateKeyNodeById() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity("b"));
        storage.addEntity(new TestEntity("a"));
        int id = storage.addEntity(new TestEntity("b"));
        storage.addEntity(new TestEntity("c"));

        assertEquals(4, storage.getNumEntities());
        storage.putEntityById(id, new TestEntity("e"));
        List<String> result = storage.getAllEntities(true).stream().map(TestEntity::getKey).collect(Collectors.toList());
        assertEquals(Arrays.asList("a", "b", "c", "e"), result);
    }

    @Test(expected = EntityStorageException.class)
    public void testReplaceNodeWithMissingKey() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity("b"));

        storage.putEntityByKey(new TestEntity("c"));
    }

    @Test
    public void testReplaceNodeWithNewKey() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity("b"));
        storage.addEntity(new TestEntity("a"));
        storage.addEntity(new TestEntity("c"));

        storage.putEntityById(0, new TestEntity("d"));
        assertEquals(3, storage.getNumEntities());
        assertEquals("d", storage.getEntity(0).getKey());
        storage.validateStructure();
    }

    @Test
    public void testReplaceNodeWithNewKeyThatAlreadyExists() throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = createStorage();
        int id = storage.addEntity(new TestEntity("b"));
        storage.addEntity(new TestEntity("a"));

        TestEntity replaceEntity = TestEntity.builder().id(id).key("a").build();
        storage.putEntityById(id, replaceEntity);

        List<String> result = storage.getAllEntities(false).stream().map(TestEntity::getKey).collect(Collectors.toList());
        assertEquals(Arrays.asList("a", "a"), result);
    }

    @Test(expected = NullPointerException.class)
    public void testAddEntityWhenMandatoryFieldIsNotSet() throws EntityStorageException, IOException {
        EntityStorage<TestEntityv2> storage = EntityStorageImpl.createInMemory();
        storage.addEntity(TestEntityv2.builder().key("b").build());
    }


    @Test
    public void testStreamOrderedAscending() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        String[] values = {"d", "t", "b", "e", "q", "w", "a", "l", "c", "v"};
        for (String value : values) {
            storage.addEntity(new TestEntity(value));
        }

        String result = storage.streamOrderedAscending()
                .map(TestEntity::getKey)
                .collect(Collectors.joining());
        assertEquals("abcdelqtvw", result);

        result = storage.streamOrderedAscending(new TestEntity("f"))
                .map(TestEntity::getKey)
                .collect(Collectors.joining());
        assertEquals("lqtvw", result);

        result = storage.streamOrderedAscending(new TestEntity("q"))
                .map(TestEntity::getKey)
                .collect(Collectors.joining());
        assertEquals("qtvw", result);
    }

    @Test
    public void testAscendingStreamOverEmptyStorage() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();

        assertEquals(0, storage.streamOrderedAscending().count());
        assertEquals(0, storage.streamOrderedAscending(new TestEntity("a")).count());
    }

    @Test
    public void testAscendingStreamOverSingleNodeStorage() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity("e"));

        // No start marker
        assertEquals("e", storage.streamOrderedAscending().map(TestEntity::getKey).collect(Collectors.joining()));

        // Start before first
        assertEquals("e", storage.streamOrderedAscending(new TestEntity("b")).map(TestEntity::getKey).collect(Collectors.joining()));

        // Start same as first
        assertEquals("e", storage.streamOrderedAscending(new TestEntity("e")).map(TestEntity::getKey).collect(Collectors.joining()));

        // Start after as first
        assertEquals("", storage.streamOrderedAscending(new TestEntity("g")).map(TestEntity::getKey).collect(Collectors.joining()));
    }

    @Test
    public void testOrderedDescendingStream() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        String[] values = {"d", "t", "b", "e", "q", "w", "a", "l", "c", "v"};
        for (String value : values) {
            storage.addEntity(new TestEntity(value));
        }

        assertEquals("wvtqledcba", storage.streamOrderedDescending(null).map(TestEntity::getKey).collect(Collectors.joining()));

        assertEquals("edcba", storage.streamOrderedDescending(new TestEntity("f")).map(TestEntity::getKey).collect(Collectors.joining()));

        assertEquals("qledcba", storage.streamOrderedDescending(new TestEntity("q")).map(TestEntity::getKey).collect(Collectors.joining()));
    }

    @Test
    public void testStreamWithDuplicateEntries() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        String[] values = {"d", "e", "b", "e", "q", "w", "a", "l", "c", "v", "x", "e", "b", "t" };
        for (String value : values) {
            storage.addEntity(new TestEntity(value));
        }

        assertEquals("abbcdeeelqtvwx", storage.streamOrderedAscending().map(TestEntity::getKey).collect(Collectors.joining()));
        assertEquals("xwvtqleeedcbba", storage.streamOrderedDescending(null).map(TestEntity::getKey).collect(Collectors.joining()));
    }

    @Test
    public void testStreamOverEmptyStorage() throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = createStorage();
        assertEquals(0, storage.stream().count());

        storage.addEntity(new TestEntity("hello"));
        storage.deleteEntity(0);

        assertEquals(0, storage.stream().count());
    }

    @Test
    public void testStream() throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = createStorage();
        String[] values = {"d", "t", "b", "e", "q", "w", "a", "l", "c", "v"};
        for (String value : values) {
            storage.addEntity(new TestEntity(value));
        }
        storage.deleteEntity(3);
        storage.deleteEntity(8);

        assertEquals("dtbqwalv", storage.stream().map(TestEntity::getKey).collect(Collectors.joining()));
    }

    @Test(expected = IllegalStateException.class)
    public void testDefaultIteratorWhenStorageChanges() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity("a"));
        storage.addEntity(new TestEntity("b"));
        Iterator<TestEntity> iterator = storage.iterable().iterator();
        assertEquals("a", iterator.next().getKey());
        storage.addEntity(new TestEntity("c"));
        iterator.next();
    }

    @Test(expected = IllegalStateException.class)
    public void testOrderedIteratorWhenStorageChanges() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity("a"));
        storage.addEntity(new TestEntity("b"));
        Iterator<TestEntity> iterator = storage.streamOrderedAscending().iterator();
        assertEquals("a", iterator.next().getKey());
        storage.addEntity(new TestEntity("c"));
        iterator.next();
    }

    @Test
    public void testIteratorOverMultipleBatches() throws EntityStorageException, IOException {
        EntityStorage<TestEntity> storage = createStorage();
        int expected = 3876;
        for (int i = 0; i < expected; i++) {
            storage.addEntity(new TestEntity(nextRandomString()));
        }

        assertEquals(expected, storage.stream().count());
    }

    @Test
    public void testOpenInMemory() throws IOException, EntityStorageException {
        File file = folder.newFile();
        file.delete();
        EntityStorage<TestEntity> diskStorage = EntityStorageImpl.create(file, new TestEntitySerializer());
        String[] values = {"d", "t", "b", "e", "q", "w", "a", "l", "c", "v"};
        for (String value : values) {
            diskStorage.addEntity(new TestEntity(value));
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
        TestEntity key = TestEntity.builder().key("a").value(7).build();
        oldDb.addEntity(key);
        key = TestEntity.builder().key("b").value(9).build();
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

        TestEntityv2 e3 = newDb.getEntity(1);
        assertEquals(e3.getKey(), "b");
        assertEquals(e3.getValue(), 9);
        assertEquals(e3.getExtraValue(), 0);
        assertEquals(e3.getExtraString(), "");

        // Add one new entity, replace one and deleted one as the new database
        TestEntityv2 key2 = TestEntityv2.builder().key("c").value(12).extraValue(19).extraString("truncated").build();
        newDb.addEntity(key2);
        key2 = e2.toBuilder().value(-8).extraValue(100).extraString("also removed").build();
        newDb.putEntityById(0, key2);
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
        TestEntityv2 key = TestEntityv2.builder().key("a").value(7).extraValue(10).extraString("hello").build();
        newDb.addEntity(key);
        key = TestEntityv2.builder().key("b").value(9).extraValue(1).extraString("world").build();
        newDb.addEntity(key);
        newDb.close();

        // Open as an old database with shorter entity size and check that we can read the data
        EntityStorage<TestEntity> oldDb = EntityStorageImpl.open(file, new TestEntitySerializer());
        assertEquals(2, oldDb.getNumEntities());
        TestEntity e2 = oldDb.getEntity(0);
        assertEquals(e2.getKey(), "a");
        assertEquals(e2.getValue(), 7);

        TestEntity e3 = oldDb.getEntity(1);
        assertEquals(e3.getKey(), "b");
        assertEquals(e3.getValue(), 9);

        // Add one new entity, replace one and delete one as the old database
        TestEntity key2 = TestEntity.builder().key("c").value(12).build();
        oldDb.addEntity(key2);
        key2 = e2.toBuilder().value(-8).build();
        oldDb.putEntityById(0, key2);
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
        TestEntity hello = TestEntity.builder().key("hello").value(5).build();
        oldDb.addEntity(hello);
        oldDb.close();

        EntityStorage<TestEntity> newDb = EntityStorageImpl.open(file, new TestEntitySerializer());
        TestEntity entity = newDb.getEntity(0);
        assertEquals("hello", entity.getKey());
        assertEquals(5, entity.getValue());
    }

    @Test
    public void testDuplicate() throws IOException, EntityStorageException {
        EntityStorage<TestEntity> storage = createStorage();
        storage.addEntity(new TestEntity("foo"));
        storage.addEntity(new TestEntity("bar"));
        storage.addEntity(new TestEntity("xyz"));
        storage.addEntity(new TestEntity("abc"));
        storage.addEntity(new TestEntity("chess"));

        storage.validateStructure();

        List<String> collect = storage.getAllEntities(true).stream().map(TestEntity::getKey).collect(Collectors.toList());
        assertEquals(Arrays.asList("abc", "bar", "chess", "foo", "xyz"), collect);

        File file = folder.newFile();
        file.delete();

        EntityStorage<TestEntity> duplicateStorage = storage.duplicate(file, new TestEntitySerializer());
        duplicateStorage.validateStructure();
        List<String> collect2 = duplicateStorage.getAllEntities(true).stream().map(TestEntity::getKey).collect(Collectors.toList());
        assertEquals(Arrays.asList("abc", "bar", "chess", "foo", "xyz"), collect2);

        // Had a bug that caused the return storage from duplicate be okay but not when loading from file! (metadata not being saved)
        EntityStorage<TestEntity> clonedStorage = EntityStorageImpl.open(file, new TestEntitySerializer());
        clonedStorage.validateStructure();
        List<String> collect3 = clonedStorage.getAllEntities(true).stream().map(TestEntity::getKey).collect(Collectors.toList());
        assertEquals(Arrays.asList("abc", "bar", "chess", "foo", "xyz"), collect3);
    }
}
