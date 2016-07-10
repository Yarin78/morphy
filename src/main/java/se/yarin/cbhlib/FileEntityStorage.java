package se.yarin.cbhlib;

import lombok.Data;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.*;

public class FileEntityStorage<T extends Entity> extends EntityStorageBase<T> implements EntityStorage<T> {

    private static final Logger log = LoggerFactory.getLogger(FileEntityStorage.class);

    private static final int MAGIC_CONSTANT = 1234567890;
    private static final int ENTITY_DELETED = -999;

    private final EntitySerializer<T> serializer;
    private final int serializedEntitySize;

    private int capacity;
    private int rootEntityId;
    private int firstDeletedEntityId;
    private int numEntities;
    private int entityOffset;
    private String storageName;
    private FileChannel channel;

    /**
     * A small structure preceding each entity in the storage.
     * Mostly legacy, used to keep the ordered structure in the blob.
     * Now it's only used to keep track of deleted entities.
     */
    @Data
    static class EntityHeader {
        // The id of the preceding entity id. If -999, this entity is deleted.
        private int leftEntityId = -1;
        // The id of the succeeding entity id. If this entity is deleted, this points to the next deleted entity.
        private int rightEntityId = -1;
        // Height of left tree - height of right tree. Used for balancing the binary tree.
        private int heightDif = 0;

        private boolean isDeleted() {
            return leftEntityId == ENTITY_DELETED;
        }

        static EntityHeader deserialize(ByteBuffer buf) {
            EntityHeader header = new EntityHeader();
            header.leftEntityId = ByteBufferUtil.getIntL(buf);
            header.rightEntityId = ByteBufferUtil.getIntL(buf);
            header.heightDif = ByteBufferUtil.getSignedByte(buf);
            return header;
        }
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

    public static <T extends Entity> FileEntityStorage open(File file, @NonNull EntitySerializer<T> serializer)
            throws IOException {
        return new FileEntityStorage<>(file, serializer, false);
    }

    public static <T extends Entity> FileEntityStorage create(File file, @NonNull EntitySerializer<T> serializer)
            throws IOException {
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
     * Gets the entity header for the specified entity id.
     * The channel position is updated and points to the serialized entity data.
     * @param entityId the id of the entity to get the header for
     * @return the entity header
     * @throws IOException if some IO error occurred
     * @throws EntityStorageException if the entityId was outside the capacity
     */
    private EntityHeader getEntityHeader(int entityId) throws IOException, EntityStorageException {
        if (entityId < 0 || entityId >= capacity) {
            throw new EntityStorageException("Invalid entity id " + entityId + "; capacity is " + capacity);
        }

        positionChannel(entityId);
        ByteBuffer headerBuf = ByteBuffer.allocate(9);
        channel.read(headerBuf);
        headerBuf.position(0);

        EntityHeader header = EntityHeader.deserialize(headerBuf);

        if (log.isTraceEnabled()) {
            log.trace("Entity id " + entityId + " in " + storageName + " has header " + header);
        }

        return header;
    }

    private void putEntityHeader(int entityId, EntityHeader header) throws EntityStorageException, IOException {
        if (entityId < 0 || entityId > capacity) {
            throw new EntityStorageException("Can't write entity header with id " + entityId + "; capacity is " + capacity);
        }

        positionChannel(entityId);
        ByteBuffer headerBuf = ByteBuffer.allocate(9);
        ByteBufferUtil.putIntL(headerBuf, header.getLeftEntityId());
        ByteBufferUtil.putIntL(headerBuf, header.getRightEntityId());
        ByteBufferUtil.putByte(headerBuf, header.getHeightDif());

        headerBuf.position(0);
        channel.write(headerBuf);
    }

    public int nextDeletedBlobId(int entityId) throws IOException, EntityStorageException {
        EntityHeader entityHeader = getEntityHeader(entityId);
        return entityHeader.rightEntityId;
    }

    public int getInsertId() {
        return firstDeletedEntityId >= 0 ? firstDeletedEntityId : capacity;
    }


    @Override
    public int getNumEntities() {
        return numEntities;
    }

    @Override
    public T getEntity(int entityId) throws EntityStorageException, IOException {
        if (entityId < 0 || entityId >= capacity) {
            return null;
        }
        EntityHeader blobHeader = getEntityHeader(entityId);
        if (blobHeader.isDeleted()) {
            return null;
        }
        ByteBuffer blob = ByteBuffer.allocate(serializedEntitySize);
        channel.read(blob);
        blob.position(0);
        return serializer.deserialize(entityId, blob);
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
            EntityHeader header = EntityHeader.deserialize(buf);
            if (!header.isDeleted()) {
                result.add(serializer.deserialize(i, buf));
            }
        }
        return result;
    }

    @Override
    public Stream<T> getEntityStream()  {
//        return IntStream.range(0, capacity).mapToObj(id -> getEntitySafe(id)).filter(entity -> entity != null);

//        final Stream<List<Integer>> stream = Stream.empty();
//        final List<Integer> two = stream.flatMap(List::stream).collect(Collectors.toList());

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
    public int addEntity(@NonNull T entity) throws EntityStorageException, IOException {
        int entityId = getInsertId();
        putEntity(entityId, entity);
        return entityId;
    }

    @Override
    public void putEntity(int entityId, @NonNull T entity) throws EntityStorageException, IOException {
        if (entityId < 0 || entityId > capacity) {
            throw new EntityStorageException("Can't put an entity with id " + entityId + " when capacity is " + capacity);
        }
        if (entityId < capacity && getEntityHeader(entityId).isDeleted() && entityId != firstDeletedEntityId) {
            // This would break the linked list of deleted entities
            throw new EntityStorageException("Can't put an entity over a randomly deleted entity");
        }
        putEntityHeader(entityId, new EntityHeader());
        ByteBuffer buffer = serializer.serialize(entity);
        buffer.position(0);
        channel.write(buffer);

        if (entityId == firstDeletedEntityId) {
            // Replace a deleted entity
            firstDeletedEntityId = nextDeletedBlobId(firstDeletedEntityId);
            updateStorageHeader();
        } else if (entityId == capacity) {
            // Appended new entity to the end
            numEntities++;
            capacity++;
            updateStorageHeader();
        } // Otherwise it was a replacement and no stats have changed
        log.debug("Successfully put entity blob to " + storageName + " with id " + entityId);
    }

    @Override
    public boolean deleteEntity(int entityId) throws EntityStorageException, IOException {
        EntityHeader header = getEntityHeader(entityId);
        if (header.isDeleted()) {
            log.debug("Deleted entity with id " + entityId + " that was already deleted");
            return false;
        }
        header.setLeftEntityId(ENTITY_DELETED);
        header.setRightEntityId(firstDeletedEntityId);
        header.setHeightDif(0);
        putEntityHeader(entityId, header);
        firstDeletedEntityId = entityId;
        numEntities--;
        updateStorageHeader();
        return true;
    }
}
