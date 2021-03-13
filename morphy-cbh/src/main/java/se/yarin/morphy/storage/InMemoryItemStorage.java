package se.yarin.morphy.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.exceptions.MorphyIOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * An in-memory version of an {@link ItemStorage}.
 * @param <THeader>
 * @param <TItem>
 */
public class InMemoryItemStorage<THeader, TItem> implements ItemStorage<THeader, TItem> {
    private @NotNull final List<TItem> items;
    private @Nullable final TItem emptyItem;
    private @NotNull THeader header;
    private final boolean strict;
    private final boolean readOnly;

    public InMemoryItemStorage(@NotNull THeader header) {
        this(header, OpenOption.RW(), null);
    }

    public InMemoryItemStorage(@NotNull THeader header, Set<OpenOption> options) {
        this(header, options, null);
    }

    public InMemoryItemStorage(@NotNull THeader header, Set<OpenOption> options, @Nullable TItem emptyItem) {
        this.items = new ArrayList<>();
        this.header = header;
        this.emptyItem = emptyItem;
        this.strict = options.contains(OpenOption.STRICT);
        this.readOnly = !options.contains(OpenOption.WRITE);
        if (!strict && emptyItem == null) {
            // This is required since otherwise we don't know what to return when trying to get an illegal id!
            // (for the FileItemStorage this is not necessary, there we just assume to read zeros)
            throw new IllegalArgumentException("A valid emptyItem must be provided when an ItemStorage is used in non-strict mode");
        }
    }

    @Override
    public @NotNull THeader getHeader() {
        return this.header;
    }

    @Override
    public void putHeader(@NotNull THeader header) {
        if (this.readOnly) {
            throw new IllegalStateException("Storage is read-only");
        }
        this.header = header;
    }

    @Override
    public @NotNull TItem getItem(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
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
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        if (index + count - 1 >= items.size()) {
            // Some items that we want to get is outside the range
            if (strict) {
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
        if (this.readOnly) {
            throw new IllegalStateException("Storage is read-only");
        }
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        if (index > items.size()) {
            if (strict) {
                throw new IllegalArgumentException(String.format("Tried to put item with id %d but InMemoryStorage only has %d items",
                        index, this.items.size()));
            }
            while (index > items.size()) {
                items.add(emptyItem);
            }
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
