package se.yarin.cbhlib.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class EntityStorageImpl<T extends Entity & Comparable<T>> implements EntityStorage<T> {
    private static final Logger log = LoggerFactory.getLogger(EntityStorageImpl.class);

    private static final int DEFAULT_HEADER_SIZE = 32;

    private final EntityNodeStorageBase<T> nodeStorage;
    private EntityNodeStorageMetadata metadata;

    private EntityStorageImpl(@NonNull File file, @NonNull EntitySerializer<T> serializer)
            throws IOException {
        nodeStorage = new PersistentEntityNodeStorage<>(file, serializer);
        metadata = ((PersistentEntityNodeStorage) nodeStorage).getMetadata();
    }

    private EntityStorageImpl() {
        nodeStorage = new InMemoryEntityNodeStorage<>();
        metadata = new EntityNodeStorageMetadata(0, 0, 0);
    }

    public static <T extends Entity & Comparable<T>> EntityStorage<T> open(
            File file, @NonNull EntitySerializer<T> serializer) throws IOException {
        return new EntityStorageImpl<>(file, serializer);
    }

    public static <T extends Entity & Comparable<T>> EntityStorage<T> create(
            File file, @NonNull EntitySerializer<T> serializer) throws IOException {
        return create(file, serializer, DEFAULT_HEADER_SIZE);
    }

    static <T extends Entity & Comparable<T>> EntityStorage<T> create(
            File file, @NonNull EntitySerializer<T> serializer, int headerSize) throws IOException {
        PersistentEntityNodeStorage.createEmptyStorage(file, serializer, headerSize);
        return open(file, serializer);
    }

    public static <T extends Entity & Comparable<T>> EntityStorage<T> openInMemory(
            File file, @NonNull EntitySerializer<T> serializer) throws IOException {
        EntityStorageImpl<T> source = new EntityStorageImpl<>(file, serializer);
        EntityStorageImpl<T> target = new EntityStorageImpl<>();
        int batchSize = 1000, capacity = source.metadata.getCapacity();
        for (int i = 0; i < capacity; i+=batchSize) {
            List<EntityNode<T>> nodes = source.nodeStorage.getEntityNodes(i, Math.min(i + batchSize, capacity));
            for (EntityNode<T> node : nodes) {
                target.nodeStorage.putEntityNode(node);
            }
        }
        target.metadata.setCapacity(capacity);
        target.metadata.setRootEntityId(source.metadata.getRootEntityId());
        target.metadata.setFirstDeletedEntityId(source.metadata.getFirstDeletedEntityId());
        target.metadata.setNumEntities(source.metadata.getNumEntities());
        return target;
    }

    public static <T extends Entity & Comparable<T>> EntityStorage<T> createInMemory() {
        return new EntityStorageImpl<>();
    }

    @Override
    public int getNumEntities() {
        return metadata.getNumEntities();
    }

    @Override
    public int getCapacity() {
        return metadata.getCapacity();
    }

    @Override
    public int getVersion() {
        return metadata.getVersion();
    }

    @Override
    public void close() throws IOException {
        nodeStorage.close();
    }

    @Override
    public T getEntity(int entityId) throws IOException {
        if (entityId < 0 || entityId >= metadata.getCapacity()) {
            return null;
        }
        return nodeStorage.getEntityNode(entityId).getEntity();
    }

    @Override
    public T getEntity(@NonNull T entity) throws IOException {
        TreePath<T> treePath = treeSearch(entity);
        if (treePath == null) {
            return null;
        }
        T foundEntity = treePath.getNode().getEntity();
        if (foundEntity.compareTo(entity) == 0) {
            return foundEntity;
        }
        return null;
    }

    @Override
    public EntityStorageTransaction<T> beginTransaction() {
        return new EntityStorageTransaction<>(this, nodeStorage, metadata);
    }

    // TODO: This is ugly. Would be nicer and more consistent to move metadata into nodestorage.
    void updateMetadata(@NonNull EntityNodeStorageMetadata newMetadata) {
        this.metadata = newMetadata;
    }

    @Override
    public int addEntity(@NonNull T entity) throws IOException, EntityStorageException {
        EntityStorageTransaction<T> txn = beginTransaction();
        int id = txn.addEntity(entity);
        txn.commit();
        return id;
    }


    @Override
    public void putEntityById(int entityId, @NonNull T entity) throws IOException, EntityStorageException {
        EntityStorageTransaction<T> txn = beginTransaction();
        txn.putEntityById(entityId, entity);
        txn.commit();
    }

    @Override
    public void putEntityByKey(@NonNull T entity) throws IOException, EntityStorageException {
        EntityStorageTransaction<T> txn = beginTransaction();
        txn.putEntityByKey(entity);
        txn.commit();
    }

    @Override
    public boolean deleteEntity(T entity) throws IOException, EntityStorageException {
        EntityStorageTransaction<T> txn = beginTransaction();
        boolean deleted = txn.deleteEntity(entity);
        txn.commit();
        return deleted;
    }

    @Override
    public boolean deleteEntity(int entityId) throws IOException, EntityStorageException {
        EntityStorageTransaction<T> txn = beginTransaction();
        boolean deleted = txn.deleteEntity(entityId);
        txn.commit();
        return deleted;
    }

    /**
     * Validates that the entity headers correctly reflects the order of the entities
     */
    public void validateStructure() throws EntityStorageException, IOException {
        if (metadata.getRootEntityId() == -1) {
            if (getNumEntities() == 0) {
                return;
            }
            throw new EntityStorageException(String.format(
                    "Header says there are %d entities in the storage but the root points to no entity.", getNumEntities()));
        }

        ValidationResult result = validate(metadata.getRootEntityId(), null, null);
        if (result.getCount() != getNumEntities()) {
            throw new EntityStorageException(String.format(
                    "Found %d entities when traversing the base but the header says there should be %d entities.",
                    result.getCount(), getNumEntities()));
        }
    }

    @AllArgsConstructor
    private class ValidationResult {
        @Getter private int count;
        @Getter private int height;
    }

    private ValidationResult validate(int entityId, T min, T max) throws IOException, EntityStorageException {
        EntityNode<T> node = nodeStorage.getEntityNode(entityId);
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
        int cnt = 1, leftHeight = 0, rightHeight = 0;
        if (node.getLeftEntityId() != -1) {
            ValidationResult result = validate(node.getLeftEntityId(), min, entity);
            cnt += result.getCount();
            leftHeight = result.getHeight();
        }
        if (node.getRightEntityId() != -1) {
            ValidationResult result = validate(node.getRightEntityId(), entity, max);
            cnt += result.getCount();
            rightHeight = result.getHeight();
        }

        if (rightHeight - leftHeight != node.getHeightDif()) {
            throw new EntityStorageException(String.format("Height difference at node %d was %d but node data says it should be %d",
                    node.getEntityId(), rightHeight - leftHeight, node.getHeightDif()));
        }

        if (Math.abs(leftHeight - rightHeight) > 1) {
            throw new EntityStorageException(String.format("Height difference at node %d was %d",
                    node.getEntityId(), leftHeight - rightHeight));
        }

        return new ValidationResult(cnt, 1 + Math.max(leftHeight, rightHeight));
    }

    /**
     * Returns all entities. There will be no null entries in the output.
     * If there are a large number of entities, consider using {@link #iterator()} instead.
     * @return a list of all entities
     * @throws IOException if there was an error getting any entity
     */
    public List<T> getAllEntities() throws IOException {
        ArrayList<T> result = new ArrayList<>(getNumEntities());
        for (T entity : this) {
            result.add(entity);
        }
        return result;
    }

    /**
     * Searches the tree for a specific entity. Returns a path from the root
     * to the searched entity.
     * If the entity doesn't exist in the tree, the path ends at the node in the
     * tree where the entity can be inserted.
     * @param entity the entity to search for
     * @return the most recent node in the path
     * @throws IOException if an IO error occurred when searching in the tree
     */
    private TreePath<T> treeSearch(@NonNull T entity) throws IOException {
        return nodeStorage.treeSearch(metadata.getRootEntityId(), null, entity);
    }


    public Iterator<T> iterator() {
        return iterator(0);
    }

    @Override
    public Iterator<T> iterator(int startId) {
        return new DefaultIterator(startId);
    }

    private class DefaultIterator implements Iterator<T> {
        private List<EntityNode<T>> batch = new ArrayList<>();
        private int batchPos, nextBatchStart = 0, batchSize = 1000;

        private void getNextBatch() {
            int endId = Math.min(metadata.getCapacity(), nextBatchStart + batchSize);
            if (nextBatchStart >= endId) {
                batch = null;
            } else {
                try {
                    batch = nodeStorage.getEntityNodes(nextBatchStart, endId);
                } catch (IOException e) {
                    throw new UncheckedEntityException("AN IO error when iterating entities", e);
                }
                nextBatchStart = endId;
            }
            batchPos = 0;
        }

        private void skipDeleted() {
            while (batch != null) {
                while (batchPos < batch.size()) {
                    if (!batch.get(batchPos).isDeleted()) return;
                    batchPos++;
                }
                getNextBatch();
            }
        }

        DefaultIterator(int startId) {
            nextBatchStart = startId;
            skipDeleted();
        }

        @Override
        public boolean hasNext() {
            return batch != null;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException("End of entity iteration reached");
            }
            EntityNode<T> node = batch.get(batchPos++);
            skipDeleted();
            return node.getEntity();
        }
    }

    private class OrderedIterator implements Iterator<T> {

        // Invariant: treePath.node is the next entity to be returned
        // If treePath == null, there are no more entities to be returned
        private TreePath<T> treePath;
        private final boolean ascending;

        OrderedIterator(TreePath<T> treePath, boolean ascending) throws IOException {
            this.treePath = treePath;
            this.ascending = ascending;
        }

        @Override
        public boolean hasNext() {
            return treePath != null;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException("End of entity iteration reached");
            }
            try {
                // TODO: Check if any writes have happened since iterator was created
                T entity = treePath.getNode().getEntity();
                if (ascending && treePath.getNode().getRightEntityId() >= 0) {
                    treePath = new TreePath<>(1, treePath.getNode(), treePath.getParent());
                    treePath = nodeStorage.treeSearch(treePath.getNode().getRightEntityId(), treePath, entity);
                } else if (!ascending && treePath.getNode().getLeftEntityId() >= 0) {
                    treePath = new TreePath<>(-1, treePath.getNode(), treePath.getParent());
                    treePath = nodeStorage.treeSearch(treePath.getNode().getLeftEntityId(), treePath, entity);
                } else {
                    treePath = treePath.getParent();
                    while (treePath != null && treePath.getCompare() * (ascending ? 1 : -1) > 0) {
                        treePath = treePath.getParent();
                    }
                }
                return entity;
            } catch (IOException e) {
                throw new UncheckedEntityException("AN IO error when iterating entities", e);
            }
        }
    }

    private Iterator<T> getOrderedIterator(T startEntity, boolean ascending) throws IOException {
        TreePath<T> treePath = null;
        if (startEntity == null) {
            int currentId = metadata.getRootEntityId();
            while (currentId >= 0) {
                EntityNode<T> node = nodeStorage.getEntityNode(currentId);
                treePath = new TreePath<>(ascending ? -1 : 1, node, treePath);
                currentId = ascending ? node.getLeftEntityId() : node.getRightEntityId();
            }
        } else {
            treePath = treeSearch(startEntity);
            while (treePath != null && treePath.getCompare() * (ascending ? 1 : -1) > 0) {
                treePath = treePath.getParent();
            }
        }
        return new OrderedIterator(treePath, ascending);
    }

    public Iterator<T> getOrderedAscendingIterator(T startEntity) throws IOException {
        return getOrderedIterator(startEntity, true);
    }

    public Iterator<T> getOrderedDescendingIterator(T startEntity) throws IOException {
        return getOrderedIterator(startEntity, false);
    }
}
