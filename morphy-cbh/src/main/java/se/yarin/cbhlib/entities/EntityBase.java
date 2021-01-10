package se.yarin.cbhlib.entities;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.exceptions.ChessBaseIOException;
import se.yarin.cbhlib.storage.EntitySerializer;
import se.yarin.cbhlib.storage.EntityStorageDuplicateKeyException;
import se.yarin.cbhlib.storage.EntityStorageException;
import se.yarin.cbhlib.storage.transaction.EntityStorage;
import se.yarin.cbhlib.storage.transaction.EntityStorageImpl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public abstract class EntityBase<T extends Entity & Comparable<T>> implements EntitySerializer<T> {
    private static final Logger log = LoggerFactory.getLogger(EntityBase.class);

    private final EntityStorage<T> storage;
    private final List<EntityBaseOnCloseHandler<T>> onCloseHandlers = new ArrayList<>();

    // Cache entities by id to avoid having to deserialize the same entity again and again.
    // Only used when doing direct lookups, not when iterating over ranges.
    // TODO: Proper LRU cache to save memory
    private final Map<Integer, T> cacheById = new HashMap<>();

    /**
     * Gets the underlying storage of the database.
     * @return an entity storage
     */
    public EntityStorage<T> getStorage() {
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
     * @throws ChessBaseIOException if there was an IO error reading the entity
     */
    public T get(int id) {
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
     * @throws ChessBaseIOException if there was an IO error reading the entity
     * @throws EntityStorageDuplicateKeyException if there are multiple entities with the given key
     */
    public T get(@NonNull T entityKey) throws EntityStorageDuplicateKeyException {
        return storage.getEntity(entityKey);
    }

    /**
     * Gets an entity by key. If there are multiple entities matching, returns one of them.
     * @param entityKey the key of the entity
     * @return the entity, or null if there was no entity with that key
     * @throws ChessBaseIOException if there was an IO error reading the entity
     */
    public T getAny(@NonNull T entityKey) {
        return storage.getAnyEntity(entityKey);
    }

    /**
     * Gets all entities by key. Almost always this will be 0 or 1.
     * @param entityKey the key of the entity
     * @return all entities in the base with the given key
     * @throws ChessBaseIOException if there was an IO error reading the entity
     */
    public List<T> getAll(@NonNull T entityKey) {
        return storage.getEntities(entityKey);
    }

    /**
     * Adds a new entity to the database
     * @param entity the entity to add
     * @return the added entity with the id set
     * @throws EntityStorageException if another entity with the same key exists
     * @throws ChessBaseIOException if some other IO error occurred
     */
    public T add(@NonNull T entity) throws EntityStorageException {
        int id = storage.addEntity(entity);
        return get(id);
    }

    /**
     * Updates an existing entity in the database
     * @param id the id of the entity to update
     * @param entity the new entity
     * @throws EntityStorageException if another entity with the same key exists
     * @throws ChessBaseIOException if some other IO error occurred
     */
    public void put(int id, @NonNull T entity) throws EntityStorageException {
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
     * @throws ChessBaseIOException if some IO error occurred
     */
    public boolean delete(int entityId) throws EntityStorageException {
        cacheById.remove(entityId);
        return storage.deleteEntity(entityId);
    }

    /**
     * Deletes an entity from the database
     * @param entity the key of the entity to delete
     * @return true if the entity was deleted; false if there was no entity with that key in the database
     * @throws EntityStorageException if the database is in an inconsistent state preventing the delete
     * @throws ChessBaseIOException if some IO error occurred
     */
    public boolean delete(T entity) throws EntityStorageException {
        cacheById.remove(entity.getId());
        return storage.deleteEntity(entity);
    }

    /**
     * Returns an iterable of all entities in the database, sorted by id.
     * If an error occurs while processing the stream, a {@link ChessBaseIOException} is thrown.
     * @return an iterable of all entities
     */
    public Iterable<T> iterable() {
        return storage.iterable();
    }

    /**
     * Returns an iterable of all entities in the database, sorted by id.
     * If an error occurs while processing the stream, a {@link ChessBaseIOException} is thrown.
     * @param startId the first id in the iterable
     * @return an iterable of all entities
     */
    public Iterable<T> iterable(int startId) {
        return storage.iterable(startId);
    }

    /**
     * Returns a stream of all entities in the database, sorted by id.
     * If an error occurs while processing the stream, a {@link ChessBaseIOException} is thrown.
     * @return a stream of all entities
     */
    public Stream<T> stream() {
        return storage.stream();
    }

    /**
     * Returns a stream of all entities in the database, sorted by id.
     * If an error occurs while processing the stream, a {@link ChessBaseIOException} is thrown.
     * @param startId the first id in the stream
     * @return a stream of all entities
     */
    public Stream<T> stream(int startId) {
        return storage.stream(startId);
    }

    /**
     * Gets a list of all entities in the database
     * @return a list of all entities
     * @throws ChessBaseIOException if some IO error occurred reading the entities
     */
    public List<T> getAll() {
        return storage.getAllEntities(false);
    }

    /**
     * Gets the first entity in the database according to the default sort order
     * @return the first entity, or null if there are no entities in the database
     * @throws ChessBaseIOException if some IO error occurred reading the database
     */
    public T getFirst() {
        return streamOrderedAscending()
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets the last entity in the database according to the default sort order
     * @return the last entity, or null if there are no entities in the database
     * @throws ChessBaseIOException if some IO error occurred reading the database
     */
    public T getLast() {
        return streamOrderedDescending()
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets the entity after the given one in the database according to the default sort order.
     * The given entity doesn't have to exist, it can be a search key as well.
     * @param entity an entity or an entity key
     * @return the succeeding entity, or null if there are no succeeding entities
     * @throws ChessBaseIOException if some IO error occurred reading the database
     */
    public T getNext(@NonNull T entity) {
        return streamOrderedAscending(entity)
                .filter(e -> !e.equals(entity))
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets the entity before the given one in the database according to the default sort order.
     * The given entity doesn't have to exist, it can be a search key as well.
     * @param entity an entity or an entity key
     * @return the preceding entity, or null if there are no preceding entities
     * @throws ChessBaseIOException if some IO error occurred reading the database
     */
    public T getPrevious(@NonNull T entity) {
        return streamOrderedDescending(entity)
                .filter(e -> !e.equals(entity))
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets a stream of all entities in the database, sorted by the default sorting order.
     * If an error occurs while processing the stream, a {@link ChessBaseIOException} is thrown.
     * @return a stream of all entities
     * @throws ChessBaseIOException if an IO error occurs
     */
    public Stream<T> streamOrderedAscending() {
        return streamOrderedAscending(null);
    }

    /**
     * Gets a stream of all entities in the database starting at the given key (inclusive),
     * sorted by the default sorting order.
     * If an error occurs while processing the stream, a {@link ChessBaseIOException} is thrown.
     * @param start the starting key
     * @return a stream of entities
     * @throws ChessBaseIOException if an IO error occurs
     */
    public Stream<T> streamOrderedAscending(T start) {
        return storage.streamOrderedAscending(start);
    }

    /**
     * Gets a stream of all entities in the database in reverse default sorting order.
     * If an error occurs while processing the stream, a {@link ChessBaseIOException} is thrown.
     * @return a stream of all entities
     * @throws ChessBaseIOException if an IO error occurs
     */
    public Stream<T> streamOrderedDescending() {
        return streamOrderedDescending(null);
    }

    /**
     * Gets a stream of all entities in the database starting at the given key (inclusive),
     * in reverse default sorting order.
     * If an error occurs while processing the stream, a {@link ChessBaseIOException} is thrown.
     * @param start the starting key
     * @return a stream of entities
     * @throws ChessBaseIOException if an IO error occurs
     */
    public Stream<T> streamOrderedDescending(T start) {
        return storage.streamOrderedDescending(start);
    }

    /**
     * Gets an iterable of all entities in the database
     * sorted by the default sorting order.
     * If an error occurs while iterating, a {@link ChessBaseIOException} is thrown.
     * @return an iterable of entities
     * @throws ChessBaseIOException if an IO error occurs
     */
    public Iterable<T> iterableOrderedAscending() { return iterableOrderedAscending(null); }

    /**
     * Gets an iterable of all entities in the database starting at the given key (inclusive),
     * sorted by the default sorting order.
     * If an error occurs while iterating, a {@link ChessBaseIOException} is thrown.
     * @param start the starting key
     * @return an iterable of entities
     * @throws ChessBaseIOException if an IO error occurs
     */
    public Iterable<T> iterableOrderedAscending(T start) { return storage.iterableOrderedAscending(start); }

    /**
     * Gets an iterable of all entities in the database
     * in reverse default sorting order.
     * If an error occurs while iterating, a {@link ChessBaseIOException} is thrown.
     * @return an iterable of entities
     * @throws ChessBaseIOException if an IO error occurs
     */
    public Iterable<T> iterableOrderedDescending() { return iterableOrderedDescending(null); }

    /**
     * Gets an iterable of all entities in the database starting at the given key (inclusive),
     * in reverse default sorting order.
     * If an error occurs while iterating, a {@link ChessBaseIOException} is thrown.
     * @param start the starting key
     * @return an iterable of entities
     * @throws ChessBaseIOException if an IO error occurs
     */
    public Iterable<T> iterableOrderedDescending(T start) { return storage.iterableOrderedDescending(start); }

    /**
     * Closes the entity database
     * @throws IOException if an IO error occurs
     */
    public void close() throws IOException {
        for (EntityBaseOnCloseHandler<T> onCloseHandler : onCloseHandlers) {
            onCloseHandler.closing(this);
        }
        storage.close();
    }

    public abstract EntityBase<T> duplicate(@NonNull File targetFile) throws IOException;

    protected void addOnCloseHandler(@NonNull EntityBaseOnCloseHandler<T> handler) {
        this.onCloseHandlers.add(handler);
    }
}
