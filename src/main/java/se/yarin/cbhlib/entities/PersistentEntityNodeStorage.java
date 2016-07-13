package se.yarin.cbhlib.entities;

import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardOpenOption.*;

class PersistentEntityNodeStorage<T extends Entity & Comparable<T>> extends EntityNodeStorageBase<T> {
    private static final Logger log = LoggerFactory.getLogger(PersistentEntityNodeStorage.class);

    private static final int MAGIC_CONSTANT = 1234567890;

    private final EntitySerializer<T> serializer;
    private final String storageName;

    private FileChannel channel;
    private int entityOffset;
    private int serializedEntitySize;

    PersistentEntityNodeStorage(File file, EntitySerializer<T> serializer)
            throws IOException {
        storageName = file.getName();

        this.serializer = serializer;
        channel = FileChannel.open(file.toPath(), READ, WRITE);
        EntityNodeStorageMetadata metadata = getMetadata();
        entityOffset = metadata.getEntityOffset();
        serializedEntitySize = metadata.getSerializedEntitySize();

        log.debug(String.format("Opening %s; capacity = %d, root = %d, numEntities = %d, firstDeletedId = %d",
                storageName, metadata.getCapacity(), metadata.getRootEntityId(),
                metadata.getNumEntities(), metadata.getFirstDeletedEntityId()));
    }

    public static <T extends Entity> void createEmptyStorage(File file, EntitySerializer<T> serializer)
            throws IOException {
        FileChannel channel = FileChannel.open(file.toPath(), CREATE_NEW, READ, WRITE);
        EntityNodeStorageMetadata metadata = new EntityNodeStorageMetadata(
                serializer.getSerializedEntityLength(), 32);
        channel.write(serializeMetadata(metadata));
        channel.close();
    }

    public EntityNodeStorageMetadata getMetadata() throws IOException {
        channel.position(0);
        ByteBuffer header = ByteBuffer.allocate(32);
        channel.read(header);
        header.position(0);

        int capacity = ByteBufferUtil.getIntL(header);
        int rootEntityId= ByteBufferUtil.getIntL(header);
        int headerInt = ByteBufferUtil.getIntL(header);
        if (headerInt != MAGIC_CONSTANT) {
            // Not sure what this is!?
            throw new IOException("Invalid header int: " + headerInt);
        }
        int serializedEntitySize = ByteBufferUtil.getIntL(header);
        int firstDeletedId = ByteBufferUtil.getIntL(header);
        int numEntities = ByteBufferUtil.getIntL(header);
        int entityOffset = 28 + ByteBufferUtil.getIntL(header);

        EntityNodeStorageMetadata metadata = new EntityNodeStorageMetadata(serializedEntitySize, entityOffset);
        metadata.setCapacity(capacity);
        metadata.setRootEntityId(rootEntityId);
        metadata.setFirstDeletedEntityId(firstDeletedId);
        metadata.setNumEntities(numEntities);

        return metadata;
    }

    private static ByteBuffer serializeMetadata(EntityNodeStorageMetadata metadata) {
        ByteBuffer buffer = ByteBuffer.allocate(32);
        ByteBufferUtil.putIntL(buffer, metadata.getCapacity());
        ByteBufferUtil.putIntL(buffer, metadata.getRootEntityId());
        ByteBufferUtil.putIntL(buffer, MAGIC_CONSTANT);
        ByteBufferUtil.putIntL(buffer, metadata.getSerializedEntitySize());
        ByteBufferUtil.putIntL(buffer, metadata.getFirstDeletedEntityId());
        ByteBufferUtil.putIntL(buffer, metadata.getNumEntities());
        ByteBufferUtil.putIntL(buffer, metadata.getEntityOffset() - 28);

        buffer.position(0);
        return buffer;
    }

    public void putMetadata(EntityNodeStorageMetadata metadata) throws IOException {
        ByteBuffer buffer = serializeMetadata(metadata);

        channel.position(0);
        channel.write(buffer);

        log.debug(String.format("Updated %s; capacity = %d, root = %d, numEntities = %d, firstDeletedId = %d",
                storageName, metadata.getCapacity(), metadata.getRootEntityId(), metadata.getNumEntities(),
                metadata.getFirstDeletedEntityId()));
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


    @Override
    protected EntityNode<T> getEntityNode(int entityId) throws IOException {
        positionChannel(entityId);
        ByteBuffer buf = ByteBuffer.allocate(9 + serializedEntitySize);
        channel.read(buf);
        buf.position(0);

        EntityNode<T> entityNode = deserializeNode(entityId, buf);

        if (log.isTraceEnabled()) {
            log.trace("Read entity node: " + entityNode);
        }

        return entityNode;
    }

    /**
     * Gets all entity node in the specified range. Deleted entities will be omitted,
     * so the resulting array may be shorter than the specified range.
     */
    protected List<EntityNode<T>> getEntityNodes(int startIdInclusive, int endIdExclusive) throws IOException {
        if (startIdInclusive >= endIdExclusive) {
            return new ArrayList<>();
        }
        if (log.isTraceEnabled()) {
            log.trace(String.format("getEntitiesBuffered [%d, %d)", startIdInclusive, endIdExclusive));
        }
        ArrayList<EntityNode<T>> result = new ArrayList<>(endIdExclusive - startIdInclusive);
        positionChannel(startIdInclusive);
        ByteBuffer buf = ByteBuffer.allocate((endIdExclusive - startIdInclusive) * (9 + serializedEntitySize));
        channel.read(buf);
        buf.position(0);
        for (int i = startIdInclusive; i < endIdExclusive; i++) {
            buf.position((i - startIdInclusive) * (9 + serializedEntitySize));
            EntityNode<T> node = deserializeNode(i, buf);
            if (!node.isDeleted()) {
                result.add(node);
            }
        }
        return result;
    }

    @Override
    protected void putEntityNode(@NonNull EntityNode<T> node) throws IOException {
        positionChannel(node.getEntityId());
        ByteBuffer src = serializeNode(node);
        src.position(0);
        channel.write(src);

        if (log.isDebugEnabled()) {
            log.debug(String.format("Successfully put entity to %s: %s", storageName, node.toString()));
        }
    }

    @Override
    public EntityNode<T> createNode(int entityId, T entity) {
        if (entity == null) {
            // If creating a deleted node
            return new SerializedEntityNode(entityId, -1, -1, 0, new byte[serializedEntitySize], null);
        }
        return new SerializedEntityNode(entityId, -1, -1, 0, entity);
    }

    @Override
    public void close() throws IOException {
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
        ByteBuffer buf = ByteBuffer.allocate(9 + serializedEntitySize);
        ByteBufferUtil.putIntL(buf, node.getLeftEntityId());
        ByteBufferUtil.putIntL(buf, node.getRightEntityId());
        ByteBufferUtil.putByte(buf, node.getHeightDif());
        buf.put(((SerializedEntityNode) node).getSerializedEntity());
        return buf;
    }

    private EntityNode<T> deserializeNode(int entityId, ByteBuffer buf) {
        int leftEntityId = ByteBufferUtil.getIntL(buf);
        int rightEntityId = ByteBufferUtil.getIntL(buf);
        int heightDif = ByteBufferUtil.getSignedByte(buf);
        // Only deserialize the actual entity on demand
        byte[] serializedEntity = new byte[serializedEntitySize];
        buf.get(serializedEntity);

        return new SerializedEntityNode(entityId, leftEntityId, rightEntityId, heightDif, serializedEntity);
    }
}
