package se.yarin.morphy.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseContext;
import se.yarin.morphy.exceptions.MorphyIOException;
import se.yarin.morphy.metrics.ItemMetrics;
import se.yarin.morphy.metrics.MetricsRef;

import java.util.ArrayList;
import java.util.List;

/**
 * An in-memory version of an {@link ItemStorage}.
 * @param <THeader>
 * @param <TItem>
 */
public class InMemoryItemStorage<THeader, TItem> implements ItemStorage<THeader, TItem> {
    private @NotNull final List<TItem> items;
    // If an empty item is provided, we are in "non-strict" mode
    private @Nullable final TItem emptyItem;
    private final boolean oneIndexed; // true if first id is 1, false if 0
    private final DatabaseContext context;
    private final String storageName;
    private final MetricsRef<ItemMetrics> itemMetricsRef;
    private @NotNull THeader header;

    /**
     * Creates a new empty zero-indexed in-memory storage
     * @param header a header representing an empty storage
     */
    public InMemoryItemStorage(@NotNull THeader header) {
        this(header, null);
    }

    /**
     * Creates a new empty zero-indexed in-memory storage
     * @param header a header representing an empty storage
     */
    public InMemoryItemStorage(@Nullable DatabaseContext context, @Nullable String storageName, @NotNull THeader header) {
        this(context, storageName, header, null);
    }

    /**
     * Creates a new empty zero-indexed in-memory storage
     * @param header a header representing an empty storage
     * @param emptyItem the item to be returned when trying to read outside the index boundaries of the storage;
     *                  if null, the storage is opened in strict mode
     */
    public InMemoryItemStorage(@NotNull THeader header, @Nullable TItem emptyItem) {
        this(null, null, header, emptyItem, false);
    }

    /**
     * Creates a new empty zero-indexed in-memory storage
     * @param header a header representing an empty storage
     * @param emptyItem the item to be returned when trying to read outside the index boundaries of the storage;
     *                  if null, the storage is opened in strict mode
     */
    public InMemoryItemStorage(
            @Nullable DatabaseContext context,
            @Nullable String storageName,
            @NotNull THeader header,
            @Nullable TItem emptyItem) {
        this(context, storageName, header, emptyItem, false);
    }

    /**
     * Creates a new empty in-memory storage
     * @param header a header representing an empty storage
     * @param emptyItem the item to be returned when trying to read outside the index boundaries of the storage;
     *                  if null, the storage is opened in strict mode
     * @param oneIndexed if true, the first item in the storage has index 1; otherwise it has index 0
     */
    public InMemoryItemStorage(
            @Nullable DatabaseContext context,
            @Nullable String storageName,
            @NotNull THeader header,
            @Nullable TItem emptyItem,
            boolean oneIndexed) {
        this.items = new ArrayList<>();
        this.context = context == null ? new DatabaseContext() : context;
        this.storageName = storageName == null ? header.getClass().getSimpleName() : storageName;
        this.header = header;
        this.emptyItem = emptyItem;
        this.oneIndexed = oneIndexed;
        this.itemMetricsRef = ItemMetrics.register(this.context.instrumentation(), this.storageName);
    }

    @Override
    public @NotNull THeader getHeader() {
        return this.header;
    }

    @Override
    public void putHeader(@NotNull THeader header) {
        this.header = header;
    }

    @Override
    public boolean isEmpty() {
        return this.items.size() == 0;
    }

    @Override
    public int count() {
        return this.items.size();
    }

    @Override
    public @NotNull TItem getItem(int index) {
        if (oneIndexed) {
            index--;
        }

        if (index < 0 || index >= items.size()) {
            if (emptyItem == null) {
                throw new IllegalArgumentException(String.format("Tried to get item at index %d but InMemoryStorage has %d items",
                        index + (oneIndexed ? 1 : 0), this.items.size()));
            }
            return emptyItem;
        }
        itemMetricsRef.update(metrics -> metrics.addGet(1));
        return items.get(index);
    }

    @Override
    public @NotNull List<TItem> getItems(int index, int count) {
        return getItems(index, count, null);
    }

    @Override
    public @NotNull List<TItem> getItems(int index, int count, @Nullable ItemStorageFilter<TItem> filter) {
        if (count < 0 ) {
            throw new IllegalArgumentException("count must be non-negative");
        }

        if (oneIndexed) {
            index--;
        }

        if (index < 0 || index + count - 1 >= items.size() || filter != null) {
            // Some items that we want to get is outside the range or we have a filter
            ArrayList<TItem> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int j = index + i;
                TItem item;
                if (j < 0 || j >= items.size()) {
                    if (emptyItem == null) {
                        throw new IllegalArgumentException(String.format("Tried to get item with id %d but InMemoryStorage only has %d items",
                                j + (oneIndexed ? 1 : 0), this.items.size()));
                    }
                    item = emptyItem;
                } else {
                    item = items.get(j);
                }
                result.add(filter == null || filter.matches(item) ? item : null);
            }
            return result;
        }
        itemMetricsRef.update(metrics -> metrics.addGet(1));
        // Fast unfiltered version; just return a sublist
        return new ArrayList<>(items.subList(index, index + count));
    }

    @Override
    public void putItem(int index, @NotNull TItem item) {
        if (oneIndexed) {
            index--;
        }

        if (index < 0) {
            throw new IllegalArgumentException("Invalid index");
        }
        if (index > items.size()) {
            throw new IllegalArgumentException(String.format("Tried to put item with id %d but InMemoryStorage only has %d items",
                    index + (oneIndexed ? 1 : 0), this.items.size()));
        }
        itemMetricsRef.update(metrics -> metrics.addPut(1));
        if (index == items.size()) {
            items.add(item);
        } else {
            items.set(index, item);
        }
    }

    @Override
    public void close() throws MorphyIOException {
    }
}
