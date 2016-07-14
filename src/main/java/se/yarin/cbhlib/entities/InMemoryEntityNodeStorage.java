package se.yarin.cbhlib.entities;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class InMemoryEntityNodeStorage<T extends Entity & Comparable<T>> extends EntityNodeStorageBase<T> {
    private static final Logger log = LoggerFactory.getLogger(InMemoryEntityNodeStorage.class);

    private TreeMap<Integer, EntityNode<T>> nodes = new TreeMap<>();

    @Override
    protected EntityNode<T> getEntityNode(int entityId) throws IOException {
        EntityNode<T> entityNode = nodes.get(entityId);
        if (log.isTraceEnabled()) {
            log.trace("Read entity node: " + entityNode);
        }
        return entityNode;
    }

    @Override
    protected List<EntityNode<T>> getEntityNodes(int startIdInclusive, int endIdExclusive)
            throws IOException {
        return nodes.subMap(startIdInclusive, true, endIdExclusive, false)
                .values()
                .stream()
                .collect(Collectors.toList());
    }

    @Override
    protected void putEntityNode(@NonNull EntityNode<T> node) throws IOException {
        // TODO: Fix this!!!
        assert node.isDeleted() || node.getEntity().getId() == node.getEntityId()
            : "Entity id of node must match entity id";
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

    @Override
    public void putMetadata(EntityNodeStorageMetadata metadata) throws IOException {
        // Nothing to do
    }
}
