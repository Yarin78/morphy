package se.yarin.morphy.storage;

import se.yarin.morphy.exceptions.MorphyIOException;

import java.util.List;

public interface ItemStorage<THeader, TItem> {
    THeader getHeader();
    void putHeader(THeader header);

    /**
     * Gets an item from the storage
     * If the index is invalid, either an empty (non-null) item is returned,
     * or {@link MorphyIOException} is thrown depending on the open option.
     * @param index the id of the item
     * @return an item
     */
    TItem getItem(int index);
    void putItem(int index, TItem item);

    /**
     * Gets a range of items from the storage
     * If any index is invalid, either an empty (non-null) item is returned,
     * or {@link MorphyIOException} is thrown depending on the open option.
     * @param index the id of the first item
     * @param count the number of items to return
     * @return a list of items
     */
    List<TItem> getItems(int index, int count);

    void close() throws MorphyIOException;
}
