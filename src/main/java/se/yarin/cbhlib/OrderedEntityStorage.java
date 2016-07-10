package se.yarin.cbhlib;

import lombok.NonNull;

import java.io.IOException;
import java.util.stream.Stream;

public interface OrderedEntityStorage<T extends Entity> extends EntityStorage<T> {
    int firstId() throws EntityStorageException, IOException;
    int lastId() throws EntityStorageException, IOException;
    T firstEntity() throws EntityStorageException, IOException;
    T lastEntity() throws EntityStorageException, IOException;
    int nextEntityId(int entityId) throws EntityStorageException, IOException;
    int previousEntityId(int entityId) throws EntityStorageException, IOException;
    T nextEntity(@NonNull T entity) throws EntityStorageException, IOException;
    T previousEntity(@NonNull T entity) throws EntityStorageException, IOException;

    Stream<T> getAscendingEntityStream();
    Stream<T> getAscendingEntityStream(T start);
    Stream<T> getDescendingEntityStream();
    Stream<T> getDescendingEntityStream(T start);
}
