package se.yarin.morphy.entities;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.DatabaseContext;
import se.yarin.morphy.exceptions.MorphyEntityIndexException;
import se.yarin.morphy.exceptions.MorphyIOException;
import se.yarin.morphy.exceptions.MorphyNotSupportedException;
import se.yarin.morphy.storage.ItemStorage;
import se.yarin.morphy.util.CBUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

public abstract class EntityIndex<T extends Entity & Comparable<T>>  {
    private static final Logger log = LoggerFactory.getLogger(EntityIndex.class);

    protected final @NotNull ItemStorage<EntityIndexHeader, EntityNode> storage;
    private final @NotNull String entityType;
    private final @NotNull DatabaseContext context;
    private int numCommittedTxn;

    @NotNull EntityIndexHeader storageHeader() {
        return storage.getHeader();
    }

    protected EntityIndex(@NotNull ItemStorage<EntityIndexHeader, EntityNode> storage, @NotNull String entityType, @Nullable DatabaseContext context) {
        this.storage = storage;
        this.entityType = entityType;
        this.context = context == null ? new DatabaseContext() : context;
    }

    public @NotNull DatabaseContext context() {
        return context;
    }

    public int getNumCommittedTxn() {
        return numCommittedTxn;
    }

    /**
     * Returns the number of entities in the index.
     * @return number of entities
     */
    public int count() {
        return storage.getHeader().numEntities();
    }

    /**
     * Returns the capacity of the index
     * @return number of entity slots in the index
     */
    public int capacity() {
        return storage.getHeader().capacity();
    }

    /**
     * Gets an entity node in the index
     * If the index is invalid, either an empty (non-null) item is returned,
     * or {@link IllegalArgumentException} is thrown depending on the open option.
     * @param id the id of the entity
     * @return an entity node
     * @throws IllegalArgumentException if the index points to an item outside of the storage and the storage is open in strict mode
     * @throws MorphyIOException if an IO error occurred when reading the data
     */
    @NotNull EntityNode getNode(int id) {
        return storage.getItem(id);
    }

    /**
     * Gets the entity from a node
     * @param node the entity node
     * @return an entity
     * @throws IllegalArgumentException if the entity refers to a deleted node
     */
    protected @NotNull T resolveEntity(@NotNull EntityNode node) throws IllegalArgumentException {
        if (node.isDeleted()) {
            throw new IllegalArgumentException(String.format("The %s node with id %d is deleted", entityType, node.getId()));
        }
        return deserialize(node.getId(), node.getGameCount(), node.getFirstGameId(), node.getSerializedEntity());
    }

    public EntityIndexTransaction<T> beginTransaction() {
        // TODO: Acquire read lock (probably better in constructor)
        return new EntityIndexTransaction<>(this);
    }

    void transactionCommitted(EntityIndexTransaction<T> txn) {
        if (!txn.isCommitted()) {
            throw new IllegalStateException("Transaction hasn't been committed!");
        }
        numCommittedTxn += 1;
    }

    /**
     * Gets an entity by id.
     * If the id is invalid (outside of the index, or the item has been deleted),
     * empty (non-null) item is returned or {@link IllegalArgumentException} is thrown, depending on the OpenOption of the index.
     * @param id the id of the entity
     * @return the entity
     */
    public @NotNull T get(int id) {
        return resolveEntity(getNode(id));
    }

    /**
     * Gets an entity by key. If there are multiple entities matching, returns one of them.
     * @param entityKey the key of the entity
     * @return the entity, or null if there was no entity with that key
     */
    public @Nullable T get(T entityKey) {
        EntityIndexTransaction<T> txn = beginTransaction();
        EntityIndexTransaction<T>.NodePath treePath = txn.lowerBound(entityKey);
        if (treePath.isEnd()) {
            return null;
        }
        T foundEntity = treePath.getEntity();
        if (foundEntity.compareTo(entityKey) == 0) {
            return foundEntity;
        }
        return null;
    }

