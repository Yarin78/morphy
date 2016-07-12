package se.yarin.cbhlib.entities;

import lombok.NonNull;

import java.io.IOException;
import java.util.stream.Stream;

public interface OrderedEntityStorage<T extends Entity> extends EntityStorage<T> {
    T firstEntity() throws IOException;
    T lastEntity() throws IOException;
    T nextEntity(@NonNull T entity) throws IOException;
    T previousEntity(@NonNull T entity) throws IOException;

    /**
     * Gets an entity by key (the unique parts of an entity)
     * @param key the entity key
     * @return an entity in the storage matching the key, or null if non existed
     */
    T getEntity(@NonNull T key) throws IOException;

    Stream<T> getAscendingEntityStream();
    Stream<T> getAscendingEntityStream(T start);
    Stream<T> getDescendingEntityStream();
    Stream<T> getDescendingEntityStream(T start);
}
