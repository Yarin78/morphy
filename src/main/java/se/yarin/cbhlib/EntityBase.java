package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class EntityBase<T extends Entity> implements EntitySerializer<PlayerEntity> {
    private static final Logger log = LoggerFactory.getLogger(EntityBase.class);

    private final OrderedEntityStorageImpl<T> storage;

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
            throws IOException, EntityStorageException {
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

    public int getCount() {
        return storage.getNumEntities();
    }

    public T get(int id) throws EntityStorageException, IOException {
        return storage.getEntity(id);
    }

    public T put(@NonNull T player) throws EntityStorageException, IOException {
        if (player.getId() == -1) {
            int id = storage.addEntity(player);
            return get(id);
        }
        storage.putEntity(player.getId(), player);
        return player;
    }

    public Stream<T> getAll() throws IOException, EntityStorageException {
        return storage.getEntityStream();
    }

    public T getFirst() throws EntityStorageException, IOException {
        return storage.firstEntity();
    }

    public T getLast() throws EntityStorageException, IOException {
        return storage.lastEntity();
    }

    public T getNext(T entity) throws EntityStorageException, IOException {
        return storage.nextEntity(entity);
    }

    public T getPrevious(T entity) throws EntityStorageException, IOException {
        return storage.previousEntity(entity);
    }

    public Stream<T> getAscendingStream() {
        return storage.getAscendingEntityStream();
    }

    public Stream<T> getAscendingStream(@NonNull T start) {
        return storage.getAscendingEntityStream(start);
    }

    public Stream<T> getDescendingStream() {
        return storage.getDescendingEntityStream();
    }

    public Stream<T> getDescendingStream(@NonNull T start) {
        return storage.getDescendingEntityStream(start);
    }

    public List<T> getAscendingList(int limit) {
        return getAscendingStream().limit(limit).collect(Collectors.toList());
    }

    public List<T> getAscendingList(@NonNull T start, int limit) {
        return getAscendingStream(start).limit(limit).collect(Collectors.toList());
    }

    public List<T> getDescendingList(int limit) {
        return getDescendingStream().limit(limit).collect(Collectors.toList());
    }

    public List<T> getDescendingList(@NonNull T start, int limit) {
        return getDescendingStream(start).limit(limit).collect(Collectors.toList());
    }
}
