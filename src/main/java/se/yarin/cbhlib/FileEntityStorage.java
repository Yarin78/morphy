package se.yarin.cbhlib;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.*;

public class FileEntityStorage<T extends Entity & Comparable<T>> extends EntityStorageBase<T> implements EntityStorage<T> {

    private static final Logger log = LoggerFactory.getLogger(FileEntityStorage.class);

    private static final int MAGIC_CONSTANT = 1234567890;
    private static final int ENTITY_DELETED = -999;

    private final EntitySerializer<T> serializer;
    private final int serializedEntitySize;

    private int capacity;
    private int rootEntityId; // The id of the root entity in the tree
    private int firstDeletedEntityId;
    private int numEntities;
    private int entityOffset;
    private String storageName;
    private FileChannel channel;

    /**
     * An entity node in the entity tree, including its data.
     * If the entity is deleted, then it contains a link to the next deleted entity.
     */
    @Data
    class EntityNode {
        private int entityId;
        // The id of the preceding entity id. If -999, this entity is deleted.
        private int leftEntityId = -1;
        // The id of the succeeding entity id. If this entity is deleted, this points to the next deleted entity.
        private int rightEntityId = -1;
        // Height of left tree - height of right tree. Used for balancing the binary tree.
        private int heightDif = 0;
        private byte[] serializedEntity;
        private T entity = null; // cached deserialization

        private EntityNode() {
        }

        public EntityNode(int entityId, T entity) {
            this.entityId = entityId;
            this.entity = entity;
            serializedEntity = serializer.serialize(entity).array();
        }

        public EntityNode(T entity) {
            this(entity.getId(), entity);
        }

        public boolean isDeleted() {
            return leftEntityId == ENTITY_DELETED;
        }

        public T getEntity() {
            if (isDeleted()) {
                return null;
            }
            if (entity == null) {
                entity = serializer.deserialize(entityId, ByteBuffer.wrap(serializedEntity));
            }
            return entity;
        }
    }

    private EntityNode createDeletedNode(int entityId, int nextDeletedEntityId) {
        EntityNode node = new EntityNode();
        node.entityId = entityId;
        node.setLeftEntityId(ENTITY_DELETED);
        node.setRightEntityId(nextDeletedEntityId);
        node.setHeightDif(0);
        node.serializedEntity = new byte[serializedEntitySize];
        return node;
    }


    private EntityNode deserializeNode(int entityId, ByteBuffer buf) {
        EntityNode node = new EntityNode();
        node.entityId = entityId;
        node.leftEntityId = ByteBufferUtil.getIntL(buf);
        node.rightEntityId = ByteBufferUtil.getIntL(buf);
        node.heightDif = ByteBufferUtil.getSignedByte(buf);
        // Only deserialize the actual entity on demand
        node.serializedEntity = new byte[serializedEntitySize];
        buf.get(node.serializedEntity);
        return node;
    }

    private ByteBuffer serializeNode(EntityNode node) {
        ByteBuffer buf = ByteBuffer.allocate(9 + serializedEntitySize);
        ByteBufferUtil.putIntL(buf, node.getLeftEntityId());
        ByteBufferUtil.putIntL(buf, node.getRightEntityId());
        ByteBufferUtil.putByte(buf, node.getHeightDif());
        buf.put(node.serializedEntity);
        return buf;
    }

    FileEntityStorage(File file, EntitySerializer<T> serializer, boolean create) throws IOException {
        this.storageName = file.getName();
        this.serializer = serializer;

        if (create) {
            channel = FileChannel.open(file.toPath(), CREATE_NEW, READ, WRITE);

            capacity = 0;
            rootEntityId = -1;
            serializedEntitySize = serializer.getSerializedEntityLength();
            numEntities = 0;
            firstDeletedEntityId = -1;
            entityOffset = 32;
            updateStorageHeader();
        } else {
            channel = FileChannel.open(file.toPath(), READ, WRITE);
            ByteBuffer header = ByteBuffer.allocate(32);
            channel.read(header);
            header.position(0);

            capacity = ByteBufferUtil.getIntL(header);
            rootEntityId = ByteBufferUtil.getIntL(header);
            int headerInt = ByteBufferUtil.getIntL(header);
            if (headerInt != MAGIC_CONSTANT) {
                // Not sure what this is!?
                throw new IOException("Invalid header int: " + headerInt);
            }
            serializedEntitySize = ByteBufferUtil.getIntL(header);
            firstDeletedEntityId = ByteBufferUtil.getIntL(header);
            numEntities = ByteBufferUtil.getIntL(header);
            entityOffset = 28 + ByteBufferUtil.getIntL(header);

            log.debug(String.format("Opening %s; capacity = %d, root = %d, numEntities = %d, firstDeletedId = %d",
                    storageName, capacity, rootEntityId, numEntities, firstDeletedEntityId));
        }
    }

