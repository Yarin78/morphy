package se.yarin.cbhlib.entities;

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

class PersistentEntityNodeStorage<T extends Entity> extends EntityNodeStorageBase {
    private static final Logger log = LoggerFactory.getLogger(PersistentEntityNodeStorage.class);

    private static final int MAGIC_CONSTANT = 1234567890;

    private FileChannel channel;
    private int entityOffset;

    PersistentEntityNodeStorage(File file, EntitySerializer<T> serializer, boolean create)
            throws IOException {
        super(file.getName(), serializer);

        if (create) {
            channel = FileChannel.open(file.toPath(), CREATE_NEW, READ, WRITE);

            setCapacity(0);
            setRootEntityId(-1);
            setNumEntities(0);
            setFirstDeletedEntityId(-1);
            entityOffset = 32;
            updateStorageHeader();
        } else {
            channel = FileChannel.open(file.toPath(), READ, WRITE);
            ByteBuffer header = ByteBuffer.allocate(32);
            channel.read(header);
            header.position(0);

            setCapacity(ByteBufferUtil.getIntL(header));
            setRootEntityId(ByteBufferUtil.getIntL(header));
            int headerInt = ByteBufferUtil.getIntL(header);
            if (headerInt != MAGIC_CONSTANT) {
                // Not sure what this is!?
                throw new IOException("Invalid header int: " + headerInt);
            }
            setSerializedEntitySize(ByteBufferUtil.getIntL(header));
            setFirstDeletedEntityId(ByteBufferUtil.getIntL(header));
            setNumEntities(ByteBufferUtil.getIntL(header));
            entityOffset = 28 + ByteBufferUtil.getIntL(header);

            log.debug(String.format("Opening %s; capacity = %d, root = %d, numEntities = %d, firstDeletedId = %d",
                    getStorageName(), getCapacity(), getRootEntityId(), getNumEntities(), getFirstDeletedEntityId()));
        }
    }

    @Override
    public void updateStorageHeader() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(32);
        ByteBufferUtil.putIntL(buffer, getCapacity());
        ByteBufferUtil.putIntL(buffer, getRootEntityId());
        ByteBufferUtil.putIntL(buffer, MAGIC_CONSTANT);
        ByteBufferUtil.putIntL(buffer, getSerializedEntitySize());
        ByteBufferUtil.putIntL(buffer, getFirstDeletedEntityId());
        ByteBufferUtil.putIntL(buffer, getNumEntities());
        ByteBufferUtil.putIntL(buffer, entityOffset - 28);

        buffer.position(0);

        channel.position(0);
        channel.write(buffer);

        log.debug(String.format("Updated %s; capacity = %d, root = %d, numEntities = %d, firstDeletedId = %d",
                getStorageName(), getCapacity(), getRootEntityId(), getNumEntities(), getFirstDeletedEntityId()));
    }

    /**
     * Positions the channel at the start of the specified entityId.
     * Valid positions are between 0 and capacity (to allow for adding new entities)
     * @param entityId the entityId to position to channel against
     * @throws IOException
     */
    private void positionChannel(int entityId) throws IOException {
        channel.position(entityOffset + entityId * (9 + getSerializedEntitySize()));
    }


    @Override
    protected EntityNode getEntityNode(int entityId) throws IOException {
        if (entityId < 0 || entityId >= getCapacity()) {
            throw new IllegalArgumentException("Invalid entity id " + entityId + "; capacity is " + getCapacity());
        }

        positionChannel(entityId);
        ByteBuffer buf = ByteBuffer.allocate(9 + getSerializedEntitySize());
        channel.read(buf);
        buf.position(0);

        EntityNode entityNode = deserializeNode(entityId, buf);

        if (log.isTraceEnabled()) {
            log.trace("Read entity node: " + entityNode);
        }

        return entityNode;
    }

    /**
     * Gets all entity node in the specified range. Deleted entities will be omitted,
     * so the resulting array may be shorter than the specified range.
     */
    protected List<EntityNode> getEntityNodes(int startIdInclusive, int endIdExclusive) throws IOException {
        if (startIdInclusive < 0 || startIdInclusive > getCapacity()) {
            throw new IllegalArgumentException(String.format(
                    "start must be within the capacity of the storage (capacity = %d, start = %d)",
                    getCapacity(), startIdInclusive));
        }
        if (endIdExclusive < 0 || endIdExclusive > getCapacity()) {
            throw new IllegalArgumentException(String.format(
                    "end must be within the capacity of the storage (capacity = %d, end = %d)",
                    getCapacity(), endIdExclusive));
        }
        if (startIdInclusive >= endIdExclusive) {
            return new ArrayList<>();
        }
        if (log.isTraceEnabled()) {
            log.trace(String.format("getEntitiesBuffered [%d, %d)", startIdInclusive, endIdExclusive));
        }
        ArrayList<EntityNode> result = new ArrayList<>(endIdExclusive - startIdInclusive);
        positionChannel(startIdInclusive);
        ByteBuffer buf = ByteBuffer.allocate((endIdExclusive - startIdInclusive) * (9 + getSerializedEntitySize()));
        channel.read(buf);
        buf.position(0);
        for (int i = startIdInclusive; i < endIdExclusive; i++) {
            buf.position((i - startIdInclusive) * (9 + getSerializedEntitySize()));
            EntityNode node = deserializeNode(i, buf);
            if (!node.isDeleted()) {
                result.add(node);
            }
        }
        return result;
    }

    @Override
    protected void putEntityNode(@NonNull EntityNode node) throws IOException {
        if (node.getEntityId() < 0 || node.getEntityId() > getCapacity()) {
            throw new IllegalArgumentException(String.format("Can't write entity header with id %d; capacity is %d",
                    node.getEntityId(), getCapacity()));
        }

        positionChannel(node.getEntityId());
        ByteBuffer src = serializeNode(node);
        src.position(0);
        channel.write(src);

        if (log.isDebugEnabled()) {
            log.debug(String.format("Put entity node: " + node));
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
