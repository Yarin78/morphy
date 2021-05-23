package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.DatabaseContext;
import se.yarin.morphy.entities.filters.EntityFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class EntityIndexReadTransaction<T extends Entity & Comparable<T>> extends EntityIndexTransaction<T> {
    private static final Logger log = LoggerFactory.getLogger(EntityIndexReadTransaction.class);

    // The version of the database the transaction starts from
    private final int version;

    public EntityIndexReadTransaction(@NotNull EntityIndex<T> index) {
        super(DatabaseContext.DatabaseLock.READ, index);

        this.version = index.currentVersion();
    }

    @Override
    protected int version() {
        return version;
    }

    /**
     * Gets a list of all node ids that are marked as deleted and can be reused.
     * For internal use only.
     * @return a list of deleted entity ids.
     */
    public @NotNull List<Integer> getDeletedEntityIds() {
        ArrayList<Integer> ids = new ArrayList<>();
        int id = header().deletedEntityId();
        while (id >= 0 && !ids.contains(id)) {
            ids.add(id);
            // Get the data directly from storage since we can't deserialize deleted items
            id = index().storage.getItem(id).getRightChildId();
        }
        if (id >= 0) {
            log.warn(String.format("Loop in deleted id chain in entity %s", index().entityType().toLowerCase()));
        }
        return ids;
    }

    /**
     * Returns an iterable of all entities in the index, sorted by id.
     * @return an iterable of all entities
     */
    public @NotNull Iterable<T> iterable() {
        return iterable(0);
    }

    /**
     * Returns an iterable of all entities in the index, sorted by id.
     * @param startId the first id in the iterable
     * @return an iterable of all entities
     */
    public @NotNull Iterable<T> iterable(int startId) {
        return iterable(startId, null);
    }

    /**
     * Returns an iterable of all entities in the index matching an optional filter, sorted by id.
     * @param filter an entity filter
     * @return an iterable of all entities
     */
    public @NotNull Iterable<T> iterable(@Nullable EntityFilter<T> filter) {
        return iterable(0, filter);
    }

    /**
     * Returns an iterable of all entities in the index matching an optional filter, sorted by id.
     * @param startId the first id in the iterable
     * @param filter an entity filter
     * @return an iterable of all entities
     */
    public @NotNull Iterable<T> iterable(int startId, @Nullable EntityFilter<T> filter) {
        return () -> new EntityBatchIterator<>(this, startId, filter);
    }


    /**
     * Returns a stream of all entities in the index, sorted by id.
     * @return a stream of all entities
     */
    public @NotNull Stream<T> stream() {
        return StreamSupport.stream(iterable().spliterator(), false);
    }

    /**
     * Returns a stream of all entities in the index, sorted by id.
     * @param startId the first id in the stream
     * @return a stream of all entities
     */
    public @NotNull Stream<T> stream(int startId) {
        return StreamSupport.stream(iterable(startId).spliterator(), false);
    }

    /**
     * Returns a stream of all entities in the index matching an optional filter, sorted by id.
     * @param filter an entity filter
     * @return a stream of all entities
     */
    public @NotNull Stream<T> stream(@Nullable EntityFilter<T> filter) {
        return StreamSupport.stream(iterable(filter).spliterator(), false);
    }

    /**
     * Returns a stream of all entities in the index matching an optional filter, sorted by id.
     * @param startId the first id in the stream
     * @param filter an entity filter
     * @return a stream of all entities
     */
    public @NotNull Stream<T> stream(int startId, @Nullable EntityFilter<T> filter) {
        return StreamSupport.stream(iterable(startId, filter).spliterator(), false);
    }

    /**
     * Gets an iterable of all entities in the index sorted by the default sorting order.
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableAscending() {
        return iterableAscending((EntityFilter<T>) null);
    }

    /**
     * Gets an iterable of all entities matching an optional filter in the index sorted by the default sorting order.
     * @param filter an optional filter
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableAscending(@Nullable EntityFilter<T> filter) {
        return () -> new OrderedEntityAscendingIterator<>(begin(), -1, filter);
    }

    /**
     * Gets an iterable of all entities in the index starting at the given key (inclusive),
     * sorted by the default sorting order.
     * @param start the starting key
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableAscending(@NotNull T start) {
        return iterableAscending(start, (EntityFilter<T>) null);
    }

    /**
     * Gets an iterable of all entities in the index starting at the given key (inclusive),
     * sorted by the default sorting order.
     * @param start the starting key
     * @param filter an optional filter
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableAscending(@NotNull T start, @Nullable EntityFilter<T> filter) {
        return () -> new OrderedEntityAscendingIterator<>(lowerBound(start), -1, filter);
    }

    /**
     * Gets an iterable of all entities in the index starting at a given key (inclusive),
     * ending at a given key (exclusive), sorted by the default sorting order.
     * @param start the starting key
     * @param end the end key
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableAscending(@NotNull T start, @NotNull T end) {
        return iterableAscending(start, end, null);
    }

    /**
     * Gets an iterable of all entities matching an optional filter in the index starting at a given key (inclusive),
     * ending at a given key (exclusive), sorted by the default sorting order.
     * @param start the starting key
     * @param end the end key
     * @param filter an optional filter
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableAscending(@NotNull T start, @NotNull T end, @Nullable EntityFilter<T> filter) {
        EntityIndexWriteTransaction<T>.NodePath endPath = lowerBound(end);
        return () -> new OrderedEntityAscendingIterator<>(lowerBound(start), endPath.isEnd() ? -1 : endPath.getEntityId(), filter);
    }

    /**
     * Gets an iterable of all entities in the index in reverse default sorting order.
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableDescending() {
        return iterableDescending((EntityFilter<T>) null);
    }

    /**
     * Gets an iterable of all entities in the index in reverse default sorting order.
     * @param filter an optional filter
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableDescending(@Nullable EntityFilter<T> filter) {
        return () -> new OrderedEntityDescendingIterator<>(end(), filter);
    }

    /**
     * Gets an iterable of all entities in the index starting at the given key (inclusive),
     * in reverse default sorting order.
     * @param start the starting key
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableDescending(@NotNull T start) {
        return iterableDescending(start, null);
    }

    /**
     * Gets an iterable of all entities matching an optional filter in the index starting at the given key (inclusive),
     * in reverse default sorting order.
     * @param start the starting key
     * @param filter an optional filter
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableDescending(@NotNull T start, @Nullable EntityFilter<T> filter) {
        return () -> new OrderedEntityDescendingIterator<>(upperBound(start), filter);
    }

    /**
     * Gets a stream of all entities in the index, sorted by the default sorting order.
     * @return a stream of all entities
     */
    public @NotNull Stream<T> streamOrderedAscending() {
        return StreamSupport.stream(iterableAscending().spliterator(), false);
    }

    /**
     * Gets a stream of all entities in the index matching an optional filter, sorted by the default sorting order.
     * @param filter an optional filter
     * @return a stream of all entities
     */
    public @NotNull Stream<T> streamOrderedAscending(@Nullable EntityFilter<T> filter) {
        return StreamSupport.stream(iterableAscending(filter).spliterator(), false);
    }

    /**
     * Gets a stream of all entities in the index starting at the given key (inclusive),
     * sorted by the default sorting order.
     * @param start the starting key
     * @return a stream of entities
     */
    public @NotNull Stream<T> streamOrderedAscending(@NotNull T start) {
        return StreamSupport.stream(iterableAscending(start).spliterator(), false);
    }

    /**
     * Gets a stream of all entities matching an optional filter in the index starting at the given key (inclusive),
     * sorted by the default sorting order.
     * @param start the starting key
     * @param filter an optional filter
     * @return a stream of entities
     */
    public @NotNull Stream<T> streamOrderedAscending(@NotNull T start, @Nullable EntityFilter<T> filter) {
        return StreamSupport.stream(iterableAscending(start, filter).spliterator(), false);
    }

    /**
     * Gets a stream of all entities in the index starting at a given key (inclusive),
     * and ending at a given key (exclusive), sorted by the default sorting order.
     * @param start the starting key
     * @param end the end key
     * @return a stream of entities
     */
    public @NotNull Stream<T> streamOrderedAscending(@NotNull T start, @NotNull T end) {
        return StreamSupport.stream(iterableAscending(start, end).spliterator(), false);
    }

    /**
     * Gets a stream of all entities matching an optional filter in the index starting at a given key (inclusive),
     * and ending at a given key (exclusive), sorted by the default sorting order.
     * @param start the starting key
     * @param end the end key
     * @param filter an optional filter
     * @return a stream of entities
     */
    public @NotNull Stream<T> streamOrderedAscending(@NotNull T start, @NotNull T end, @Nullable EntityFilter<T> filter) {
        return StreamSupport.stream(iterableAscending(start, end, filter).spliterator(), false);
    }

    /**
     * Gets a stream of all entities in the index in reverse default sorting order.
     * @return a stream of all entities
     */
    public @NotNull Stream<T> streamOrderedDescending() {
        return StreamSupport.stream(iterableDescending().spliterator(), false);
    }

    /**
     * Gets a stream of all entities matching an optional filter in the index in reverse default sorting order.
     * @param filter an optional filter
     * @return a stream of all entities
     */
    public @NotNull Stream<T> streamOrderedDescending(@Nullable EntityFilter<T> filter) {
        return StreamSupport.stream(iterableDescending(filter).spliterator(), false);
    }

    /**
     * Gets a stream of all entities in the index starting at the given key (inclusive),
     * in reverse default sorting order.
     * @param start the starting key
     * @return a stream of entities
     */
    public @NotNull Stream<T> streamOrderedDescending(@NotNull T start) {
        return StreamSupport.stream(iterableDescending(start).spliterator(), false);
    }

    /**
     * Gets a stream of all entities matching an optional filter in the index starting at the given key (inclusive),
     * in reverse default sorting order.
     * @param start the starting key
     * @param filter an optional filter
     * @return a stream of entities
     */
    public @NotNull Stream<T> streamOrderedDescending(@NotNull T start, @Nullable EntityFilter<T> filter) {
        return StreamSupport.stream(iterableDescending(start, filter).spliterator(), false);
    }

}
