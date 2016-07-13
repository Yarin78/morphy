package se.yarin.cbhlib.entities;

import lombok.Data;

@Data
public class EntityNodeStorageMetadata {
    // The serialized size could be different if opening an existing database,
    // so it must be possible to change it after construction time
    private final int serializedEntitySize;
    private int rootEntityId = -1;
    private int numEntities = 0;
    private int firstDeletedEntityId = -1;
    private int capacity = 0;
    private final int entityOffset;

    public EntityNodeStorageMetadata(int serializedEntitySize, int entityOffset) {
        this.serializedEntitySize = serializedEntitySize;
        this.entityOffset = entityOffset;
    }
}
