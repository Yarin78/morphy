package se.yarin.morphy.storage;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.exceptions.MorphyIOException;

import java.util.List;

public interface ItemStorage<THeader, TItem> {
    @NotNull THeader getHeader();
    void putHeader(@NotNull THeader header);

    /**
     * Determines if the storage is empty (has at least 1 item),
     * regardless of what the header metadata might say.
     * @return true if the storage has at least one item record.
     */
    boolean isEmpty();

    /**
     * Gets an item from the storage
     * If the index is invalid, either an empty (non-null) item is returned,
     * or {@link MorphyIOException} is thrown depending on the open option.
     * @param index the id of the item
     * @return an item
     */
    @NotNull TItem getItem(int index);
    void putItem(int index, @NotNull TItem item);

    /**
     * Gets a range of items from the storage
     * If any index is invalid, either an empty (non-null) item is returned,
     * or {@link MorphyIOException} is thrown depending on the open option.
     * @param index the id of the first item
     * @param count the number of items to return
     * @return a list of items
     */
    @NotNull List<TItem> getItems(int index, int count);

    void close() throws MorphyIOException;
}
