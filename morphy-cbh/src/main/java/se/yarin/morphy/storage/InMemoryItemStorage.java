package se.yarin.morphy.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.exceptions.MorphyIOException;

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
    private @NotNull THeader header;

    public InMemoryItemStorage(@NotNull THeader header) {
        this(header, null);
    }

    public InMemoryItemStorage(@NotNull THeader header, @Nullable TItem emptyItem) {
        this(header, emptyItem, false);
    }

    public InMemoryItemStorage(@NotNull THeader header, @Nullable TItem emptyItem, boolean oneIndexed) {
        this.items = new ArrayList<>();
        this.header = header;
        this.emptyItem = emptyItem;
        this.oneIndexed = oneIndexed;
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
    public @NotNull TItem getItem(int index) {
        if (oneIndexed) {
            index--;
        }

        if (index < 0) {
            throw new IllegalArgumentException("Invalid index");
        }
        if (index >= items.size()) {
            if (emptyItem == null) {
                throw new IllegalArgumentException(String.format("Tried to get item with id %d but InMemoryStorage only has %d items",
                        index, this.items.size()));
            }
            return emptyItem;
        }
        return items.get(index);
    }

    @Override
    public List<TItem> getItems(int index, int count) {
        if (oneIndexed) {
            index--;
        }

        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        if (index + count - 1 >= items.size()) {
            // Some items that we want to get is outside the range
            if (emptyItem == null) {
                throw new IllegalArgumentException(String.format("Tried to get item with id %d but InMemoryStorage only has %d items",
                        index + count - 1, this.items.size()));
            }
            ArrayList<TItem> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                result.add(index + i < items.size() ? items.get(index + i) : emptyItem);
            }
        }
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
