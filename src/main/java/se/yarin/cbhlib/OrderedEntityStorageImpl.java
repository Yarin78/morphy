package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.TreeMap;
import java.util.stream.Stream;

public class OrderedEntityStorageImpl<T extends Entity> extends EntityStorageBase<T> implements OrderedEntityStorage<T> {
    private static final Logger log = LoggerFactory.getLogger(OrderedEntityStorageImpl.class);

    private EntityStorage<T> storage;
    private TreeMap<T, Integer> entityTree;

    public OrderedEntityStorageImpl(@NonNull EntityStorage<T> storage) {
        this.storage = storage;
        reloadIndex();
    }

    public void reloadIndex() {
        log.info("Building in memory index for storage containing " + storage.getNumEntities() + " entities");
        long start = System.currentTimeMillis();
        entityTree = new TreeMap<>();
        // This may throw an unchecked exception if there was an error reading the data
        storage.getEntityStream().forEach(entity -> entityTree.put(entity, entity.getId()));
        long elapsed = System.currentTimeMillis() - start;
        log.info("Finished building index in " + elapsed + " ms");
    }

    private void ensureIndexExists() {
        if (entityTree == null) {
            throw new IllegalStateException("The index has not been initialized");
        }
    }

    @Override
    public int getNumEntities() {
        return storage.getNumEntities();
    }

    @Override
    public T getEntity(int entityId) throws IOException {
        return storage.getEntity(entityId);
    }

    @Override
    public Stream<T> getEntityStream() {
        return storage.getEntityStream();
    }

    @Override
    public int addEntity(@NonNull T entity) throws EntityStorageException, IOException {
        ensureIndexExists();
        Integer existingId = entityTree.get(entity);
        if (existingId != null) {
            throw new EntityStorageException(String.format(
                    "Tried to add entity '%s' but an entity with the same data exists having id %d",
                    entity.toString(), existingId));
        }
        int id = storage.addEntity(entity);
        entityTree.put(entity, id);
        return id;
    }

    @Override
    public void putEntity(int entityId, @NonNull T entity) throws EntityStorageException, IOException {
        ensureIndexExists();
        Integer existingId = entityTree.get(entity);
        if (existingId != null && existingId != entityId) {
            throw new EntityStorageException(String.format(
                    "Tried to add entity '%s' but an entity with the same data exists having id %d",
                    entity.toString(), existingId));
        }
        T oldEntity = storage.getEntity(entityId);
        if (oldEntity != null) {
            entityTree.remove(oldEntity);
        }
        storage.putEntity(entityId, entity);
        entityTree.put(entity, entityId);
    }

    @Override
    public boolean deleteEntity(int entityId) throws IOException {
        ensureIndexExists();
        T entity = storage.getEntity(entityId);
        if (entity != null) {
            entityTree.remove(entity);
        }
        return storage.deleteEntity(entityId);
    }

    @Override
    public void close() throws IOException {
        storage.close();
    }


    @Override
    public T getEntity(@NonNull T key) throws IOException {
        ensureIndexExists();
        Integer id = entityTree.get(key);
        if (id == null) {
            return null;
        }
        return getEntity(id);
    }


    @Override
    public T firstEntity() {
        ensureIndexExists();
        return entityTree.size() == 0 ? null : entityTree.firstKey();
    }

    @Override
    public T lastEntity() {
        ensureIndexExists();
        return entityTree.size() == 0 ? null : entityTree.lastKey();
    }

    @Override
    public T nextEntity(@NonNull T entity) {
        ensureIndexExists();
        return entityTree.higherKey(entity);
    }

    @Override
    public T previousEntity(@NonNull T entity) {
        ensureIndexExists();
        return entityTree.lowerKey(entity);
    }


    @Override
    public Stream<T> getAscendingEntityStream() {
        ensureIndexExists();
        return entityTree.keySet().stream();
    }

    @Override
    public Stream<T> getAscendingEntityStream(T start) {
        ensureIndexExists();
        return entityTree.tailMap(start, true).keySet().stream();
    }

    @Override
    public Stream<T> getDescendingEntityStream() {
        ensureIndexExists();
        return entityTree.descendingKeySet().stream();
    }

    @Override
    public Stream<T> getDescendingEntityStream(T start) {
        ensureIndexExists();
        return entityTree.headMap(start, true).descendingKeySet().stream();
    }
}

