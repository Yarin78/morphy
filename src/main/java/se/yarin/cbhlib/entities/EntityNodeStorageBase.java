package se.yarin.cbhlib.entities;

import lombok.*;
import se.yarin.cbhlib.ByteBufferUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public abstract class EntityNodeStorageBase<T extends Entity> {
    private static final int ENTITY_DELETED = -999;

    private final EntitySerializer<T> serializer;

    // The serialized size could be different if opening an existing database,
    // so it must be possible to change it after construction time
    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PROTECTED)
    private int serializedEntitySize;

    @Getter(AccessLevel.PUBLIC)
    @Setter(AccessLevel.PROTECTED)
    private int capacity;

    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PROTECTED)
    private int rootEntityId; // The id of the root entity in the tree

    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PROTECTED)
    private int firstDeletedEntityId;

    @Getter(AccessLevel.PUBLIC)
    @Setter(AccessLevel.PROTECTED)
    private int numEntities;

    @Getter(AccessLevel.PUBLIC)
    private final String storageName;


    public EntityNodeStorageBase(@NonNull String storageName, @NonNull EntitySerializer<T> serializer) {
        this.storageName = storageName;
        this.serializer = serializer;
        this.serializedEntitySize = serializer.getSerializedEntityLength();
    }

    /**
     * Gets the entity node for the specified entity id.
     * @param entityId the id of the entity node to get
     * @return the entity node
     * @throws IOException if some IO error occurred
     */
    protected abstract EntityNode getEntityNode(int entityId) throws IOException;

    /**
     * Gets all entity node in the specified range. Deleted entities will be omitted,
     * so the resulting array may be shorter than the specified range.
     * @param startIdInclusive the id of the first entity to get
     * @param endIdExclusive the id of the first entity <i>not</i> to get
     * @return a list of all entities between startIdInclusive and endIdExclusive
     * @throws IOException if some IO error occurred
     */
    protected abstract List<EntityNode> getEntityNodes(int startIdInclusive, int endIdExclusive) throws IOException;

    /**
     * Puts an entity node in the storage
     * @param node the node to put. The entityId must be set.
     * @throws IOException if some IO error occurred
     */
    protected abstract void putEntityNode(@NonNull EntityNode node) throws IOException;

    public EntityNode createNode(int entityId, T entity) {
        return new EntityNode(entityId, entity);
    }

    public EntityNode createNode(T entity) {
        return new EntityNode(entity);
    }

    public EntityNode createDeletedNode(int entityId) {
        EntityNode node = new EntityNode();
        node.entityId = entityId;
        node.setLeftEntityId(ENTITY_DELETED);
        node.setRightEntityId(getFirstDeletedEntityId());
        node.setHeightDif(0);
        node.serializedEntity = new byte[serializedEntitySize];
        return node;
    }

    /**
     * Closes the storage
     */
    public abstract void close() throws IOException;

    /**
     * An entity node in the entity tree, including its data.
     * If the entity is deleted, then it contains a link to the next deleted entity.
     */
    @Data
    protected class EntityNode {
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

    protected EntityNode deserializeNode(int entityId, ByteBuffer buf) {
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

    protected ByteBuffer serializeNode(EntityNode node) {
        ByteBuffer buf = ByteBuffer.allocate(9 + serializedEntitySize);
        ByteBufferUtil.putIntL(buf, node.getLeftEntityId());
        ByteBufferUtil.putIntL(buf, node.getRightEntityId());
        ByteBufferUtil.putByte(buf, node.getHeightDif());
        buf.put(node.serializedEntity);
        return buf;
    }


    public abstract void updateStorageHeader() throws IOException;
}
