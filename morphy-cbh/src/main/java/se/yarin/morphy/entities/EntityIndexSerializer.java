package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.util.ByteBufferUtil;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.storage.ItemStorageSerializer;

import java.nio.ByteBuffer;

public class EntityIndexSerializer implements ItemStorageSerializer<EntityIndexHeader, EntityNode> {
    private static final Logger log = LoggerFactory.getLogger(EntityIndexSerializer.class);

    // The record size for the primary entity index file is fixed across all versions
    private final int recordSize;

    public EntityIndexSerializer(int recordSize) {
        // Record size _including_ count and firstGameId
        this.recordSize = recordSize;
    }

    @Override
    public int serializedHeaderSize() {
        return 32;
    }

    @Override
    public long itemOffset(EntityIndexHeader header, int index) {
        return header.headerSize() + (long) index * itemSize(header);
    }

    @Override
    public int itemSize(EntityIndexHeader header) {
        return header.entitySize() + 9;  // 9 additional bytes for the node tree
    }

    @Override
    public int headerSize(@NotNull EntityIndexHeader header) {
        return header.headerSize();
    }

    @Override
    public EntityIndexHeader deserializeHeader(ByteBuffer buf) throws MorphyInvalidDataException {
        int capacity = ByteBufferUtil.getIntL(buf);
        int rootEntityId = ByteBufferUtil.getIntL(buf);
        if (capacity == 0 && rootEntityId == 0) {
            // In many team databases both of these are 0, even though root ought to be -1
            rootEntityId = -1;
        }
        if (rootEntityId >= capacity) {
            throw new MorphyInvalidDataException(String.format("Root entity is %d but capacity %d", rootEntityId, capacity));
        }
        int constant = ByteBufferUtil.getIntL(buf);
        if (constant != EntityIndexHeader.MAGIC_CONSTANT) {
            // Not sure what this is!?
            throw new MorphyInvalidDataException(String.format("Invalid magic constant in header; was %d, expected %d",
                    constant, EntityIndexHeader.MAGIC_CONSTANT));
        }
        int serializedEntitySize = ByteBufferUtil.getIntL(buf);
        if (serializedEntitySize != recordSize) {
            throw new MorphyInvalidDataException(String.format("Invalid record size in entity index; was %d, expected %d", serializedEntitySize, recordSize));
        }
        int firstDeletedId = ByteBufferUtil.getIntL(buf);
        int numEntities = ByteBufferUtil.getIntL(buf);
        int numPaddingBytes = ByteBufferUtil.getIntL(buf);
        buf.position(buf.position() + numPaddingBytes);
        return ImmutableEntityIndexHeader.builder()
                .capacity(capacity)
                .rootNodeId(rootEntityId)
                .deletedEntityId(firstDeletedId)
                .entitySize(serializedEntitySize)
                .numEntities(numEntities)
                .headerSize(28 + numPaddingBytes)
                .build();
    }

    @Override
    public void serializeHeader(EntityIndexHeader header, ByteBuffer buf) {
        ByteBufferUtil.putIntL(buf, header.capacity());
        ByteBufferUtil.putIntL(buf, header.rootNodeId());
        ByteBufferUtil.putIntL(buf, EntityIndexHeader.MAGIC_CONSTANT);
        ByteBufferUtil.putIntL(buf, header.entitySize());
        ByteBufferUtil.putIntL(buf, header.deletedEntityId());
        ByteBufferUtil.putIntL(buf, header.numEntities());
        ByteBufferUtil.putIntL(buf, 4);  // num padding bytes
        ByteBufferUtil.putIntL(buf, 0);  // padding bytes
    }

    @Override
    public EntityNode deserializeItem(int id, ByteBuffer buf) {
        byte[] serializedEntity = new byte[recordSize - 8];

        int leftEntityId = ByteBufferUtil.getIntL(buf);
        int rightEntityId = ByteBufferUtil.getIntL(buf);
        int heightDif = ByteBufferUtil.getSignedByte(buf);
        buf.get(serializedEntity);
        int count = ByteBufferUtil.getIntL(buf);
        int firstGameId = ByteBufferUtil.getIntL(buf);
        return new EntityNode(id, leftEntityId, rightEntityId, heightDif, count, firstGameId, serializedEntity);
    }

    @Override
    public void serializeItem(EntityNode node, ByteBuffer buf) {
        ByteBufferUtil.putIntL(buf, node.getLeftChildId());
        ByteBufferUtil.putIntL(buf, node.getRightChildId());
        ByteBufferUtil.putByte(buf, node.getBalance());
        byte[] serializedEntity = node.getSerializedEntity();
        assert serializedEntity.length == recordSize - 8;
        buf.put(serializedEntity);
        ByteBufferUtil.putIntL(buf, node.getGameCount());
        ByteBufferUtil.putIntL(buf, node.getFirstGameId());
    }
}
