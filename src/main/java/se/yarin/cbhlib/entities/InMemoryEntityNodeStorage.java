package se.yarin.cbhlib.entities;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class InMemoryEntityNodeStorage<T extends Entity> extends EntityNodeStorageBase {
    private static final Logger log = LoggerFactory.getLogger(InMemoryEntityNodeStorage.class);

    private TreeMap<Integer, EntityNode> nodes = new TreeMap<>();

    public InMemoryEntityNodeStorage(@NonNull String storageName, @NonNull EntitySerializer serializer) {
        super(storageName, serializer);

        setCapacity(0);
        setRootEntityId(-1);
        setNumEntities(0);
        setFirstDeletedEntityId(-1);
    }


    @Override
    protected EntityNode getEntityNode(int entityId) throws IOException {
        EntityNode entityNode = nodes.get(entityId);
        if (log.isTraceEnabled()) {
            log.trace("Read entity node: " + entityNode);
        }
        return entityNode;
    }

    @Override
    protected List<EntityNode> getEntityNodes(int startIdInclusive, int endIdExclusive) throws IOException {
        return nodes.subMap(startIdInclusive, true, endIdExclusive, false)
                .values()
                .stream()
                .filter(entityNode -> entityNode != null)
                .collect(Collectors.toList());
    }

    @Override
    protected void putEntityNode(@NonNull EntityNode node) throws IOException {
        nodes.put(node.getEntityId(), node);
        if (log.isDebugEnabled()) {
            log.debug(String.format("Put entity node: " + node));
        }
    }

    @Override
    public void close() throws IOException { }

    @Override
    public void updateStorageHeader() throws IOException {

    }
}