    public static <T extends Entity & Comparable<T>> FileEntityStorage open(
            File file, @NonNull EntitySerializer<T> serializer) throws IOException {
        return new FileEntityStorage<>(file, serializer, false);
    }

    public static <T extends Entity & Comparable<T>> FileEntityStorage create(
            File file, @NonNull EntitySerializer<T> serializer) throws IOException {
        return new FileEntityStorage<>(file, serializer, true);
    }

    private void updateStorageHeader() throws IOException {
        channel.position(0);

        ByteBuffer header = ByteBuffer.allocate(32);
        ByteBufferUtil.putIntL(header, capacity);
        ByteBufferUtil.putIntL(header, rootEntityId);
        ByteBufferUtil.putIntL(header, MAGIC_CONSTANT);
        ByteBufferUtil.putIntL(header, serializedEntitySize);
        ByteBufferUtil.putIntL(header, firstDeletedEntityId);
        ByteBufferUtil.putIntL(header, numEntities);
        ByteBufferUtil.putIntL(header, entityOffset - 28);

        header.position(0);
        channel.write(header);

        log.debug(String.format("Updated %s; capacity = %d, root = %d, numEntities = %d, firstDeletedId = %d",
                storageName, capacity, rootEntityId, numEntities, firstDeletedEntityId));
    }

    /**
     * Positions the channel at the start of the specified entityId.
     * Valid positions are between 0 and capacity (to allow for adding new entities)
     * @param entityId the entityId to position to channel against
     * @throws IOException
     */
    private void positionChannel(int entityId) throws IOException {
        channel.position(entityOffset + entityId * (9 + serializedEntitySize));
    }

    /**
     * Gets the entity node for the specified entity id.
     * The channel position is updated and points to the serialized entity node.
     * @param entityId the id of the entity node to get
     * @return the entity header
     * @throws IOException if some IO error occurred
     */
    private EntityNode getEntityNode(int entityId) throws IOException {
        if (entityId < 0 || entityId >= capacity) {
            throw new IllegalArgumentException("Invalid entity id " + entityId + "; capacity is " + capacity);
        }

        positionChannel(entityId);
        ByteBuffer buf = ByteBuffer.allocate(9 + serializedEntitySize);
        channel.read(buf);
        buf.position(0);

        EntityNode entityNode = deserializeNode(entityId, buf);

        if (log.isTraceEnabled()) {
            log.trace("Read entity node: " + entityNode);
        }

        return entityNode;
    }

    private void putEntityNode(EntityNode node) throws IOException {
        if (node.entityId < 0 || node.entityId > capacity) {
            throw new IllegalArgumentException("Can't write entity header with id " + node.entityId + "; capacity is " + capacity);
        }

        positionChannel(node.entityId);
        ByteBuffer src = serializeNode(node);
        src.position(0);
        channel.write(src);

        if (log.isDebugEnabled()) {
            log.debug(String.format("Put entity node: " + node));
        }
    }

    @Override
    public int getNumEntities() {
        return numEntities;
    }

    @Override
    public T getEntity(int entityId) throws IOException {
        if (entityId < 0 || entityId >= capacity) {
            return null;
        }
        return getEntityNode(entityId).getEntity();
    }

    /**
     * Gets all entities in the specified range. Deleted entities will be omitted, so the resulting
     * array may be shorter than the specified range.
     */
    private List<T> getEntitiesBuffered(int startIdInclusive, int endIdExclusive) throws IOException {
        if (startIdInclusive < 0 || startIdInclusive > capacity) {
            throw new IllegalArgumentException(String.format(
                    "start must be within the capacity of the storage (capacity = %d, start = %d)",
                    capacity, startIdInclusive));
        }
        if (endIdExclusive < 0 || endIdExclusive > capacity) {
            throw new IllegalArgumentException(String.format(
                    "end must be within the capacity of the storage (capacity = %d, end = %d)",
                    capacity, endIdExclusive));
        }
        if (startIdInclusive >= endIdExclusive) {
            return new ArrayList<>();
        }
        if (log.isTraceEnabled()) {
            log.trace(String.format("getEntitiesBuffered [%d, %d)", startIdInclusive, endIdExclusive));
        }
        ArrayList<T> result = new ArrayList<>(endIdExclusive - startIdInclusive);
        positionChannel(startIdInclusive);
        ByteBuffer buf = ByteBuffer.allocate((endIdExclusive - startIdInclusive) * (9 + serializedEntitySize));
        channel.read(buf);
        buf.position(0);
        for (int i = startIdInclusive; i < endIdExclusive; i++) {
            buf.position((i - startIdInclusive) * (9 + serializedEntitySize));
            EntityNode node = deserializeNode(i, buf);
            if (node.getEntity() != null) {
                result.add(node.getEntity());
            }
        }
        return result;
    }

