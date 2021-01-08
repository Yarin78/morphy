package se.yarin.cbhlib.storage;

import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.exceptions.ChessBaseIOException;
import se.yarin.cbhlib.util.ByteBufferUtil;
import se.yarin.cbhlib.entities.Entity;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardOpenOption.*;

public class PersistentEntityNodeStorage<T extends Entity & Comparable<T>> extends EntityNodeStorageBase<T> {
    private static final Logger log = LoggerFactory.getLogger(PersistentEntityNodeStorage.class);

    private static final int MAGIC_CONSTANT = 1234567890;

    private final EntitySerializer<T> serializer;
    private final String storageName;
    private final int serializedEntitySize;
    private final int headerSize;

    private final FileChannel channel;

    public PersistentEntityNodeStorage(File file, EntitySerializer<T> serializer)
            throws IOException {
        super(loadMetadata(file));

        this.serializedEntitySize = getMetadata().getSerializedEntitySize();
        this.headerSize = getMetadata().getHeaderSize();
        this.storageName = file.getName();
        this.serializer = serializer;
        this.channel = FileChannel.open(file.toPath(), READ, WRITE);

        log.debug(String.format("Opening %s; capacity = %d, root = %d, numEntities = %d, firstDeletedId = %d",
                storageName, getCapacity(), getRootEntityId(), getNumEntities(), getFirstDeletedEntityId()));
    }

    public static <T extends Entity> void createEmptyStorage(File file, EntitySerializer<T> serializer, int headerSize)
            throws IOException {
        if (headerSize < 28) {
            throw new IllegalArgumentException("The size of the header must be at least 28 bytes");
        }
        FileChannel channel = FileChannel.open(file.toPath(), CREATE_NEW, READ, WRITE);
        EntityNodeStorageMetadata metadata = new EntityNodeStorageMetadata(
                serializer.getSerializedEntityLength(), headerSize, 0);
        channel.write(serializeMetadata(metadata));
        channel.close();
    }

    private static EntityNodeStorageMetadata loadMetadata(File file) throws IOException {
        try (FileChannel channel = FileChannel.open(file.toPath(), READ)) {
            channel.position(0);
            // Only 28 bytes of the header is used by this library.
            // The last 4 bytes of those is an integer specifying number of additional bytes
            // until the first entity. It used by be 0 in older versions of ChessBase, but is now 4.
            // What these 4 bytes are used for is unknown.
            ByteBuffer header = ByteBuffer.allocate(28);
            channel.read(header);
            header.position(0);

            int capacity = ByteBufferUtil.getIntL(header);
            int rootEntityId = ByteBufferUtil.getIntL(header);
            int headerInt = ByteBufferUtil.getIntL(header);
            if (headerInt != MAGIC_CONSTANT) {
                // Not sure what this is!?
                throw new IOException("Invalid header int: " + headerInt);
            }
            int serializedEntitySize = ByteBufferUtil.getIntL(header);
            int firstDeletedId = ByteBufferUtil.getIntL(header);
            int numEntities = ByteBufferUtil.getIntL(header);
            int headerSize = 28 + ByteBufferUtil.getIntL(header);

            EntityNodeStorageMetadata metadata = new EntityNodeStorageMetadata(serializedEntitySize, headerSize, 0);
            metadata.setCapacity(capacity);
            metadata.setRootEntityId(rootEntityId);
            metadata.setFirstDeletedEntityId(firstDeletedId);
            metadata.setNumEntities(numEntities);
            return metadata;
        }
    }

    private static ByteBuffer serializeMetadata(EntityNodeStorageMetadata metadata) {
        ByteBuffer buffer = ByteBuffer.allocate(metadata.getHeaderSize());
        ByteBufferUtil.putIntL(buffer, metadata.getCapacity());
        ByteBufferUtil.putIntL(buffer, metadata.getRootEntityId());
        ByteBufferUtil.putIntL(buffer, MAGIC_CONSTANT);
        ByteBufferUtil.putIntL(buffer, metadata.getSerializedEntitySize());
        ByteBufferUtil.putIntL(buffer, metadata.getFirstDeletedEntityId());
        ByteBufferUtil.putIntL(buffer, metadata.getNumEntities());
        ByteBufferUtil.putIntL(buffer, metadata.getHeaderSize() - 28);

        buffer.position(0);
        return buffer;
    }

    @Override
    public synchronized void setMetadata(EntityNodeStorageMetadata metadata) {
        // Update the in-memory metadata cache as well
        super.setMetadata(metadata);

        ByteBuffer buffer = serializeMetadata(metadata);

        try {
            channel.position(0);
            channel.write(buffer);
        } catch (IOException e) {
            throw new ChessBaseIOException("Failed to write metadata to entity storage " + storageName);
        }

        if (log.isDebugEnabled()) {
            log.debug(String.format("Updated %s; capacity = %d, root = %d, numEntities = %d, firstDeletedId = %d",
                    storageName, metadata.getCapacity(), metadata.getRootEntityId(), metadata.getNumEntities(),
                    metadata.getFirstDeletedEntityId()));
        }
    }

    /**
     * Positions the channel at the start of the specified entityId.
     * Valid positions are between 0 and capacity (to allow for adding new entities)
     * @param entityId the entityId to position to channel against
     * @throws IOException if an IO error occurs
     */
    private void positionChannel(int entityId) throws IOException {
        channel.position(headerSize + (long) entityId * (9 + serializedEntitySize));
    }


