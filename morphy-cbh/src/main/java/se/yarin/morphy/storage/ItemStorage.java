package se.yarin.morphy.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.exceptions.MorphyIOException;

import java.util.List;

/**
 * Interface of a simple structured storage containing items of the same type.
 * Items are referenced by an index. It's up to the implementation to map a valid index range.
 * The storage also contains a header where metadata about the storage can be stored.
 * It's up to the implementation of the interface to handle the logic of the items and header, serialization etc
 * @param <THeader> the type of the header
 * @param <TItem> the type of the item
 */
public interface ItemStorage<THeader, TItem> {
    /**
     * Gets the header from the storage
     * @return a header
     */
    @NotNull THeader getHeader();

    /**
     * Updates the header in the storage
     * @param header the new header
     */
    void putHeader(@NotNull THeader header);

    /**
     * Determines if the storage is empty (has 0 items), regardless of what the header metadata might say.
     * @return true if the storage has 0 item records
     */
    boolean isEmpty();

    /**
     * Gets an item from the storage
     * If the index is invalid, {@link IllegalArgumentException} should be thrown, unless
     * the storage is opened in non-strict (safe) mode, in which case empty items can be returned.
     * It's up to the implementation of the storage to support this.
     * @param index the id of the item
     * @return an item
     * @throws IllegalArgumentException if the index points to an item outside of the storage and the storage is open in strict mode
     * @throws MorphyIOException if an IO error occurred when reading data frmo IO
     */
    @NotNull TItem getItem(int index);

    /**
     * Puts an item to the storage at the specific index
     * @param index the index into the storage
     * @param item the item to put
     * @throws IllegalArgumentException if the index points to a place in the storage that couldn't be written to
     * (it's up to the implementation to decide valid indexes)
     */
    void putItem(int index, @NotNull TItem item);

    /**
     * Gets a range of items from the storage
     * If any index in the range is invalid, {@link IllegalArgumentException} should be thrown, unless
     * the storage is opened in non-strict (safe) mode, in which case empty items can be returned.
     * @param index the id of the first item
     * @param count the number of items to return
     * @return a list of items
     * @throws IllegalArgumentException if any index in the range points to an item outside of the storage and the storage is open in strict mode
     * @throws MorphyIOException if an IO error occurred when reading the data
     */
    @NotNull List<TItem> getItems(int index, int count);

    /**
     * Gets a range of items from the storage that matches the given filter
     * If any index in the range is invalid, {@link IllegalArgumentException} should be thrown, unless
     * the storage is opened in non-strict (safe) mode, in which case empty items can be returned.
     * @param index the id of the first item
     * @param count the number of items to return
     * @param filter the filter to match items against
     * @return a list of items. Will always contain count items; non-matching items will have null values
     * @throws IllegalArgumentException if any index in the range points to an item outside of the storage and the storage is open in strict mode
     * @throws MorphyIOException if an IO error occurred when reading the data
     */
    @NotNull List<TItem> getItems(int index, int count, @Nullable ItemStorageFilter<TItem> filter);

    /**
     * Closes the estorage
     * @throws MorphyIOException if an IO error occurred when closing the storage
     */
    void close() throws MorphyIOException;
}
