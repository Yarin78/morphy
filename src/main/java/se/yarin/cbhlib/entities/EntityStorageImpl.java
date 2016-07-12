package se.yarin.cbhlib.entities;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class EntityStorageImpl<T extends Entity & Comparable<T>>
        extends EntityStorageBase<T> implements EntityStorage<T> {
    private static final Logger log = LoggerFactory.getLogger(EntityStorageImpl.class);

    private final EntityNodeStorageBase<T> nodeStorage;

    private EntityStorageImpl(@NonNull File file, @NonNull EntitySerializer<T> serializer, boolean create)
            throws IOException {
        nodeStorage = new PersistentEntityNodeStorage<T>(file, serializer, create);
    }

    private EntityStorageImpl(String storageName, @NonNull EntitySerializer<T> serializer)
            throws IOException {
        nodeStorage = new InMemoryEntityNodeStorage<>(storageName, serializer);
    }

    public static <T extends Entity & Comparable<T>> EntityStorageImpl open(
            File file, @NonNull EntitySerializer<T> serializer) throws IOException {
        return new EntityStorageImpl<T>(file, serializer, false);
    }

    public static <T extends Entity & Comparable<T>> EntityStorageImpl create(
            File file, @NonNull EntitySerializer<T> serializer) throws IOException {
        return new EntityStorageImpl<>(file, serializer, true);
    }

    public static <T extends Entity & Comparable<T>> EntityStorageImpl createInMemory(
            String name, @NonNull EntitySerializer<T> serializer) throws IOException {
        return new EntityStorageImpl<>(name, serializer);
    }

    // TODO: Add public static method for creating new in-memory version
    // Maybe also in-memory version that loads initial data from disk

    @Override
    public int getNumEntities() {
        return nodeStorage.getNumEntities();
    }

    @Override
    public T getEntity(int entityId) throws IOException {
        if (entityId < 0 || entityId >= nodeStorage.getCapacity()) {
            return null;
        }
        return nodeStorage.getEntityNode(entityId).getEntity();
    }

    @Override
    public int addEntity(@NonNull T entity) throws IOException, EntityStorageException {
        int entityId;

        SearchResult result = treeSearch(entity);
        if (result.node != null && result.compare == 0) {
            throw new EntityStorageException("An entity with the same key already exists");
        }

        if (nodeStorage.getFirstDeletedEntityId() >= 0) {
            // Replace a deleted entity
            entityId = nodeStorage.getFirstDeletedEntityId();
            nodeStorage.setFirstDeletedEntityId(nodeStorage.getEntityNode(entityId).getRightEntityId());
        } else {
            // Appended new entity to the end
            entityId = nodeStorage.getCapacity();
            nodeStorage.setCapacity(entityId + 1);
        }
        nodeStorage.setNumEntities(nodeStorage.getNumEntities() + 1);

        if (result.node == null) {
            nodeStorage.setRootEntityId(entityId);
        } else {
            if (result.compare < 0) {
                result.node.setLeftEntityId(entityId);
            } else {
                result.node.setRightEntityId(entityId);
            }
            nodeStorage.putEntityNode(result.node);
        }
        // TODO: Balance tree

        nodeStorage.updateStorageHeader();

        nodeStorage.putEntityNode(nodeStorage.createNode(entityId, entity));

        log.debug(String.format("Successfully added entity to %s with id %d",
                nodeStorage.getStorageName(), entityId));

        return entityId;
    }

    @AllArgsConstructor
    private class SearchResult {
        private int compare;
        private EntityNodeStorageBase<T>.EntityNode node;
    }

    private SearchResult treeSearch(@NonNull T entity) throws IOException {
        return treeSearch(nodeStorage.getRootEntityId(), new SearchResult(0, null), entity);
    }

    private SearchResult treeSearch(int currentId, SearchResult result, @NonNull T entity) throws IOException {
        if (currentId < 0) {
            return result;
        }

        T current = getEntity(currentId);
        EntityNodeStorageBase.EntityNode node = nodeStorage.getEntityNode(currentId);
        int comp = entity.compareTo(current);

        result = new SearchResult(comp, node);
        if (comp == 0) {
            return result;
        } else if (comp < 0) {
            return treeSearch(node.getLeftEntityId(), result, entity);
        } else {
            return treeSearch(node.getRightEntityId(), result, entity);
        }
    }

    @Override
    public void putEntity(int entityId, @NonNull T entity) throws IOException {
        if (entityId < 0 || entityId >= nodeStorage.getCapacity()) {
            throw new IllegalArgumentException(String.format("Can't put an entity with id %d when capacity is %d",
                    entityId, nodeStorage.getCapacity()));
        }
        if (nodeStorage.getEntityNode(entityId).isDeleted()) {
            throw new IllegalArgumentException("Can't replace a deleted entity");
        }

        nodeStorage.putEntityNode(nodeStorage.createNode(entityId, entity));

        log.debug("Successfully put entity to " + nodeStorage.getStorageName() + " with id " + entityId);
    }

    @Override
    public boolean deleteEntity(int entityId) throws IOException {
        EntityNodeStorageBase.EntityNode node = nodeStorage.getEntityNode(entityId);
        if (node.isDeleted()) {
            log.debug("Deleted entity with id " + entityId + " that was already deleted");
            return false;
        }

        nodeStorage.putEntityNode(nodeStorage.createDeletedNode(entityId));
        nodeStorage.setFirstDeletedEntityId(entityId);
        nodeStorage.setNumEntities(nodeStorage.getNumEntities() - 1);
        nodeStorage.updateStorageHeader();
        return true;
    }

    @Override
    public void close() throws IOException {
        nodeStorage.close();
    }

    /**
     * Validates that the entity headers correctly reflects the order of the entities
     */
    public void validateStructure() throws EntityStorageException, IOException {
        if (nodeStorage.getRootEntityId() == -1) {
            if (getNumEntities() == 0) {
                return;
            }
            throw new EntityStorageException(String.format(
                    "Header says there are %d entities in the storage but the root points to no entity.", getNumEntities()));
        }

        int sum = validate(nodeStorage.getRootEntityId(), null, null);
        if (sum != getNumEntities()) {
            throw new EntityStorageException(String.format(
                    "Found %d entities when traversing the base but the header says there should be %d entities.", sum, getNumEntities()));
        }
    }

    private int validate(int entityId, T min, T max) throws IOException, EntityStorageException {
        // TODO: Validate height difference of left and right tree
        EntityNodeStorageBase<T>.EntityNode node = nodeStorage.getEntityNode(entityId);
        T entity = node.getEntity();
        if (node.isDeleted() || entity == null) {
            throw new EntityStorageException(String.format(
                    "Reached deleted element %d when validating the storage structure.", entityId));
        }
        if ((min != null && min.compareTo(entity) >= 0) || (max != null && max.compareTo(entity) <= 0)) {
            throw new EntityStorageException(String.format(
                    "Entity %d out of order when validating the storage structure", entityId));
        }

        // Since the range is strictly decreasing every time, we should not have to worry
        // about ending up in an infinite recursion.
        int cnt = 1;
        if (node.getLeftEntityId() != -1) {
            cnt += validate(node.getLeftEntityId(), min, entity);
        }
        if (node.getRightEntityId() != -1) {
            cnt += validate(node.getRightEntityId(), entity, max);
        }
        return cnt;
    }


    @Override
    public Stream<T> getEntityStream()  {
        int bufferSize = 1000;

        return IntStream.range(0, (nodeStorage.getCapacity() + bufferSize - 1) / bufferSize)
                .mapToObj(rangeId -> {
                    int rangeStart = rangeId * bufferSize;
                    int rangeEnd = Math.min(nodeStorage.getCapacity(), (rangeId + 1) * bufferSize);
                    try {
                        return nodeStorage.getEntityNodes(rangeStart, rangeEnd);
                    } catch (IOException e) {
                        throw new UncheckedEntityException("Error reading entities", e);
                    }
                })
                .flatMap(List::stream)
                .map(EntityNodeStorageBase.EntityNode::getEntity);
    }
}
