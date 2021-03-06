package se.yarin.morphy.entities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.exceptions.MorphyIOException;
import se.yarin.morphy.storage.ItemStorage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public abstract class EntityIndex<T extends Entity & Comparable<T>>  {
    private static final Logger log = LoggerFactory.getLogger(EntityIndex.class);

    protected final ItemStorage<EntityIndexHeader, EntityNode> storage;

    EntityIndexHeader storageHeader() {
        return storage.getHeader();
    }

    protected EntityIndex(ItemStorage<EntityIndexHeader, EntityNode> storage) {
        this.storage = storage;
    }

    /**
     * Returns the number of entities in the index.
     * @return number of entities
     */
    public int count() {
        return storage.getHeader().numEntities();
    }

    EntityNode getNode(int id) {
        return storage.getItem(id);
    }

    protected T resolveEntity(EntityNode node) {
        return deserialize(node.getId(), node.getGameCount(), node.getFirstGameId(), node.getSerializedEntity());
    }

    /**
     * Gets an entity by id.
     * If the id is invalid, either an empty (non-null) item is returned or
     * {@link MorphyIOException} is thrown, depending on the OpenOption of the index.
     * @param id the id of the entity
     * @return the entity
     */
    public T get(int id) {
        return resolveEntity(getNode(id));
    }

    /**
     * Gets an entity by key. If there are multiple entities matching, returns one of them.
     * @param entityKey the key of the entity
     * @return the entity, or null if there was no entity with that key
     */
    public T get(T entityKey) {
        NodePath<T> treePath = lowerBound(entityKey);
        if (treePath.isEnd()) {
            return null;
        }
        T foundEntity = treePath.getEntity();
        if (foundEntity.compareTo(entityKey) == 0) {
            return foundEntity;
        }
        return null;
    }

    /**
     * Gets a list of all entities in the database
     * @return a list of all entities
     */
    public List<T> getAll() {
        ArrayList<T> result = new ArrayList<>();
        iterable().forEach(result::add);
        return result;
    }

    /**
     * Gets the first entity in the index according to the default sort order
     * @return the first entity, or null if there are no entities in the database
     */
    public T getFirst() {
        Iterator<T> iterator = iterableAscending().iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }

    /**
     * Gets the last entity in the index according to the default sort order
     * @return the last entity, or null if there are no entities in the database
     */
    public T getLast() {
        Iterator<T> iterator = iterableDescending().iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }

    /**
     * Gets the entity after the given one in the index according to the default sort order.
     * The given entity doesn't have to exist, it can be a search key as well.
     * @param entity an entity or an entity key
     * @return the succeeding entity, or null if there are no succeeding entities
     */
    public T getNext(T entity) {
        for (T e : iterableAscending(entity)) {
            if (!e.equals(entity)) {
                return e;
            }
        }
        return null;
    }

    /**
     * Gets the entity before the given one in the index according to the default sort order.
     * The given entity doesn't have to exist, it can be a search key as well.
     * @param entity an entity or an entity key
     * @return the preceding entity, or null if there are no preceding entities
     */
    public T getPrevious(T entity) {
        for (T e : iterableDescending(entity)) {
            if (!e.equals(entity)) {
                return e;
            }
        }
        return null;
    }

    /**
     * Returns an iterable of all entities in the index, sorted by id.
     * @return an iterable of all entities
     */
    public Iterable<T> iterable() {
        return iterable(0);
    }

    /**
     * Returns an iterable of all entities in the index, sorted by id.
     * @param startId the first id in the iterable
     * @return an iterable of all entities
     */
    public Iterable<T> iterable(int startId) {
        return () -> new EntityBatchIterator<>(this, startId);
    }

    /**
     * Gets an iterable of all entities in the index sorted by the default sorting order.
     * @return an iterable of entities
     */
    public Iterable<T> iterableAscending() {
        return () -> new OrderedEntityAscendingIterator<>(NodePath.begin(this), -1);
    }

    /**
     * Gets an iterable of all entities in the index starting at the given key (inclusive),
     * sorted by the default sorting order.
     * @param start the starting key
     * @return an iterable of entities
     */
    public Iterable<T> iterableAscending(T start) {
        return () -> new OrderedEntityAscendingIterator<>(lowerBound(start), -1);
    }

    /**
     * Gets an iterable of all entities in the index in reverse default sorting order.
     * @return an iterable of entities
     */
    public Iterable<T> iterableDescending() {
        return () -> new OrderedEntityDescendingIterator<>(NodePath.end(this));
    }

    /**
     * Gets an iterable of all entities in the index starting at the given key (inclusive),
     * in reverse default sorting order.
     * @param start the starting key
     * @return an iterable of entities
     */
    public Iterable<T> iterableDescending(T start) {
        return () -> new OrderedEntityDescendingIterator<>(lowerBound(start));
    }

    /**
     * Returns a stream of all entities in the index, sorted by id.
     * @return a stream of all entities
     */
    public Stream<T> stream() {
        return StreamSupport.stream(iterable().spliterator(), false);
    }

    /**
     * Returns a stream of all entities in the index, sorted by id.
     * @param startId the first id in the stream
     * @return a stream of all entities
     */
    public Stream<T> stream(int startId) {
        return StreamSupport.stream(iterable(startId).spliterator(), false);
    }

    /**
     * Gets a stream of all entities in the index, sorted by the default sorting order.
     * @return a stream of all entities
     */
    public Stream<T> streamOrderedAscending() {
        return StreamSupport.stream(iterableAscending().spliterator(), false);
    }

    /**
     * Gets a stream of all entities in the index starting at the given key (inclusive),
     * sorted by the default sorting order.
     * @param start the starting key
     * @return a stream of entities
     */
    public Stream<T> streamOrderedAscending(T start) {
        return StreamSupport.stream(iterableAscending(start).spliterator(), false);
    }

    /**
     * Gets a stream of all entities in the index in reverse default sorting order.
     * @return a stream of all entities
     */
    public Stream<T> streamOrderedDescending() {
        return StreamSupport.stream(iterableDescending().spliterator(), false);
    }

    /**
     * Gets a stream of all entities in the index starting at the given key (inclusive),
     * in reverse default sorting order.
     * @param start the starting key
     * @return a stream of entities
     */
    public Stream<T> streamOrderedDescending(T start) {
        return StreamSupport.stream(iterableDescending(start).spliterator(), false);
    }

    /**
     * Returns a NodePath to the first node which does not compare less than entity, or NodePath.end if no such node exists.
     * If nodes exists with that compares equally to entity, the first of those nodes will be returned.
     */
    public NodePath<T> lowerBound(T entity) {
        return lowerBound(entity, storageHeader().rootNodeId(), null);
    }

    private NodePath<T> lowerBound(T entity, int currentId, NodePath<T> path) {
        if (currentId < 0) {
            return NodePath.end(this);
        }

        EntityNode node = getNode(currentId);
        T current = resolveEntity(node);
        // IMPROVEMENT: Would be nice to be able to compare entities without having to deserialize them
        int comp = entity.compareTo(current);

        path = new NodePath<>(this, currentId, path);
        if (comp <= 0) {
            NodePath<T> left = lowerBound(entity, node.getLeftChildId(), path);
            return left.isEnd() ? path : left;
        } else {
            return lowerBound(entity, node.getRightChildId(), path);
        }
    }

    /**
     * Returns a NodePath to the first node which compares greater than entity, or NodePath.end if no such node exists.
     */
    public NodePath<T> upperBound(T entity) {
        return upperBound(entity, storageHeader().rootNodeId(), null);
    }

    private NodePath<T> upperBound(T entity, int currentId, NodePath<T> path) {
        if (currentId < 0) {
            return NodePath.end(this);
        }

        EntityNode node = getNode(currentId);
        T current = resolveEntity(node);
        int comp = entity.compareTo(current);

        path = new NodePath<>(this, currentId, path);
        if (comp < 0) {
            NodePath<T> left = upperBound(entity, node.getLeftChildId(), path);
            return left.isEnd() ? path : left;
        } else {
            return upperBound(entity, node.getRightChildId(), path);
        }
    }

    protected abstract T deserialize(int entityId, int count, int firstGameId, byte[] serializedData);

    protected abstract void serialize(T entity, ByteBuffer buf);


    public int addEntity(T entity) {
        /*
        EntityIndexTransaction txn = new EntityIndexTransaction(this);
        txn.addEntity(entity);
        txn.commit();
        EntityNode node = new EntityNode(0, 0, 0, 0, 0, serialize(entity));
        int newId = 0;
        entityStorage.putItem(newId, node);
        return newId;

         */
        return 0;
    }

    public void close() throws MorphyIOException {
        storage.close();
    }

}
