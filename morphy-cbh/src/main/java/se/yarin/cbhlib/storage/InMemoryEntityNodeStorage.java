package se.yarin.cbhlib.storage;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.entities.Entity;

import java.io.IOException;
import java.util.*;

public class InMemoryEntityNodeStorage<T extends Entity & Comparable<T>> extends EntityNodeStorageBase<T> {
    private static final Logger log = LoggerFactory.getLogger(InMemoryEntityNodeStorage.class);

    private final TreeMap<Integer, EntityNode<T>> nodes = new TreeMap<>();

    public InMemoryEntityNodeStorage() {
        this(new EntityNodeStorageMetadata(0, 0, 0));
    }

    public InMemoryEntityNodeStorage(@NonNull EntityNodeStorageMetadata metadata) {
        super(metadata);
    }

    @Override
    public EntityNode<T> getEntityNode(int entityId) {
        EntityNode<T> entityNode = nodes.get(entityId);
        if (log.isTraceEnabled()) {
            log.trace("Read entity node: " + entityNode);
        }
        return entityNode;
    }

    public Collection<EntityNode<T>> getAllEntityNodes() {
        return nodes.values();
    }

    @Override
    public List<EntityNode<T>> getEntityNodes(int startIdInclusive, int endIdExclusive) {
        return new ArrayList<>(nodes.subMap(startIdInclusive, true, endIdExclusive, false)
                .values());
    }

    @Override
    public void putEntityNode(@NonNull EntityNode<T> node) {
        nodes.put(node.getEntityId(), node);
        if (log.isDebugEnabled()) {
            log.debug(String.format("Put entity node: %s", node));
        }
    }

    @Override
    public EntityNode<T> createNode(int entityId, T entity) {
        return new EntityNodeImpl<>(entityId, entity, -1, -1, 0);
    }

    @Override
    public void close() throws IOException { }
}