    /**
     * Gets the underlying raw data for an entity.
     * For debugging purposes only.
     * @param id the id of the entity to get
     * @return a byte array containing a copy of the underlying data
     */
    public @NotNull byte[] getRaw(int id) {
        EntityNode node = storage.getItem(id);
        return node.getSerializedEntity().clone();
    }

    /**
     * Gets all entities with a given key.
     * @param entityKey the key of the entity
     * @return a list of all matching entities.
     */
    public @NotNull List<T> getAll(@NotNull T entityKey) {
        return streamOrderedAscending(entityKey)
                .takeWhile(e -> e.compareTo(entityKey) == 0)
                .collect(Collectors.toList());
    }

    /**
     * Gets a list of all entities in the database, ordered by id
     * @return a list of all entities
     */
    public @NotNull List<T> getAll() {
        ArrayList<T> result = new ArrayList<>();
        iterable().forEach(result::add);
        return result;
    }

    /**
     * Gets a list of all entities in the database, ordered by key
     * @return a list of all entities
     */
    public @NotNull List<T> getAllOrdered() {
        ArrayList<T> result = new ArrayList<>();
        iterableAscending().forEach(result::add);
        return result;
    }

    /**
     * Gets the first entity in the index according to the default sort order
     * @return the first entity, or null if there are no entities in the database
     */
    public @Nullable T getFirst() {
        Iterator<T> iterator = iterableAscending().iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }

    /**
     * Gets the last entity in the index according to the default sort order
     * @return the last entity, or null if there are no entities in the database
     */
    public @Nullable T getLast() {
        Iterator<T> iterator = iterableDescending().iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }

    /**
     * Gets the entity after the given one in the index according to the default sort order.
     * The given entity doesn't have to exist, it can be a search key as well.
     * @param entity an entity or an entity key
     * @return the succeeding entity, or null if there are no succeeding entities
     */
    public @Nullable T getNext(T entity) {
        for (T e : iterableAscending(entity)) {
            if (!e.equals(entity)) {
                return e;
            }
        }
        return null;
    }

    /**
     * Gets the entity before the given one in the index according to the default sort order.
     * The given entity doesn't have to exist, it can be a search key as well.
     * @param entity an entity or an entity key
     * @return the preceding entity, or null if there are no preceding entities
     */
    public @Nullable T getPrevious(T entity) {
        for (T e : iterableDescending(entity)) {
            if (!e.equals(entity)) {
                return e;
            }
        }
        return null;
    }

    /**
     * Returns an iterable of all entities in the index, sorted by id.
     * @return an iterable of all entities
     */
    public @NotNull Iterable<T> iterable() {
        return iterable(0);
    }

    /**
     * Returns an iterable of all entities in the index, sorted by id.
     * @param startId the first id in the iterable
     * @return an iterable of all entities
     */
    public @NotNull Iterable<T> iterable(int startId) {
        return () -> new EntityBatchIterator<>(this, startId);
    }

