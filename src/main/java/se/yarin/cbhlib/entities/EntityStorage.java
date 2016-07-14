package se.yarin.cbhlib.entities;

import lombok.NonNull;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public interface EntityStorage<T> extends Iterable<T> {
    /**
     * Gets the number of entities in the storage.
     * @return the number of entities
     */
    int getNumEntities();

    /**
     * Gets the entity with given id.
     * @param entityId the id of the entity to get
     * @return the entity, or null if there was no entity with that id
     */
    T getEntity(int entityId) throws IOException;

    /**
     * Gets an entity by the unique key
     * @param entity an entity populated with the unique key fields
     * @return the entity, or null if there was no entity with that key
     */
    T getEntity(T entity) throws IOException;

    /**
     * Returns all entities. There will be no null entries in the output.
     * If there are a large number of entities, consider using {@link #iterator()} instead.
     * @return a list of all entities
     */
    List<T> getAllEntities() throws IOException;

    /**
     * Adds a new entity to the storage. The id-field in the entity is ignored.
     * @param entity the entity to add
     * @return the id of the new entity
     * @throws EntityStorageException if another entity with the same key already exists
     */
    int addEntity(@NonNull T entity) throws EntityStorageException, IOException;

    /**
     * Updates an entity in the storage.
     * @param entityId the id of the entity to update
     * @param entity the new entity
     * @throws EntityStorageException if another entity with the same key already exists
     */
    void putEntity(int entityId, @NonNull T entity) throws EntityStorageException, IOException;

    /**
     * Deletes an entity from the storage.
     * @param entityId the id of the entity to delete
     * @return true if an entity was deleted; false if there was no entity with that id
     */
    boolean deleteEntity(int entityId) throws IOException, EntityStorageException;

    /**
     * Closes the storage. Any further operations on the storage will cause IO errors.
     * @throws IOException if an IO error occurs
     */
    void close() throws IOException;

    /**
     * Gets an iterator over the entities in ascending id order.
     * Entities will be read in batches to improve performance.
     * @param startId the first entity id, inclusive
     * @return an entity iterator
     * @throws IOException if an IO error occurs
     */
    Iterator<T> iterator(int startId) throws IOException;

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
}
