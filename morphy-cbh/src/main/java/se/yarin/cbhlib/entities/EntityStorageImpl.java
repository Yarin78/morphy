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

    private EntityStorageImpl(@NonNull File file, @NonNull EntitySerializer<T> serializer)
            throws IOException {
        nodeStorage = new PersistentEntityNodeStorage<>(file, serializer);
    }

    private EntityStorageImpl() {
        nodeStorage = new InMemoryEntityNodeStorage<>();
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
        int batchSize = 1000, capacity = source.getCapacity();
        for (int i = 0; i < capacity; i+=batchSize) {
            List<EntityNode<T>> nodes = source.nodeStorage.getEntityNodes(i, Math.min(i + batchSize, capacity));
            for (EntityNode<T> node : nodes) {
                target.nodeStorage.putEntityNode(node);
            }
        }
        target.nodeStorage.setMetadata(source.nodeStorage.getMetadata());
        return target;
    }

    public static <T extends Entity & Comparable<T>> EntityStorage<T> createInMemory() {
        return new EntityStorageImpl<>();
    }

    @Override
    public int getNumEntities() {
        return nodeStorage.getNumEntities();
    }

    @Override
    public int getCapacity() {
        return nodeStorage.getCapacity();
    }

    @Override
    public int getVersion() {
        return nodeStorage.getVersion();
    }

    @Override
    public void close() throws IOException {
        nodeStorage.close();
    }

    @Override
    public T getEntity(int entityId) throws IOException {
        if (entityId < 0 || entityId >= nodeStorage.getCapacity()) {
            return null;
        }
        return nodeStorage.getEntityNode(entityId).getEntity();
    }

    @Override
    public T getAnyEntity(@NonNull T entity) throws IOException {
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
    public T getEntity(@NonNull T entity) throws IOException, EntityStorageDuplicateKeyException {
        Iterator<T> iterator = getOrderedAscendingIterator(entity);
        ArrayList<T> result = new ArrayList<>();
        while (iterator.hasNext() && result.size() < 2) {
            T e = iterator.next();
            if (e.compareTo(entity) != 0) {
                break;
            }
            result.add(e);
        }
        if (result.size() == 0) {
            return null;
        }
        if (result.size() == 1) {
            return result.get(0);
        }
        throw new EntityStorageDuplicateKeyException(String.format("Id %d and %d have a matching key",
                result.get(0).getId(), result.get(1).getId()));
    }

    @Override
    public List<T> getEntities(@NonNull T entity) throws IOException {
        Iterator<T> iterator = getOrderedAscendingIterator(entity);
        ArrayList<T> result = new ArrayList<>();
        while (iterator.hasNext()) {
            T e = iterator.next();
            if (e.compareTo(entity) != 0) {
                break;
            }
            result.add(e);
        }
        return result;
    }

    @Override
    public EntityStorageTransaction<T> beginTransaction() {
        return new EntityStorageTransaction<>(this, nodeStorage);
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
        if (nodeStorage.getRootEntityId() == -1) {
            if (getNumEntities() == 0) {
                return;
            }
            throw new EntityStorageException(String.format(
                    "Header says there are %d entities in the storage but the root points to no entity.", getNumEntities()));
        }

        ValidationResult result = validate(nodeStorage.getRootEntityId(), null, null);
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
        if ((min != null && min.compareTo(entity) > 0) || (max != null && max.compareTo(entity) < 0)) {
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
    public List<T> getAllEntities(boolean sortByKey) throws IOException {
        ArrayList<T> result = new ArrayList<>(getNumEntities());
        if (sortByKey) {
            Iterator<T> iterator = getOrderedAscendingIterator(null);
            while (iterator.hasNext()) {
                result.add(iterator.next());
            }
        } else {
            for (T entity : this) {
                result.add(entity);
            }
        }
        return result;
    }

    /**
     * Searches the tree for a specific entity. Returns a path from the root
     * to the searched entity.
     * If the entity doesn't exist in the tree, the path ends at the node in the
     * tree where the entity can be inserted.
     * If there are multiple matching entities, the leftmost will be returned.
     * @param entity the entity to search for
     * @return the most recent node in the path
     * @throws IOException if an IO error occurred when searching in the tree
     */
    private TreePath<T> treeSearch(@NonNull T entity) throws IOException {
        return nodeStorage.treeSearch(nodeStorage.getRootEntityId(), null, entity);
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
        private final int version;

        private void getNextBatch() {
            int endId = Math.min(nodeStorage.getCapacity(), nextBatchStart + batchSize);
            if (nextBatchStart >= endId) {
                batch = null;
            } else {
                try {
                    batch = nodeStorage.getEntityNodes(nextBatchStart, endId);
                } catch (IOException e) {
                    throw new UncheckedEntityException("An IO error when iterating entities", e);
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
            version = getVersion();
            nextBatchStart = startId;
            skipDeleted();
        }

        @Override
        public boolean hasNext() {
            if (version != getVersion()) {
                throw new IllegalStateException("The storage has changed since the iterator was created");
            }
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
        private final int version;

        OrderedIterator(TreePath<T> treePath, boolean ascending) throws IOException {
            this.treePath = treePath;
            this.ascending = ascending;
            this.version = getVersion();
        }

        @Override
        public boolean hasNext() {
            if (this.version != getVersion()) {
                throw new IllegalStateException("The storage has changed since the iterator was created");
            }
            return treePath != null;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException("End of entity iteration reached");
            }
            try {
                T entity = treePath.getNode().getEntity();
                if (ascending) {
                    int rightEntityId = treePath.getNode().getRightEntityId();
                    if (rightEntityId >= 0) {
                        // In ascending traversal, the next node is the leftmost child in the right subtree
                        treePath = new TreePath<>(1, treePath.getNode(), treePath.getParent());
                        treePath = traverseLeftMost(rightEntityId, treePath);
                    } else {
                        treePath = treePath.getParent();
                        while (treePath != null && treePath.getCompare() > 0) {
                            treePath = treePath.getParent();
                        }
                    }
                } else {
                    int leftEntityId = treePath.getNode().getLeftEntityId();
                    if (leftEntityId >= 0) {
                        // In descending traversal, the next node is the rightmost child in the left subtree
                        treePath = new TreePath<>(-1, treePath.getNode(), treePath.getParent());
                        treePath = traverseRightMost(leftEntityId, treePath);
                    } else {
                        // If no child tree in the right direction exist, go to parent
                        treePath = treePath.getParent();
                        while (treePath != null && treePath.getCompare() < 0) {
                            treePath = treePath.getParent();
                        }
                    }
                }
                return entity;
            } catch (IOException e) {
                throw new UncheckedEntityException("An IO error when iterating entities", e);
            }
        }
    }

    TreePath<T> traverseLeftMost(int currentId, TreePath<T> path) throws IOException {
        if (currentId < 0) {
            return path;
        }
        EntityNode<T> node = nodeStorage.getEntityNode(currentId);
        return traverseLeftMost(node.getLeftEntityId(), new TreePath<>(-1, node, path));
    }

    TreePath<T> traverseRightMost(int currentId, TreePath<T> path) throws IOException {
        if (currentId < 0) {
            return path;
        }
        EntityNode<T> node = nodeStorage.getEntityNode(currentId);
        return traverseRightMost(node.getRightEntityId(), new TreePath<>(1, node, path));
    }


    private Iterator<T> getOrderedIterator(T startEntity, boolean ascending) throws IOException {
        TreePath<T> treePath;
        if (startEntity == null) {
            int currentId = nodeStorage.getRootEntityId();
            treePath = ascending ? traverseLeftMost(currentId, null) : traverseRightMost(currentId, null);
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