    /**
     * Gets an iterable of all entities in the index sorted by the default sorting order.
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableAscending() {
        EntityIndexTransaction<T> txn = beginTransaction();
        return () -> new OrderedEntityAscendingIterator<>(txn.begin(), -1);
    }

    /**
     * Gets an iterable of all entities in the index starting at the given key (inclusive),
     * sorted by the default sorting order.
     * @param start the starting key
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableAscending(@NotNull T start) {
        EntityIndexTransaction<T> txn = beginTransaction();
        return () -> new OrderedEntityAscendingIterator<>(txn.lowerBound(start), -1);
    }

    /**
     * Gets an iterable of all entities in the index starting at a given key (inclusive),
     * ending at a given key (exclusive), sorted by the default sorting order.
     * @param start the starting key
     * @param end the end key
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableAscending(@NotNull T start, @NotNull T end) {
        EntityIndexTransaction<T> txn = beginTransaction();
        EntityIndexTransaction<T>.NodePath endPath = txn.lowerBound(end);
        return () -> new OrderedEntityAscendingIterator<>(txn.lowerBound(start), endPath.isEnd() ? -1 : endPath.getEntityId());
    }

    /**
     * Gets an iterable of all entities in the index in reverse default sorting order.
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableDescending() {
        EntityIndexTransaction<T> txn = beginTransaction();
        return () -> new OrderedEntityDescendingIterator<>(txn.end());
    }

    /**
     * Gets an iterable of all entities in the index starting at the given key (inclusive),
     * in reverse default sorting order.
     * @param start the starting key
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableDescending(T start) {
        EntityIndexTransaction<T> txn = beginTransaction();
        return () -> new OrderedEntityDescendingIterator<>(txn.upperBound(start));
    }

    /**
     * Returns a stream of all entities in the index, sorted by id.
     * @return a stream of all entities
     */
    public @NotNull Stream<T> stream() {
        return StreamSupport.stream(iterable().spliterator(), false);
    }

    /**
     * Returns a stream of all entities in the index, sorted by id.
     * @param startId the first id in the stream
     * @return a stream of all entities
     */
    public @NotNull Stream<T> stream(int startId) {
        return StreamSupport.stream(iterable(startId).spliterator(), false);
    }

    /**
     * Gets a stream of all entities in the index, sorted by the default sorting order.
     * @return a stream of all entities
     */
    public @NotNull Stream<T> streamOrderedAscending() {
        return StreamSupport.stream(iterableAscending().spliterator(), false);
    }

    /**
     * Gets a stream of all entities in the index starting at the given key (inclusive),
     * sorted by the default sorting order.
     * @param start the starting key
     * @return a stream of entities
     */
    public @NotNull Stream<T> streamOrderedAscending(@NotNull T start) {
        return StreamSupport.stream(iterableAscending(start).spliterator(), false);
    }

    /**
     * Gets a stream of all entities in the index starting at a given key (inclusive),
     * and ending at a given key (exclusive), sorted by the default sorting order.
     * @param start the starting key
     * @param end the end key
     * @return a stream of entities
     */
    public @NotNull Stream<T> streamOrderedAscending(@NotNull T start, @NotNull T end) {
        return StreamSupport.stream(iterableAscending(start, end).spliterator(), false);
    }

    /**
     * Gets a stream of all entities in the index in reverse default sorting order.
     * @return a stream of all entities
     */
    public @NotNull Stream<T> streamOrderedDescending() {
        return StreamSupport.stream(iterableDescending().spliterator(), false);
    }

    /**
     * Gets a stream of all entities in the index starting at the given key (inclusive),
     * in reverse default sorting order.
     * @param start the starting key
     * @return a stream of entities
     */
    public @NotNull Stream<T> streamOrderedDescending(@NotNull T start) {
        return StreamSupport.stream(iterableDescending(start).spliterator(), false);
    }

    protected abstract @NotNull T deserialize(int entityId, int count, int firstGameId, byte[] serializedData);

    protected abstract void serialize(T entity, @NotNull ByteBuffer buf);

    /**
     * Adds a new entity to the index
     * @param entity the entity to add
     * @return the id of the added entity
     */
    public int add(@NotNull T entity) {
        EntityIndexTransaction<T> txn = beginTransaction();
        int id = txn.addEntity(entity);
        txn.commit();
        return id;
    }

    /**
     * Updates an existing entity in the index
     * @param id the id of the entity to update
     * @param entity the new entity (the id field will be ignored)
     */
    public void put(int id, @NotNull T entity) {
        EntityIndexTransaction<T> txn = beginTransaction();
        txn.putEntityById(id, entity);
        txn.commit();
    }

