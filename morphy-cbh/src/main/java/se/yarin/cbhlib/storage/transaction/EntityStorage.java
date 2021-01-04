package se.yarin.cbhlib.storage.transaction;

import lombok.NonNull;
import se.yarin.cbhlib.entities.Entity;
import se.yarin.cbhlib.storage.EntityStorageDuplicateKeyException;
import se.yarin.cbhlib.storage.EntityStorageException;
import se.yarin.cbhlib.storage.TreePath;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public interface EntityStorage<T extends Entity & Comparable<T>> extends Iterable<T> {
    /**
     * Gets the number of entities in the storage.
     * @return the number of entities
     */
    int getNumEntities();

    /**
     * Gets the current capacity of the underlying storage.
     * @return the capacity
     */
    int getCapacity();

    /**
     * Gets the entity with given id.
     * @param entityId the id of the entity to get
     * @return the entity, or null if there was no entity with that id
     */
    T getEntity(int entityId) throws IOException;

    /**
     * Gets an entity by its key. If there are multiple matching the key, one of them will be returned.
     * @param entity an entity populated with the key fields
     * @return the entity, or null if there was no entity with that key
     */
    T getAnyEntity(T entity) throws IOException;

    /**
     * Gets an entity by its key.
     * @param entity an entity populated with the key fields
     * @return the entity, or null if there was no entity with that key
     * @throws EntityStorageDuplicateKeyException if there are multiple entities in the storage having the same key
     */
    T getEntity(T entity) throws IOException, EntityStorageDuplicateKeyException;

    /**
     * Gets all entities matching the key.
     * @param entity an entity populated with the key fields
     * @return all entities in the base with the given key
     */
    List<T> getEntities(T entity) throws IOException;

    /**
     * Begins a new transaction
     * @return the transaction
     */
    EntityStorageTransaction<T> beginTransaction();

    /**
     * Adds a new entity to the storage. The id-field in the entity is ignored.
     * @param entity the entity to add
     * @return the id of the new entity
     */
    int addEntity(@NonNull T entity) throws EntityStorageException, IOException;

    /**
     * Updates an entity in the storage.
     * @param id the entity id to update.
     * @param entity the new entity. {@link Entity#getId()} will be ignored.
     */
    void putEntityById(int id, @NonNull T entity) throws EntityStorageException, IOException;

    /**
     * Updates an entity in the storage. The key fields of the entity will
     * determine which entity in the storage to update.
     * @param entity the new entity. {@link Entity#getId()} will be ignored.
     * @throws EntityStorageException if no existing entity with the key exists
     * @throws EntityStorageDuplicateKeyException if more than one entity with the key exists
     */
    void putEntityByKey(@NonNull T entity) throws EntityStorageException, IOException;

    /**
     * Deletes an entity from the storage.
     * @param entityId the id of the entity to delete
     * @return true if an entity was deleted; false if there was no entity with that id
     */
    boolean deleteEntity(int entityId) throws IOException, EntityStorageException;

    /**
     * Deletes an entity from the storage.
     * @param entity the entity key to delete
     * @return true if an entity was deleted; false if there was no entity with that key
     * @throws EntityStorageDuplicateKeyException if there are multiple entities with the key
     * @throws EntityStorageException if the delete operation failed
     */
    boolean deleteEntity(@NonNull T entity) throws IOException, EntityStorageException;

    /**
     * Closes the storage. Any further operations on the storage will cause IO errors.
     * @throws IOException if an IO error occurs
     */
    void close() throws IOException;

    /**
     * Returns all entities. There will be no null entries in the output.
     * If there are a large number of entities, consider using {@link #iterator()} instead.
     * @param sortByKey if false, sort by id; otherwise sort by default sorting order
     * @return a list of all entities
     */
    List<T> getAllEntities(boolean sortByKey) throws IOException;

    /**
     * Gets an iterator over the entities in ascending id order.
     * Entities will be read in batches to improve performance.
     * @param startId the first entity id, inclusive
     * @return an entity iterator
     * @throws IOException if an IO error occurs
     */
    Iterator<T> iterator(int startId) throws IOException;

    TreePath<T> lowerBound(@NonNull T entity) throws IOException;

    TreePath<T> upperBound(@NonNull T entity) throws IOException;

    /**
     * Gets an iterator over the entities in ascending primary key sorting order
     * @param startEntity the first entity (inclusive), or null to start from the first entity
     * @return an entity iterator
     * @throws IOException if an IO error occurs
     */
    Iterator<T> getOrderedAscendingIterator(T startEntity) throws IOException;

    /**
     * Gets an iterator over the entities in descending primary key sorting order
     * @param startEntity the first entity (inclusive), or null to start from the last entity
     * @return an entity iterator
     * @throws IOException if an IO error occurs
     */
    Iterator<T> getOrderedDescendingIterator(T startEntity) throws IOException;

    /**
     * Validates the integrity of the entity storage.
     * @throws EntityStorageException if the structure of the storage is damaged in some way
     * @throws IOException if an IO error occurs
     */
    void validateStructure() throws EntityStorageException, IOException;

    /**
     * Gets the number of transactions committed to the storage since it was opened
     * @return the version number of the storage
     */
    int getVersion();
}