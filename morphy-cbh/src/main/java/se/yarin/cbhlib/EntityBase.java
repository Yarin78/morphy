package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.entities.*;
import se.yarin.cbhlib.entities.storage.EntitySerializer;
import se.yarin.cbhlib.entities.transaction.EntityStorage;
import se.yarin.cbhlib.entities.transaction.EntityStorageImpl;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public abstract class EntityBase<T extends Entity & Comparable<T>> implements EntitySerializer<T>, Iterable<T> {
    private static final Logger log = LoggerFactory.getLogger(EntityBase.class);

    private final EntityStorage<T> storage;

    // Cache entities by id to avoid having to deserialize the same entity again and again.
    // Only used when doing direct lookups, not when iterating over ranges.
    // TODO: Proper LRU cache to save memory
    private Map<Integer, T> cacheById = new HashMap<>();

    /**
     * Gets the underlying storage of the database.
     * @return an entity storage
     */
    protected EntityStorage<T> getStorage() {
        return storage;
    }

    protected EntityBase(@NonNull EntityStorage<T> storage) {
        this.storage = storage;
    }

    /**
     * Loads an entity database from file into an in-memory storage.
     * Any writes to the database will not be persisted to disk.
     * @param file the file to populate the in-memory database with
     * @param serializer the entity serializer
     * @param <T> the type of the entity
     * @return an open in-memory storage
     */
    protected static <T extends Entity & Comparable<T>> EntityStorage<T> loadInMemoryStorage(
            @NonNull File file, EntitySerializer<T> serializer) throws IOException {
        return EntityStorageImpl.openInMemory(file, serializer);
    }

    /**
     * Gets the number of entities in the database
     * @return the number of entities
     */
    public int getCount() {
        return storage.getNumEntities();
    }

    /**
     * Gets the allocated number of entities in the database; expanded on demand,
     * but typically not decreased when entities are deleted.
     * @return the number of entity entries
     */
    public int getCapacity() {
        return storage.getCapacity();
    }

    /**
     * Gets an entity by id
     * @param id the id of the entity (0-indexed)
     * @return the entity, or null if there was no entity with that id
     * @throws IOException if there was an IO error reading the entity
     */
    public T get(int id) throws IOException {
        if (cacheById.containsKey(id)) {
            return cacheById.get(id);
        }
        T entity = storage.getEntity(id);
        cacheById.put(id, entity);
        return entity;
    }

    /**
     * Gets an entity by key
     * @param entityKey the key of the entity
     * @return the entity, or null if there was no entity with that key
     * @throws IOException if there was an IO error reading the entity
     * @throws EntityStorageDuplicateKeyException if there are multiple entities with the given key
     */
    public T get(@NonNull T entityKey) throws IOException, EntityStorageDuplicateKeyException {
        return storage.getEntity(entityKey);
    }

    /**
     * Gets an entity by key. If there are multiple entities matching, returns one of them.
     * @param entityKey the key of the entity
     * @return the entity, or null if there was no entity with that key
     * @throws IOException if there was an IO error reading the entity
     */
    public T getAny(@NonNull T entityKey) throws IOException {
        return storage.getAnyEntity(entityKey);
    }

    /**
     * Gets all entities by key. Almost always this will be 0 or 1.
     * @param entityKey the key of the entity
     * @return all entities in the base with the given key
     * @throws IOException if there was an IO error reading the entity
     */
    public List<T> getAll(@NonNull T entityKey) throws IOException {
        return storage.getEntities(entityKey);
    }

    /**
     * Adds a new entity to the database
     * @param entity the entity to add
     * @return the added entity with the id set
     * @throws EntityStorageException if another entity with the same key exists
     * @throws IOException if some other IO error occurred
     */
    public T add(@NonNull T entity) throws EntityStorageException, IOException {
        int id = storage.addEntity(entity);
        return get(id);
    }

    /**
     * Updates an existing entity in the database
     * @param id the id of the entity to update
     * @param entity the new entity
     * @throws EntityStorageException if another entity with the same key exists
     * @throws IOException if some other IO error occurred
     */
    public void put(int id, @NonNull T entity) throws EntityStorageException, IOException {
        if (entity.getId() == -1) {
            throw new IllegalArgumentException("The id of the entity to update must be set");
        }
        cacheById.remove(id);
        storage.putEntityById(id, entity);
    }

    /**
     * Deletes an entity from the database
     * @param entityId the id of the entity to delete
     * @return true if the entity was deleted; false if there was no entity with that id in the database
     * @throws EntityStorageException if the database is in an inconsistent state preventing the delete
     * @throws IOException if some IO error occurred
     */
    public boolean delete(int entityId) throws IOException, EntityStorageException {
        cacheById.remove(entityId);
        return storage.deleteEntity(entityId);
    }

    /**
     * Deletes an entity from the database
     * @param entity the key of the entity to delete
     * @return true if the entity was deleted; false if there was no entity with that key in the database
     * @throws EntityStorageException if the database is in an inconsistent state preventing the delete
     * @throws IOException if some IO error occurred
     */
    public boolean delete(T entity) throws IOException, EntityStorageException {
        cacheById.remove(entity.getId());
        return storage.deleteEntity(entity);
    }

    /**
     * Gets a stream of all entities in the database, sorted by id.
     * If an error occurs while processing the stream, a {@link UncheckedEntityException} is thrown.
     * @return a stream of all entities
     */
    public Stream<T> streamAll() {
        return StreamSupport.stream(storage.spliterator(), false);
    }

    /**
     * Gets a list of all entities in the database
     * @return a list of all entities
     * @throws IOException if some IO error occurred reading the entities
     */
    public List<T> getAll() throws IOException {
        return storage.getAllEntities(false);
    }

    /**
     * Gets an iterator over all entities in the database in id order
     * @return an iterator
     */
    public Iterator<T> iterator() {
        return storage.iterator();
    }

    /**
     * Gets an iterator over all entities in the database in ascending default sort order
     * @return an iterator
     * @throws IOException if some IO error occurred reading the database
     */
    public Iterator<T> getAscendingIterator() throws IOException {
        return storage.getOrderedAscendingIterator(null);
    }

    /**
     * Gets an iterator over all entities in the database in descending default sort order
     * @return an iterator
     * @throws IOException if some IO error occurred reading the database
     */
    public Iterator<T> getDescendingIterator() throws IOException {
        return storage.getOrderedDescendingIterator(null);
    }

    /**
     * Gets the first entity in the database according to the default sort order
     * @return the first entity, or null if there are no entities in the database
     * @throws IOException if some IO error occurred reading the database
     */
    public T getFirst() throws IOException {
        Iterator<T> iterator = storage.getOrderedAscendingIterator(null);
        return iterator.hasNext() ? iterator.next() : null;
    }

    /**
     * Gets the last entity in the database according to the default sort order
     * @return the last entity, or null if there are no entities in the database
     * @throws IOException if some IO error occurred reading the database
     */
    public T getLast() throws IOException {
        Iterator<T> iterator = storage.getOrderedDescendingIterator(null);
        return iterator.hasNext() ? iterator.next() : null;
    }

    /**
     * Gets the entity after the given one in the database according to the default sort order.
     * The given entity doesn't have to exist, it can be a search key as well.
     * @param entity an entity or an entity key
     * @return the succeeding entity, or null if there are no succeeding entities
     * @throws IOException if some IO error occurred reading the database
     */
    public T getNext(@NonNull T entity) throws IOException {
        Iterator<T> iterator = storage.getOrderedAscendingIterator(entity);
        if (!iterator.hasNext()) return null;
        T next = iterator.next();
        if (!next.equals(entity)) return next;
        if (!iterator.hasNext()) return null;
        return iterator.next();
    }

    /**
     * Gets the entity before the given one in the database according to the default sort order.
     * The given entity doesn't have to exist, it can be a search key as well.
     * @param entity an entity or an entity key
     * @return the preceding entity, or null if there are no preceding entities
     * @throws IOException if some IO error occurred reading the database
     */
    public T getPrevious(@NonNull T entity) throws IOException {
        Iterator<T> iterator = storage.getOrderedDescendingIterator(entity);
        if (!iterator.hasNext()) return null;
        T next = iterator.next();
        if (!next.equals(entity)) return next;
        if (!iterator.hasNext()) return null;
        return iterator.next();
    }

    /**
     * Gets a stream of all entities in the database, sorted by the default sorting order.
     * If an error occurs while processing the stream, a {@link UncheckedEntityException} is thrown.
     * @return a stream of all entities
     * @throws IOException if an IO error occurs
     */
    public Stream<T> getAscendingStream() throws IOException {
        return getAscendingStream(null);
    }

    /**
     * Gets a stream of all entities in the database starting at the given key (inclusive),
     * sorted by the default sorting order.
     * If an error occurs while processing the stream, a {@link UncheckedEntityException} is thrown.
     * @param start the starting key
     * @return a stream of entities
     * @throws IOException if an IO error occurs
     */
    public Stream<T> getAscendingStream(T start) throws IOException {
        Iterator<T> iterator = storage.getOrderedAscendingIterator(start);
        Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Gets a stream of all entities in the database in reverse default sorting order.
     * If an error occurs while processing the stream, a {@link UncheckedEntityException} is thrown.
     * @return a stream of all entities
     * @throws IOException if an IO error occurs
     */
    public Stream<T> getDescendingStream() throws IOException {
        return getDescendingStream(null);
    }

    /**
     * Gets a stream of all entities in the database starting at the given key (inclusive),
     * in reverse default sorting order.
     * If an error occurs while processing the stream, a {@link UncheckedEntityException} is thrown.
     * @param start the starting key
     * @return a stream of entities
     * @throws IOException if an IO error occurs
     */
    public Stream<T> getDescendingStream(T start) throws IOException {
        Iterator<T> iterator = storage.getOrderedDescendingIterator(start);
        Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Gets a list of entities in the database, sorted by the default sorting order.
     * @param limit the maximum number of entities to return
     * @return a list of entities
     * @throws IOException if an error occurred reading the entities
     */
    public List<T> getAscendingList(int limit) throws IOException {
        try {
            return getAscendingStream().limit(limit).collect(Collectors.toList());
        } catch (UncheckedEntityException e) {
            throw new IOException("An error occurred reading an entity", e);
        }
    }

    /**
     * Gets a list of entities in the database starting at the specified key,
     * sorted by the default sorting order.
     * @param limit the maximum number of entities to return
     * @param start the starting key
     * @return a list of entities
     * @throws IOException if an error occurred reading the entities
     */
    public List<T> getAscendingList(@NonNull T start, int limit) throws IOException {
        try {
            return getAscendingStream(start).limit(limit).collect(Collectors.toList());
        } catch (UncheckedEntityException e) {
            throw new IOException("An error occurred reading an entity", e);
        }
    }

    /**
     * Gets a list of entities in the database in reverse default sorting order.
     * @param limit the maximum number of entities to return
     * @return a list of entities
     * @throws IOException if an error occurred reading the entities
     */
    public List<T> getDescendingList(int limit) throws IOException {
        try {
            return getDescendingStream().limit(limit).collect(Collectors.toList());
        } catch (UncheckedEntityException e) {
            throw new IOException("An error occurred reading an entity", e);
        }
    }

    /**
     * Gets a list of entities in the database starting at the specified key
     * in reverse default sorting order.
     * @param limit the maximum number of entities to return
     * @param start the starting key
     * @return a list of entities
     * @throws IOException if an error occurred reading the entities
     */
    public List<T> getDescendingList(@NonNull T start, int limit) throws IOException {
        try {
            return getDescendingStream(start).limit(limit).collect(Collectors.toList());
        } catch (UncheckedEntityException e) {
            throw new IOException("An error occurred reading an entity", e);
        }
    }

    /**
     * Closes the entity database
     * @throws IOException if an IO error occurs
     */
    public void close() throws IOException {
        storage.close();
    }
}