    /**
     * Updates an existing entity in the index
     * The entity to update is determined by the key; use {@link #put(int, T)} to update the key fields of an entity
     * @param entity the new entity
     * @throws IllegalArgumentException if there is no matching entity, or if there are
     * @return the id of the entity that was updated
     */
    public int put(@NotNull T entity) {
        EntityIndexTransaction<T> txn = beginTransaction();
        int id = txn.putEntityByKey(entity);
        txn.commit();
        return id;
    }

    /**
     * Deletes an entity from the index
     * @param entityId the id of the entity to delete
     * @return true if the entity was deleted; false if there was no entity with that id in the index
     */
    public boolean delete(int entityId) {
        EntityIndexTransaction<T> txn = beginTransaction();
        boolean deleted = txn.deleteEntity(entityId);
        txn.commit();
        return deleted;
    }

    /**
     * Deletes an entity from the index
     * @param entity the key of the entity to delete
     * @return true if the entity was deleted; false if there was no entity with that key in the index
     * @throws IllegalArgumentException if there are multiple entities with the given key
     */
    public boolean delete(@NotNull T entity) {
        EntityIndexTransaction<T> txn = beginTransaction();
        boolean deleted = txn.deleteEntity(entity);
        txn.commit();
        return deleted;
    }

    public void close() throws MorphyIOException {
        storage.close();
    }

    protected void copyEntities(@NotNull EntityIndex<T> targetIndex) {
        // Low level copy of all entities from one index to a new empty index
        if (targetIndex.capacity() != 0) {
            throw new IllegalStateException("The target index must be empty");
        }

        int batchSize = 1000, capacity = capacity(), currentIndex = 0;
        for (int i = 0; i < capacity; i += batchSize) {
            List<EntityNode> nodes = storage.getItems(i, Math.min(i + batchSize, capacity));
            for (EntityNode node : nodes) {
                targetIndex.storage.putItem(currentIndex, node);
                currentIndex += 1;
            }
        }

        // Copy all fields in the source header except the header size
        ImmutableEntityIndexHeader newHeader = ImmutableEntityIndexHeader
                .copyOf(storageHeader())
                .withHeaderSize(targetIndex.storageHeader().headerSize());
        targetIndex.storage.putHeader(newHeader);
    }

    /**
     * Validates that the entity headers correctly reflects the order of the entities
     * @throws MorphyEntityIndexException if the structure of the storage is damaged in some way
     */
    public void validateStructure() throws MorphyEntityIndexException {
        if (storageHeader().rootNodeId() == -1) {
            if (storageHeader().numEntities() == 0) {
                return;
            }
            throw new MorphyEntityIndexException(String.format(
                    "Header says there are %d entities in the storage but the root points to no entity.", storageHeader().numEntities()));
        }

        ValidationResult result = validate(storageHeader().rootNodeId(), null, null, 0);
        if (result.count() != storageHeader().numEntities()) {
            // This is not a critical error; ChessBase integrity checker doesn't even notice it
            // It's quite often, at least in older databases, off by one; in particular
            // when there are just a few entities in the db
            log.debug(String.format(
                    "Found %d entities when traversing the %s base but the header says there should be %d entities.",
                    result.count(), entityType.toLowerCase(), storageHeader().numEntities()));
        }
    }

    /**
     * Gets a list of all node ids that are marked as deleted and can be reused.
     * For internal use only.
     * @return a list of deleted entity ids.
     */
    public @NotNull List<Integer> getDeletedEntityIds() {
        ArrayList<Integer> ids = new ArrayList<>();
        int id = storageHeader().deletedEntityId();
        while (id >= 0 && !ids.contains(id)) {
            ids.add(id);
            id = storage.getItem(id).getRightChildId();
        }
        if (id >= 0) {
            log.warn(String.format("Loop in deleted id chain in entity %s", entityType.toLowerCase()));
        }
        return ids;
    }

    @Value.Immutable
    public static abstract class ValidationResult {
        public abstract int count();
        public abstract int height();

        public static @NotNull ValidationResult of(int count, int height) {
            return ImmutableValidationResult.builder().count(count).height(height).build();
        }
    }

