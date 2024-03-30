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
        return iterableAscending(null, null, null);
    }

    /**
     * Gets an iterable of all entities matching an optional filter in the index sorted by the default sorting order.
     * @param filter an optional filter
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableAscending(@Nullable EntityFilter<T> filter) {
        return iterableAscending(null, null, filter);
    }

    /**
     * Gets an iterable of all entities in the index starting at a given key (inclusive),
     * ending at a given key (exclusive), sorted by the default sorting order.
     * @param start the starting key
     * @param end the end key
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableAscending(@Nullable T start, @Nullable T end) {
        return iterableAscending(start, end, null);
    }

    /**
     * Gets an iterable of all entities matching an optional filter in the index starting at a given key (inclusive),
     * ending at a given key (exclusive), sorted by the default sorting order.
     * @param start an optional starting key
     * @param end an optional end key
     * @param filter an optional filter
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableAscending(@Nullable T start, @Nullable T end, @Nullable EntityFilter<T> filter) {
        int stopId = -1;
        if (end != null) {
            if (start != null && end.compareTo(start) < 0) {
                end = start;
            }
            EntityIndexWriteTransaction<T>.NodePath endPath = lowerBound(end);
            if (!endPath.isEnd()) {
                stopId = endPath.getEntityId();
            }
        }
        final int finalStopId = stopId;
        return () -> new OrderedEntityAscendingIterator<>(start == null ? begin() : lowerBound(start), finalStopId, filter);
    }

    /**
     * Gets an iterable of all entities in the index in reverse default sorting order.
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableDescending() {
        return iterableDescending(null, null, null);
    }

    /**
     * Gets an iterable of all entities in the index in reverse default sorting order.
     * @param filter an optional filter
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableDescending(@Nullable EntityFilter<T> filter) {
        return iterableDescending(null, null, filter);
    }

    /**
     * Gets an iterable of all entities in the index starting at the given key (inclusive),
     * ending at a given key (exclusive) in reverse default sorting order.
     * @param start the starting key
     * @param end the end key
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableDescending(@Nullable T start, @Nullable T end) {
        return iterableDescending(start, end, null);
    }

    /**
     * Gets an iterable of all entities matching an optional filter in the index starting at the given key (inclusive),
     * ending at a given key (exclusive), sorted in the reverse default sorting order.
     * @param start the starting key
     * @param end the end key
     * @param filter an optional filter
     * @return an iterable of entities
     */
    public @NotNull Iterable<T> iterableDescending(@Nullable T start, @Nullable T end, @Nullable EntityFilter<T> filter) {
        int stopId = -2; // -1 is the fictional end node, so we can't stop there
        if (end != null) {
            if (start != null && end.compareTo(start) > 0) {
                end = start;
            }
            EntityIndexWriteTransaction<T>.NodePath endPath = upperBound(end);
            if (!endPath.isBegin()) {
                stopId = endPath.getEntityId();
            }
        }
        final int finalStopId = stopId;

        return () -> new OrderedEntityDescendingIterator<>(start == null ? end() : upperBound(start), finalStopId, filter);
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
     * ending at the given key (exclusive), sorted by the default sorting order.
     * @param start the starting key
     * @param end the end key
     * @return a stream of entities
     */
    public @NotNull Stream<T> streamOrderedAscending(@Nullable T start, @Nullable T end) {
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
    public @NotNull Stream<T> streamOrderedAscending(@Nullable T start, @Nullable T end, @Nullable EntityFilter<T> filter) {
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
     * ending at the given key (exclusive), in reverse default sorting order.
     * @param start the starting key
     * @param end the end key
     * @return a stream of entities
     */
    public @NotNull Stream<T> streamOrderedDescending(@Nullable T start, @Nullable T end) {
        return StreamSupport.stream(iterableDescending(start, end).spliterator(), false);
    }

    /**
     * Gets a stream of all entities matching an optional filter in the index starting at the given key (inclusive),
     * ending at the given key (exclusive), in reverse default sorting order.
     * @param start the starting key
     * @param end the end key
     * @param filter an optional filter
     * @return a stream of entities
     */
    public @NotNull Stream<T> streamOrderedDescending(@Nullable T start, @Nullable T end, @Nullable EntityFilter<T> filter) {
        return StreamSupport.stream(iterableDescending(start, end, filter).spliterator(), false);
    }

}
