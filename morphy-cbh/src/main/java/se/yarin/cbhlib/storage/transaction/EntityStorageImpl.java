package se.yarin.cbhlib.storage.transaction;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.entities.Entity;
import se.yarin.cbhlib.storage.EntityStorageDuplicateKeyException;
import se.yarin.cbhlib.storage.EntityStorageException;
import se.yarin.cbhlib.exceptions.UncheckedEntityException;
import se.yarin.cbhlib.storage.*;

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

    public static <T extends Entity & Comparable<T>> EntityStorage<T> create(
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
        TreePath<T> treePath = lowerBound(entity);
        if (treePath.isEnd()) {
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

        ValidationResult result = validate(nodeStorage.getRootEntityId(), null, null, 0);
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

    private ValidationResult validate(int entityId, T min, T max, int depth) throws IOException, EntityStorageException {
        if (depth > 40) {
            throw new EntityStorageException("Infinite loop when verifying storage structure");
        }
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
            ValidationResult result = validate(node.getLeftEntityId(), min, entity, depth+1);
            cnt += result.getCount();
            leftHeight = result.getHeight();
        }
        if (node.getRightEntityId() != -1) {
            ValidationResult result = validate(node.getRightEntityId(), entity, max, depth+1);
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
            Iterator<T> iterator = getOrderedAscendingIterator();
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

    public TreePath<T> lowerBound(@NonNull T entity) throws IOException {
        return nodeStorage.lowerBound(entity);
    }

    public TreePath<T> upperBound(@NonNull T entity) throws IOException {
        return nodeStorage.upperBound(entity);
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

    private class OrderedAscendingIterator implements Iterator<T> {

        // Invariant: current.node is the next entity to be returned
        // If current.isEnd(), there are no more entities to be returned
        private @NonNull TreePath<T> current;
        private final int stopId;
        private final int version;

        OrderedAscendingIterator(@NonNull TreePath<T> start, int stopId) {
            this.current = start;
            this.stopId = stopId;
            this.version = getVersion();
        }

        @Override
        public boolean hasNext() {
            if (this.version != getVersion()) {
                throw new IllegalStateException("The storage has changed since the iterator was created");
            }
            return !current.isEnd() && current.getEntityId() != stopId;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException("End of entity iteration reached");
            }
            try {
                T entity = current.getNode().getEntity();
                this.current = this.current.successor();
                return entity;
            } catch (IOException e) {
                throw new UncheckedEntityException("An IO error when iterating entities", e);
            }
        }
    }

    private class OrderedDescendingIterator implements Iterator<T> {

        // Invariant: current.node is the next entity to be returned
        // If current.isBegin(), there are no more entities to be returned
        private @NonNull TreePath<T> current;
        private final int version;

        OrderedDescendingIterator(@NonNull TreePath<T> start) {
            this.current = start;
            this.version = getVersion();
        }

        @Override
        public boolean hasNext() {
            if (this.version != getVersion()) {
                throw new IllegalStateException("The storage has changed since the iterator was created");
            }
            try {
                return !this.current.isBegin();
            } catch (IOException e) {
                throw new UncheckedEntityException("An IO error when iterating entities", e);
            }
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException("End of entity iteration reached");
            }
            try {
                this.current = this.current.predecessor();
                return current.getNode().getEntity();
            } catch (IOException e) {
                throw new UncheckedEntityException("An IO error when iterating entities", e);
            }
        }
    }

    public Iterator<T> getOrderedAscendingIterator() throws IOException {
        return getOrderedAscendingIterator(TreePath.begin(nodeStorage), TreePath.end(nodeStorage));
    }

    public Iterator<T> getOrderedAscendingIterator(T startEntity) throws IOException {
        return getOrderedAscendingIterator(startEntity == null ? TreePath.begin(nodeStorage) : lowerBound(startEntity));
    }

    public Iterator<T> getOrderedAscendingIterator(@NonNull TreePath<T> start) {
        return getOrderedAscendingIterator(start, TreePath.end(nodeStorage));
    }

    public Iterator<T> getOrderedAscendingIterator(@NonNull TreePath<T> start, @NonNull TreePath<T> end) {
        return new OrderedAscendingIterator(start, end.getEntityId());
    }

    public Iterator<T> getOrderedDescendingIterator(T startEntity) throws IOException {
        return new OrderedDescendingIterator(startEntity == null ? TreePath.end(nodeStorage) : upperBound(startEntity));
    }
}
