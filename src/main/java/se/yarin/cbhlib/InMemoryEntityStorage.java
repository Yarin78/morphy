package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class InMemoryEntityStorage<T extends Entity> extends EntityStorageBase<T> implements EntityStorage<T> {
    private static final Logger log = LoggerFactory.getLogger(InMemoryEntityStorage.class);

    private List<T> entityList;
    private int numEntities;

    public InMemoryEntityStorage() {
        entityList = new ArrayList<>();
        numEntities = 0;
    }

    @Override
    public int getNumEntities() {
        return numEntities;
    }

    @Override
    public T getEntity(int entityId) throws IOException {
        if (entityId < 0 || entityId >= entityList.size()) {
            return null;
        }
        return entityList.get(entityId);
    }

    @Override
    public Stream<T> getEntityStream() {
        return entityList.stream().filter(entity -> entity != null);
    }

    @Override
    public int addEntity(@NonNull T entity) throws IOException {
        for (int i = 0; i < entityList.size(); i++) {
            if (entityList.get(i) == null) {
                putEntity(i, entity);
                return i;
            }
        }
        int id = entityList.size();
        putEntity(entityList.size(), entity);
        return id;
    }

    @Override
    public void putEntity(int entityId, @NonNull T entity) throws IOException {
        if (entityId < 0) {
            throw new IllegalArgumentException("Invalid entity id: " + entityId);
        }
        while (entityList.size() <= entityId) {
            entityList.add(null);
        }
        if (entityList.get(entityId) == null) {
            numEntities++;
        }
        entityList.set(entityId, entity);
    }

    @Override
    public boolean deleteEntity(int entityId) throws IOException {
        if (entityId < 0 || entityId >= entityList.size()) {
            throw new IllegalArgumentException("There is no entity with id " + entityId);
        }
        if (entityList.get(entityId) != null) {
            log.debug("Deleted entity with id " + entityId + " that was already deleted");
            numEntities--;
            entityList.set(entityId, null);
            return true;
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        // Nothing to do here
    }
}
