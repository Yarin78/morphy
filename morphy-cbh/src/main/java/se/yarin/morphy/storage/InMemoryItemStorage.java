package se.yarin.morphy.storage;

import se.yarin.morphy.exceptions.MorphyIOException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class InMemoryItemStorage<THeader, TItem> implements ItemStorage<THeader, TItem> {
    private final List<TItem> items;
    private THeader header;

    public InMemoryItemStorage(THeader header) {
        this.items = new ArrayList<>();
        this.header = header;
    }

    @Override
    public THeader getHeader() {
        return this.header;
    }

    @Override
    public void putHeader(THeader header) {
        this.header = header;
    }

    @Override
    public TItem getItem(int index) {
        if (index >= items.size()) {
            throw new IllegalArgumentException(String.format("Tried to get item with id %d but InMemoryStorage only has %d items",
                    index, this.items.size()));
        }
        return items.get(index);
    }

    @Override
    public List<TItem> getItems(int index, int count) {
        if (index + count - 1 >= items.size()) {
            throw new IllegalArgumentException(String.format("Tried to get item with id %d but InMemoryStorage only has %d items",
                    index + count - 1, this.items.size()));
        }
        return new ArrayList<>(items.subList(index, index + count));
    }

    @Override
    public void putItem(int index, TItem item) {
        if (index > items.size()) {
            throw new IllegalArgumentException(String.format("Tried to put item with id %d but InMemoryStorage only has %d items",
                    index, this.items.size()));
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
