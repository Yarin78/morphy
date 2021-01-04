package se.yarin.cbhlib.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import se.yarin.cbhlib.entities.Entity;

@AllArgsConstructor
class EntityNodeImpl<T extends Entity & Comparable<T>> implements EntityNode<T> {
    @Getter
    private int entityId;
    private T entity;
    @Getter
    private int leftEntityId;
    @Getter
    private int rightEntityId;
    @Getter
    private int heightDif;

    // This version will only be used by the in-memory node storage
    public T getEntity() {
        return entity == null ? null : entity.withNewId(entityId);
    }

    public boolean isDeleted() {
        return this.leftEntityId == -999;
    }

    public EntityNode<T> update(int newLeftEntityId, int newRightEntityId, int newHeightDif) {
        return new EntityNodeImpl<>(entityId, entity, newLeftEntityId, newRightEntityId, newHeightDif);
    }

    @Override
    public String toString() {
        return "EntityNode{" +
                "entityId=" + entityId +
                ", leftEntityId=" + leftEntityId +
                ", rightEntityId=" + rightEntityId +
                ", heightDif=" + heightDif +
                '}';
    }
}
