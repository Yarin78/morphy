package se.yarin.cbhlib.entities;

import lombok.Data;

@Data
class EntityNodeStorageMetadata {
    private final int serializedEntitySize;
    private int rootEntityId = -1;
    private int numEntities = 0;
    private int firstDeletedEntityId = -1;
    private int capacity = 0;
    private final int headerSize;

    // Number of committed transactions since storage was opened
    // This field is not persisted
    private final int version;

    EntityNodeStorageMetadata(int serializedEntitySize, int headerSize, int version) {
        this.serializedEntitySize = serializedEntitySize;
        this.headerSize = headerSize;
        this.version = version;
    }

    public EntityNodeStorageMetadata clone() {
        EntityNodeStorageMetadata clone = new EntityNodeStorageMetadata(serializedEntitySize, headerSize, version + 1);
        clone.rootEntityId = this.rootEntityId;
        clone.numEntities = this.numEntities;
        clone.firstDeletedEntityId = this.firstDeletedEntityId;
        clone.capacity = this.capacity;
        return clone;
    }
}