    @Override
    public synchronized EntityNode<T> getEntityNode(int entityId) {
        ByteBuffer buf = ByteBuffer.allocate(9 + serializedEntitySize);
        try {
            positionChannel(entityId);
            channel.read(buf);
        } catch (IOException e) {
            throw new ChessBaseIOException("Failed to get entity node " + entityId + " in " + storageName);
        }
        buf.position(0);

        EntityNode<T> entityNode = deserializeNode(entityId, buf);

        if (log.isTraceEnabled()) {
            log.trace("Read entity node: " + entityNode);
        }

        return entityNode;
    }

    /**
     * Gets all entity node in the specified range.
     */
    public synchronized List<EntityNode<T>> getEntityNodes(int startIdInclusive, int endIdExclusive) {
        if (startIdInclusive >= endIdExclusive) {
            return new ArrayList<>();
        }
        if (log.isTraceEnabled()) {
            log.trace(String.format("getEntitiesBuffered [%d, %d)", startIdInclusive, endIdExclusive));
        }
        ArrayList<EntityNode<T>> result = new ArrayList<>(endIdExclusive - startIdInclusive);
        ByteBuffer buf = ByteBuffer.allocate((endIdExclusive - startIdInclusive) * (9 + serializedEntitySize));
        try {
            positionChannel(startIdInclusive);
            channel.read(buf);
        } catch (IOException e) {
            throw new ChessBaseIOException("Failed to get entity nodes in range [%d, %d) in %s".formatted(startIdInclusive, endIdExclusive, storageName));
        }
        buf.position(0);
        for (int i = startIdInclusive; i < endIdExclusive; i++) {
            buf.position((i - startIdInclusive) * (9 + serializedEntitySize));
            result.add(deserializeNode(i, buf));
        }
        return result;
    }

    @Override
    public synchronized void putEntityNode(@NonNull EntityNode<T> node) {
        ByteBuffer src = serializeNode(node);
        src.position(0);
        try {
            positionChannel(node.getEntityId());
            channel.write(src);
        } catch (IOException e) {
            throw new ChessBaseIOException("Failed to put entity node " + node.getEntityId() + " in " + storageName);
        }

        if (log.isDebugEnabled()) {
            log.debug(String.format("Successfully put entity to %s: %s", storageName, node.toString()));
        }
    }

    @Override
    public EntityNode<T> createNode(int entityId, T entity) {
        if (entity == null) {
            // If creating a deleted node
            return new SerializedEntityNode(entityId, -1, -1, 0, new byte[serializer.getSerializedEntityLength()], null);
        }
        return new SerializedEntityNode(entityId, -1, -1, 0, entity);
    }

    @Override
    public synchronized void close() throws IOException {
        channel.close();
    }

    private class SerializedEntityNode extends EntityNodeImpl<T> {
        @Getter
        private final byte[] serializedEntity;

        private T entityCache;

        SerializedEntityNode(int entityId, int leftEntityId, int rightEntityId, int heightDif,
                             byte[] serializedEntity) {
            this(entityId, leftEntityId, rightEntityId, heightDif, serializedEntity, null);
        }

        SerializedEntityNode(int entityId, int leftEntityId, int rightEntityId, int heightDif,
                             T entity) {
            this(entityId, leftEntityId, rightEntityId, heightDif, serializer.serialize(entity).array(), entity);
        }

        private SerializedEntityNode(int entityId, int leftEntityId, int rightEntityId, int heightDif, byte[] serializedEntity, T entity) {
            super(entityId, entity, leftEntityId, rightEntityId, heightDif);
            this.serializedEntity = serializedEntity;
        }

        public EntityNode<T> update(int newLeftEntityId, int newRightEntityId, int newHeightDif) {
            return new SerializedEntityNode(getEntityId(), newLeftEntityId, newRightEntityId, newHeightDif,
                    serializedEntity, getEntity());
        }

        @Override
        public T getEntity() {
            if (isDeleted()) {
                return null;
            }
            if (entityCache == null) {
                entityCache = serializer.deserialize(getEntityId(), ByteBuffer.wrap(serializedEntity));
            }
            return entityCache;
        }
    }

    private ByteBuffer serializeNode(EntityNode<T> node) {
        ByteBuffer buf = ByteBuffer.allocate(9 + serializer.getSerializedEntityLength());
        ByteBufferUtil.putIntL(buf, node.getLeftEntityId());
        ByteBufferUtil.putIntL(buf, node.getRightEntityId());
        ByteBufferUtil.putByte(buf, node.getHeightDif());
        buf.put(((SerializedEntityNode) node).getSerializedEntity());
        // Truncate buffer if working with a database with shorter entity size
        buf.limit(Math.min(buf.limit(), 9 + serializedEntitySize));
        return buf;
    }

    private EntityNode<T> deserializeNode(int entityId, ByteBuffer buf) {
        int leftEntityId = ByteBufferUtil.getIntL(buf);
        int rightEntityId = ByteBufferUtil.getIntL(buf);
        int heightDif = ByteBufferUtil.getSignedByte(buf);
        // Only deserialize the actual entity on demand
        byte[] serializedEntity = new byte[serializer.getSerializedEntityLength()];
        // Allow reading databases with different entity sizes
        buf.get(serializedEntity, 0, Math.min(serializedEntitySize, serializer.getSerializedEntityLength()));

        return new SerializedEntityNode(entityId, leftEntityId, rightEntityId, heightDif, serializedEntity);
    }
}
