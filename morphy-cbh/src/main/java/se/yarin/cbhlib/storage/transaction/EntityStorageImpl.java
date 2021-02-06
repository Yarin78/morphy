package se.yarin.cbhlib.storage.transaction;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.entities.Entity;
import se.yarin.cbhlib.storage.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class EntityStorageImpl<T extends Entity & Comparable<T>> implements EntityStorage<T> {
    private static final Logger log = LoggerFactory.getLogger(EntityStorageImpl.class);

    private static final int DEFAULT_HEADER_SIZE = 32;

    @Getter
    private final EntityNodeStorageBase<T> nodeStorage;
    private final String entityType; // For debugging purposes, since we can't resolve <T> at runtime

    private static Map<String, String> extensionTypeMap = Map.of(
            ".cbp", "Player",
            ".cbt", "Tournament",
            ".cbc", "Annotator",
            ".cbs", "Source",
            ".cbe", "Team");

    private static String resolveEntityType(@NonNull File file) {
        String path = file.getPath().toLowerCase();
        return extensionTypeMap.getOrDefault(path.substring(path.length() - 4), "?");
    }

    private EntityStorageImpl(@NonNull File file, @NonNull EntitySerializer<T> serializer)
            throws IOException {
        nodeStorage = new PersistentEntityNodeStorage<>(file, serializer);
        entityType = resolveEntityType(file);
    }

    private EntityStorageImpl() {
        nodeStorage = new InMemoryEntityNodeStorage<>();
        entityType = "?";
    }

    private EntityStorageImpl(EntityNodeStorageBase<T> nodeStorage, String entityType) {
        this.nodeStorage = nodeStorage;
        this.entityType = entityType;
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

    public EntityStorage<T> duplicate(@NonNull File file, @NonNull EntitySerializer<T> serializer) throws IOException {
        return new EntityStorageImpl<>(new PersistentEntityNodeStorage<>(file, serializer, DEFAULT_HEADER_SIZE, this.nodeStorage), resolveEntityType(file));
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
    public List<Integer> getDeletedEntityIds() {
        ArrayList<Integer> ids = new ArrayList<>();
        int id = nodeStorage.getFirstDeletedEntityId();
        while (id >= 0 && !ids.contains(id)) {
            ids.add(id);
            id = nodeStorage.getEntityNode(id).getRightEntityId();
        }
        if (id >= 0) {
            log.warn(String.format("Loop in deleted id chain in entity %s", entityType.toLowerCase()));
        }
        return ids;
    }

    @Override
    public T getEntity(int entityId) {
        if (entityId < 0 || entityId >= nodeStorage.getCapacity()) {
            return null;
        }
        return nodeStorage.getEntityNode(entityId).getEntity();
    }

    @Override
    public T getAnyEntity(@NonNull T entity) {
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
    public T getEntity(@NonNull T entity) throws EntityStorageDuplicateKeyException {
        List<T> result = streamOrderedAscending(entity)
                .takeWhile(e -> e.compareTo(entity) == 0)
                .limit(2)
                .collect(Collectors.toList());

        return switch (result.size()) {
            case 0 -> null;
            case 1 -> result.get(0);
            default -> throw new EntityStorageDuplicateKeyException(
                    String.format("Id %d and %d have a matching key", result.get(0).getId(), result.get(1).getId()));
        };
    }

    @Override
    public List<T> getEntities(@NonNull T entity) {
        return streamOrderedAscending(entity)
                .takeWhile(e -> e.compareTo(entity) == 0)
                .collect(Collectors.toList());
    }

    @Override
    public EntityStorageTransaction<T> beginTransaction() {
        return new EntityStorageTransaction<>(this, nodeStorage);
    }

    @Override
    public int addEntity(@NonNull T entity) throws EntityStorageException {
        EntityStorageTransaction<T> txn = beginTransaction();
        int id = txn.addEntity(entity);
        txn.commit();
        return id;
    }


    @Override
    public void putEntityById(int entityId, @NonNull T entity) throws EntityStorageException {
        EntityStorageTransaction<T> txn = beginTransaction();
        txn.putEntityById(entityId, entity);
        txn.commit();
    }

    @Override
    public void putEntityByKey(@NonNull T entity) throws EntityStorageException {
        EntityStorageTransaction<T> txn = beginTransaction();
        txn.putEntityByKey(entity);
        txn.commit();
    }

    @Override
    public boolean deleteEntity(T entity) throws EntityStorageException {
        EntityStorageTransaction<T> txn = beginTransaction();
        boolean deleted = txn.deleteEntity(entity);
        txn.commit();
        return deleted;
    }

    @Override
    public boolean deleteEntity(int entityId) throws EntityStorageException {
        EntityStorageTransaction<T> txn = beginTransaction();
        boolean deleted = txn.deleteEntity(entityId);
        txn.commit();
        return deleted;
    }

    /**
     * Validates that the entity headers correctly reflects the order of the entities
     * @throws EntityStorageException if the structure of the storage is damaged in some way
     */
    public void validateStructure() throws EntityStorageException {
        if (nodeStorage.getRootEntityId() == -1) {
            if (getNumEntities() == 0) {
                return;
            }
            throw new EntityStorageException(String.format(
                    "Header says there are %d entities in the storage but the root points to no entity.", getNumEntities()));
        }

        ValidationResult result = validate(nodeStorage.getRootEntityId(), null, null, 0);
        if (result.getCount() != getNumEntities()) {
            // This is not a critical error; ChessBase integrity checker doesn't even notice it
            log.warn(String.format(
                    "Found %d entities when traversing the %s base but the header says there should be %d entities.",
                    result.getCount(), entityType.toLowerCase(), getNumEntities()));
        }
    }

    @AllArgsConstructor
    private static class ValidationResult {
        @Getter private final int count;
        @Getter private final int height;
    }

    private ValidationResult validate(int entityId, T min, T max, int depth) throws EntityStorageException {
        if (depth > 40) {
            throw new EntityStorageException("Infinite loop when verifying storage structure for entity " + entityType.toLowerCase());
        }
        EntityNode<T> node = nodeStorage.getEntityNode(entityId);
        T entity = node.getEntity();
        if (node.isDeleted() || entity == null) {
            throw new EntityStorageException(String.format(
                    "Reached deleted %s entity %d when validating the storage structure.", entityType.toLowerCase(), entityId));
        }
        if ((min != null && min.compareTo(entity) > 0) || (max != null && max.compareTo(entity) < 0)) {
            throw new EntityStorageException(String.format(
                    "%s entity %d out of order when validating the storage structure", entityType, entityId));
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
            throw new EntityStorageException(String.format("Height difference at node %d was %d but node data says it should be %d (entity type %s)",
                    node.getEntityId(), rightHeight - leftHeight, node.getHeightDif(), entityType.toLowerCase()));
        }

        if (Math.abs(leftHeight - rightHeight) > 1) {
            throw new EntityStorageException(String.format("Height difference at node %d was %d (entity type %s)",
                    node.getEntityId(), leftHeight - rightHeight, entityType.toLowerCase()));
        }

        return new ValidationResult(cnt, 1 + Math.max(leftHeight, rightHeight));
    }

    /**
     * Returns all entities. There will be no null entries in the output.
     * If there are a large number of entities, consider using {@link #stream()} instead.
     * @param sortByKey if false, sort by id; otherwise sort by default sorting order
     * @return a list of all entities
     */
    public List<T> getAllEntities(boolean sortByKey) {
        return (sortByKey ? streamOrderedAscending() : stream(0)).collect(Collectors.toList());
    }

    public TreePath<T> lowerBound(@NonNull T entity) {
        return nodeStorage.lowerBound(entity);
    }

    public TreePath<T> upperBound(@NonNull T entity) {
        return nodeStorage.upperBound(entity);
    }

    public Iterable<T> iterable() {
        return iterable(0);
    }

    public Iterable<T> iterable(int startId) {
        return () -> new DefaultIterator(startId);
    }

    @Override
    public Stream<T> stream() {
        return stream(0);
    }

    @Override
    public Stream<T> stream(int startId) {
        return StreamSupport.stream(iterable(startId).spliterator(), false);
    }

    private class DefaultIterator implements Iterator<T> {
        private List<EntityNode<T>> batch = new ArrayList<>();
        private int batchPos, nextBatchStart;
        private static final int BATCH_SIZE = 1000;
        private final int version;

        private void getNextBatch() {
            int endId = Math.min(nodeStorage.getCapacity(), nextBatchStart + BATCH_SIZE);
            if (nextBatchStart >= endId) {
                batch = null;
            } else {
                batch = nodeStorage.getEntityNodes(nextBatchStart, endId);
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
            T entity = current.getNode().getEntity();
            this.current = this.current.successor();
            return entity;
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
            return !this.current.isBegin();
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException("End of entity iteration reached");
            }
            this.current = this.current.predecessor();
            return current.getNode().getEntity();
        }
    }

    public Stream<T> streamOrderedAscending() {
        return streamOrderedAscending(TreePath.begin(nodeStorage), TreePath.end(nodeStorage));
    }

    public Stream<T> streamOrderedAscending(T startEntity) {
        return streamOrderedAscending(startEntity == null ? TreePath.begin(nodeStorage) : lowerBound(startEntity));
    }

    public Stream<T> streamOrderedAscending(@NonNull TreePath<T> start) {
        return streamOrderedAscending(start, TreePath.end(nodeStorage));
    }

    public Stream<T> streamOrderedAscending(@NonNull TreePath<T> start, @NonNull TreePath<T> end) {
        Iterable<T> iterable = () -> new OrderedAscendingIterator(start, end.getEntityId());
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    public Stream<T> streamOrderedDescending(T startEntity) {
        Iterable<T> iterable = () -> new OrderedDescendingIterator(startEntity == null ? TreePath.end(nodeStorage) : upperBound(startEntity));
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    public Iterable<T> iterableOrderedAscending(T startEntity) {
        TreePath<T> start = startEntity == null ? TreePath.begin(nodeStorage) : lowerBound(startEntity);
        return () -> new OrderedAscendingIterator(start, -1);
    }

    public Iterable<T> iterableOrderedDescending(T startEntity) {
        TreePath<T> start = startEntity == null ? TreePath.end(nodeStorage) : upperBound(startEntity);
        return () -> new OrderedDescendingIterator(start);
    }
}
