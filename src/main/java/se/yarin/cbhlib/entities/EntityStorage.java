package se.yarin.cbhlib.entities;

import lombok.NonNull;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

public interface EntityStorage<T> {
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
     * Returns a stream of all entities. There will be no null entries in the output.
     * @return an entity stream
     * @throws UncheckedEntityException if an exception occurs while processing entities
     */
    Stream<T> getEntityStream();

    /**
     * Returns all entities. There will be no null entries in the output.
     * If there are a large number of entities, consider using {@link #getEntityStream} instead.
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
    boolean deleteEntity(int entityId) throws IOException;

    /**
     * Closes the storage. Any further operations on the storage will cause IO errors.
     * @throws IOException if an IO error occurs
     */
    void close() throws IOException;
}