    private @NotNull ValidationResult validate(int entityId, @Nullable T min, @Nullable T max, int depth) throws MorphyEntityIndexException {
        if (depth > 40) {
            throw new MorphyEntityIndexException("Infinite loop when verifying storage structure for entity " + entityType.toLowerCase());
        }
        EntityNode node = getNode(entityId);
        T entity = resolveEntity(node);
        if (node.isDeleted()) {
            throw new MorphyEntityIndexException(String.format(
                    "Reached deleted %s entity %d when validating the storage structure.", entityType.toLowerCase(), entityId));
        }
        if ((min != null && min.compareTo(entity) > 0) || (max != null && max.compareTo(entity) < 0)) {
            throw new MorphyEntityIndexException(String.format(
                    "%s entity %d out of order when validating the storage structure", entityType, entityId));
        }

        // Since the range is strictly decreasing every time, we should not have to worry
        // about ending up in an infinite recursion.
        int cnt = 1, leftHeight = 0, rightHeight = 0;
        if (node.getLeftChildId() != -1) {
            ValidationResult result = validate(node.getLeftChildId(), min, entity, depth+1);
            cnt += result.count();
            leftHeight = result.height();
        }
        if (node.getRightChildId() != -1) {
            ValidationResult result = validate(node.getRightChildId(), entity, max, depth+1);
            cnt += result.count();
            rightHeight = result.height();
        }

        if (rightHeight - leftHeight != node.getBalance()) {
            throw new MorphyEntityIndexException(String.format("Height difference at node %d was %d but node data says it should be %d (entity type %s)",
                    node.getId(), rightHeight - leftHeight, node.getBalance(), entityType.toLowerCase()));
        }

        if (Math.abs(leftHeight - rightHeight) > 1) {
            throw new MorphyEntityIndexException(String.format("Height difference at node %d was %d (entity type %s)",
                    node.getId(), leftHeight - rightHeight, entityType.toLowerCase()));
        }

        return ValidationResult.of(cnt, 1 + Math.max(leftHeight, rightHeight));
    }

    /**
     * Upgrades an EntityIndex to the latest version of the file format if needed
     * If the file doesn't exist or is already on the latest version, nothing is done.
     * @param file an entity index file
     */
    protected static void upgrade(@NotNull File file, @NotNull EntityIndexSerializer serializer) throws IOException {
        if (!file.exists()) {
            return;
        }

        FileChannel inputChannel = FileChannel.open(file.toPath(), READ);
        FileChannel outputChannel = null;
        File upgradedFile;

        try {
            ByteBuffer inputBuffer = ByteBuffer.allocate(serializer.serializedHeaderSize());
            inputChannel.read(inputBuffer);
            inputBuffer.flip();
            EntityIndexHeader header = serializer.deserializeHeader(inputBuffer);

            if (header.headerSize() == 32) {
                // Header size indicates latest version
                inputChannel.close();
                return;
            }

            if (header.headerSize() != 28) {
                throw new MorphyNotSupportedException(String.format("Unsupported header size in %s (%d)", file, header.headerSize()));
            }

            upgradedFile = File.createTempFile(CBUtil.baseName(file), CBUtil.extension(file));
            outputChannel = FileChannel.open(upgradedFile.toPath(), WRITE);

            EntityIndexHeader upgradedHeader = ImmutableEntityIndexHeader.copyOf(header).withHeaderSize(32);
            ByteBuffer outputBuffer = ByteBuffer.allocate(serializer.serializedHeaderSize());
            serializer.serializeHeader(upgradedHeader, outputBuffer);
            outputBuffer.flip();

            outputChannel.write(outputBuffer);
            inputChannel.transferTo(28, file.length() - 28, outputChannel);
        }
        finally {
            if (inputChannel != null) {
                inputChannel.close();
            }
            if (outputChannel != null) {
                outputChannel.close();
            }
        }

        file.delete();
        upgradedFile.renameTo(file);
    }

}
