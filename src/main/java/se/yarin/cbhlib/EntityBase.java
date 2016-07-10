package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class EntityBase<T extends Entity> implements EntitySerializer<T> {
    private static final Logger log = LoggerFactory.getLogger(EntityBase.class);

    private final OrderedEntityStorageImpl<T> storage;

    /**
     * Gets the underlying storage of the database.
     * @return an entity storage
     */
    protected OrderedEntityStorageImpl<T> getStorage() {
        return storage;
    }

    protected EntityBase(@NonNull OrderedEntityStorageImpl<T> storage) {
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
    protected static <T extends Entity> OrderedEntityStorageImpl<T> loadInMemoryStorage(
            @NonNull File file, EntitySerializer<T> serializer)
            throws IOException {
        FileEntityStorage<T> inputStorage = FileEntityStorage.open(file, serializer);
        OrderedEntityStorageImpl<T> outputStorage = new OrderedEntityStorageImpl<>(new InMemoryEntityStorage<>());
        inputStorage.getEntityStream().forEach(entity -> {
            try {
                outputStorage.putEntity(entity.getId(), entity);
            } catch (EntityStorageException | IOException e) {
                // This shouldn't happen since the output storage is an in-memory storage
                log.error("There was an error putting an entity in the in-memory storage", e);
            }
        });
        return outputStorage;
    }

    /**
     * Gets the number of entites in the database
     * @return the number of entities
     */
    public int getCount() {
        return storage.getNumEntities();
    }

    /**
     * Gets an entity by id
     * @param id the id of the entity (0-indexed)
     * @return the entity, or null if there was no entity with that id
     * @throws IOException if there was an IO error reading the entity
     */
    public T get(int id) throws IOException {
        return storage.getEntity(id);
    }

    /**
     * Gets an entity by key
     * @param entityKey the key of the entity
     * @return the entity, or null if there was no entity with that key
     * @throws IOException if there was an IO error reading the entity
     */
    public T get(@NonNull T entityKey) throws IOException {
        return storage.getEntity(entityKey);
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
     * @param entity the entity to update
     * @throws EntityStorageException if another entity with the same key exists
     * @throws IOException if some other IO error occurred
     */
    public void put(@NonNull T entity) throws EntityStorageException, IOException {
        if (entity.getId() == -1) {
            throw new IllegalArgumentException("The id of the entity to update must be set");
        }
        storage.putEntity(entity.getId(), entity);
    }

    /**
     * Deletes an entity from the database
     * @param entityId the id of the entity to delete
     * @return true if the entity was deleted; false if there was no entity with that id in the database
     * @throws IOException if some IO error occurred
     */
    public boolean delete(int entityId) throws IOException {
        return storage.deleteEntity(entityId);
    }

    /**
     * Gets a stream of all entities in the database, sorted by id.
     * If an error occurs while processing the stream, a {@link UncheckedEntityException} is thrown.
     * @return a stream of all entities
     */
    public Stream<T> streamAll() {
        return storage.getEntityStream();
    }

    /**
     * Gets a list of all entities in the database
     * @return a list of all entities
     * @throws IOException if some IO error occurred reading the entities
     */
    public List<T> getAll() throws IOException {
        return storage.getAllEntities();
    }

    /**
     * Gets the first entity in the database according to the default sort order
     * @return the first entity
     * @throws IOException if some IO error occurred reading the database
     */
    public T getFirst() throws IOException {
        return storage.firstEntity();
    }

    /**
     * Gets the last entity in the database according to the default sort order
     * @return the last entity
     * @throws IOException if some IO error occurred reading the database
     */
    public T getLast() throws IOException {
        return storage.lastEntity();
    }

    /**
     * Gets the entity after the given one in the database according to the default sort order.
     * The given entity doesn't have to exist, it can be a search key as well.
     * @param entity an entity or an entity key
     * @return the succeeding entity
     * @throws IOException if some IO error occurred reading the database
     */
    public T getNext(T entity) throws IOException {
        return storage.nextEntity(entity);
    }

    /**
     * Gets the entity before the given one in the database according to the default sort order.
     * The given entity doesn't have to exist, it can be a search key as well.
     * @param entity an entity or an entity key
     * @return the preceeding entity
     * @throws IOException if some IO error occurred reading the database
     */
    public T getPrevious(T entity) throws IOException {
        return storage.previousEntity(entity);
    }

    /**
     * Gets a stream of all entities in the database, sorted by the default sorting order.
     * If an error occurs while processing the stream, a {@link UncheckedEntityException} is thrown.
     * @return a stream of all entities
     */
    public Stream<T> getAscendingStream() {
        return storage.getAscendingEntityStream();
    }

    /**
     * Gets a stream of all entities in the database starting at the given key (inclusive),
     * sorted by the default sorting order.
     * If an error occurs while processing the stream, a {@link UncheckedEntityException} is thrown.
     * @param start the starting key
     * @return a stream of entities
     */
    public Stream<T> getAscendingStream(@NonNull T start) {
        return storage.getAscendingEntityStream(start);
    }

    /**
     * Gets a stream of all entities in the database in reverse default sorting order.
     * If an error occurs while processing the stream, a {@link UncheckedEntityException} is thrown.
     * @return a stream of all entities
     */
    public Stream<T> getDescendingStream() {
        return storage.getDescendingEntityStream();
    }

    /**
     * Gets a stream of all entities in the database starting at the given key (inclusive),
     * in reverse default sorting order.
     * If an error occurs while processing the stream, a {@link UncheckedEntityException} is thrown.
     * @param start the starting key
     * @return a stream of entities
     */
    public Stream<T> getDescendingStream(@NonNull T start) {
        return storage.getDescendingEntityStream(start);
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