    @Override
    public Stream<T> getEntityStream()  {
        int bufferSize = 1000;
        Stream<List<T>> stream = IntStream.range(0, (capacity + bufferSize - 1) / bufferSize)
                .mapToObj(rangeId -> {
                    int rangeStart = rangeId * bufferSize;
                    int rangeEnd = Math.min(capacity, (rangeId + 1) * bufferSize);
                    try {
                        return getEntitiesBuffered(rangeStart, rangeEnd);
                    } catch (IOException e) {
                        throw new UncheckedEntityException("Error reading entities", e);
                    }
                });
        return stream.flatMap(List::stream);
    }

    @Override
    public int addEntity(@NonNull T entity) throws IOException, EntityStorageException {
        int entityId;

        SearchResult result = treeSearch(entity);
        if (result.node != null && result.compare == 0) {
            throw new EntityStorageException("An entity with the same key already exists");
        }

        if (firstDeletedEntityId >= 0) {
            // Replace a deleted entity
            entityId = firstDeletedEntityId;
            firstDeletedEntityId = getEntityNode(firstDeletedEntityId).rightEntityId;
        } else {
            // Appended new entity to the end
            entityId = capacity++;
        }
        numEntities++;

        if (result.node == null) {
            rootEntityId = entityId;
        } else {
            if (result.compare < 0) {
                result.node.setLeftEntityId(entityId);
            } else {
                result.node.setRightEntityId(entityId);
            }
            putEntityNode(result.node);
        }
        // TODO: Balance tree

        updateStorageHeader();

        putEntityNode(new EntityNode(entityId, entity));

        log.debug("Successfully added entity to " + storageName + " with id " + entityId);

        return entityId;
    }

    @AllArgsConstructor
    private class SearchResult {
        private int compare;
        private EntityNode node;
    }

    private SearchResult treeSearch(@NonNull T entity) throws IOException {
        return treeSearch(rootEntityId, new SearchResult(0, null), entity);
    }

    private SearchResult treeSearch(int currentId, SearchResult result, @NonNull T entity) throws IOException {
        if (currentId < 0) {
            return result;
        }

        T current = getEntity(currentId);
        EntityNode node = getEntityNode(currentId);
        int comp = entity.compareTo(current);

        result = new SearchResult(comp, node);
        if (comp == 0) {
            return result;
        } else if (comp < 0) {
            return treeSearch(node.leftEntityId, result, entity);
        } else {
            return treeSearch(node.rightEntityId, result, entity);
        }
    }

    @Override
    public void putEntity(int entityId, @NonNull T entity) throws IOException {
        if (entityId < 0 || entityId >= capacity) {
            throw new IllegalArgumentException(String.format("Can't put an entity with id %d when capacity is %d",
                    entityId, capacity));
        }
        if (getEntityNode(entityId).isDeleted()) {
            throw new IllegalArgumentException("Can't replace a deleted entity");
        }

        putEntityNode(new EntityNode(entityId, entity));

        log.debug("Successfully put entity to " + storageName + " with id " + entityId);
    }

    @Override
    public boolean deleteEntity(int entityId) throws IOException {
        EntityNode node = getEntityNode(entityId);
        if (node.isDeleted()) {
            log.debug("Deleted entity with id " + entityId + " that was already deleted");
            return false;
        }

        putEntityNode(createDeletedNode(entityId, firstDeletedEntityId));
        firstDeletedEntityId = entityId;
        numEntities--;
        updateStorageHeader();
        return true;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    /**
     * Validates that the entity headers correctly reflects the order of the entities
     */
    public void validateStructure() throws EntityStorageException, IOException {
        if (rootEntityId == -1) {
            if (getNumEntities() == 0) {
                return;
            }
            throw new EntityStorageException(String.format(
                    "Header says there are %d entities in the storage but the root points to no entity.", getNumEntities()));
        }

        int sum = validate(rootEntityId, null, null);
        if (sum != getNumEntities()) {
            throw new EntityStorageException(String.format(
                    "Found %d entities when traversing the base but the header says there should be %d entities.", sum, getNumEntities()));
        }
    }

    private int validate(int entityId, T min, T max) throws IOException, EntityStorageException {
        // TODO: Validate height difference of left and right tree
        EntityNode node = getEntityNode(entityId);
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
}
