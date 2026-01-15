package se.yarin.morphy.entities;

import org.immutables.value.Value;

@Value.Immutable
public abstract class EntityIndexHeader {
  public static final int MAGIC_CONSTANT = 1234567890;

  public abstract int capacity();

  public abstract int rootNodeId();

  public abstract int entitySize();

  public abstract int deletedEntityId();

  public abstract int numEntities();

  public abstract int headerSize();

  public static EntityIndexHeader empty(int entitySize) {
    return ImmutableEntityIndexHeader.builder()
        .capacity(0)
        .rootNodeId(-1)
        .entitySize(entitySize)
        .deletedEntityId(-1)
        .numEntities(0)
        .headerSize(32)
        .build();
  }
}
