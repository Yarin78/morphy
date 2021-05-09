package se.yarin.morphy.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.exceptions.MorphyException;
import se.yarin.morphy.exceptions.MorphyIOException;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.exceptions.MorphyNotSupportedException;
import se.yarin.util.BlobChannel;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.nio.file.StandardOpenOption.*;

public class FileItemStorage<THeader, TItem> implements ItemStorage<THeader, TItem> {
    private long fileSize;
    private THeader header;
    private final BlobChannel channel;
    private final boolean laxMode;
    private final ItemStorageSerializer<THeader, TItem> serializer;

    public FileItemStorage(
            @NotNull File file,
            @NotNull ItemStorageSerializer<THeader, TItem> serializer,
            @NotNull THeader emptyHeader,
            @NotNull Set<OpenOption> options)
            throws IOException, MorphyInvalidDataException {

        boolean laxMode = options.contains(MorphyOpenOption.IGNORE_NON_CRITICAL_ERRORS);

        if (options.contains(WRITE) && laxMode) {
            throw new IllegalArgumentException("A storage open in WRITE mode can't also ignore non critical errors");
        }

        this.channel = BlobChannel.open(file.toPath(), MorphyOpenOption.valid(options));
        this.serializer = serializer;
        this.fileSize = this.channel.size();
        this.laxMode = laxMode;

        if (this.fileSize == 0) {
            if (options.contains(CREATE) || options.contains(CREATE_NEW)) {
                // The storage was presumably created
                putHeader(emptyHeader);
                this.fileSize = this.channel.size();
            } else {
                throw new IllegalStateException("File was empty");
            }
        } else {
            refreshHeader();
            if (this.serializer.serializedHeaderSize() != this.serializer.headerSize(this.header)
                && options.contains(WRITE)) {
                throw new MorphyNotSupportedException(String.format(
                        "The header in %s was %d bytes (%d bytes expected) and needs to be upgraded which is not yet supported",
                        file, this.serializer.headerSize(this.header), this.serializer.serializedHeaderSize()));
            }
        }
    }

    private void refreshHeader() throws IOException, MorphyInvalidDataException {
        ByteBuffer buf = ByteBuffer.allocate(serializer.serializedHeaderSize());
        channel.read(0, buf);
        buf.position(0); // No flip since serializer expects buf to be of length prefetchHeaderSize()
        this.header = this.serializer.deserializeHeader(buf);
    }

    @Override
    public @NotNull THeader getHeader() {
        return this.header;
    }

    @Override
    public void putHeader(@NotNull THeader header) {
        ByteBuffer buf = ByteBuffer.allocate(this.serializer.serializedHeaderSize());
        this.serializer.serializeHeader(header, buf);
        buf.flip();
        try {
            channel.write(0, buf);
        } catch (IOException e) {
            throw new MorphyIOException(e);
        }
        this.header = header;
    }

    @Override
    public boolean isEmpty() {
        return this.fileSize <= serializer.serializedHeaderSize();
    }

    public @NotNull ByteBuffer getItemRaw(int index) {
        ByteBuffer buf = ByteBuffer.allocate(serializer.itemSize(this.header));
        try {
            long offset = serializer.itemOffset(this.header, index);
            if (offset >= 0 && offset < this.fileSize) {
                channel.read(offset, buf);
                buf.rewind();
            }
        } catch (IOException e) {
            throw new MorphyIOException(e);
        }
        return buf;
    }

    @Override
    public @NotNull TItem getItem(int index) {
        ByteBuffer buf = ByteBuffer.allocate(serializer.itemSize(this.header));

        try {
            long offset = serializer.itemOffset(this.header, index);
            if (offset < 0 || offset >= this.fileSize) {
                if (!laxMode) {
                    throw new IllegalArgumentException(String.format("Tried to get item with id %d at offset %d but file size was %d",
                            index, offset, this.fileSize));
                }
                return serializer.emptyItem(index);
            } else {
                channel.read(offset, buf);
                buf.rewind();
            }
        } catch (IOException e) {
            throw new MorphyIOException(e);
        }

        return serializer.deserializeItem(index, buf);
    }

    @Override
    public @NotNull List<TItem> getItems(int index, int count) {
        return getItems(index, count, null);
    }

    @Override
    public @NotNull List<TItem> getItems(int index, int count, @Nullable ItemStorageFilter<TItem> filter) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        }

        int serializedItemSize = serializer.itemSize(this.header);
        ByteBuffer buf = ByteBuffer.allocate(serializedItemSize * count);
        try {
            long offset = serializer.itemOffset(this.header, index);
            // If there's something weird with the input parameters, fall back to getting items
            // in a slightly un-optimized way to ensure we get the same behaviour
            // as multiple calls to getItem would yield
            if (offset < 0 || offset >= this.fileSize) {
                return getItemsSimple(index, count, filter);
            } else {
                channel.read(offset, buf);
                if (buf.position() != buf.limit()) {
                    // Fewer bytes than expected were read
                    return getItemsSimple(index, count, filter);
                }
                buf.rewind();
            }
        } catch (IOException e) {
            throw new MorphyIOException(e);
        }

        ArrayList<TItem> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (filter == null) {
                result.add(serializer.deserializeItem(index + i, buf));
            } else {
                if (filter.matchesSerialized(buf)) {
                    TItem item = serializer.deserializeItem(index + i, buf);
                    result.add(filter.matches(item) ? item : null);
                } else {
                    buf.position(buf.position() + serializedItemSize);
                    result.add(null);
                }
            }
        }
        return result;
    }

    private @NotNull List<TItem> getItemsSimple(int index, int count, @Nullable ItemStorageFilter<TItem> filter) {
        ArrayList<TItem> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            TItem item = getItem(index + i);
            result.add(filter == null || filter.matches(item) ? item : null);
        }
        return result;
    }

    @Override
    public void putItem(int index, @NotNull TItem item) {
        long offset = serializer.itemOffset(this.header, index);
        if (offset > this.fileSize) {
            throw new MorphyIOException(String.format("Tried to put item with id %d at offset %d but file size was %d",
                    index, offset, this.fileSize));
        }
        ByteBuffer buf = ByteBuffer.allocate(serializer.itemSize(this.header));
        serializer.serializeItem(item, buf);
        buf.flip();
        try {
            channel.write(offset, buf);
        } catch (IOException e) {
            throw new MorphyIOException(e);
        }
        this.fileSize = Math.max(this.fileSize, offset + buf.position());
    }

    @Override
    public void close() throws MorphyException {
        try {
            channel.close();
        } catch (IOException e) {
            throw new MorphyIOException("Failed to close storage", e);
        }
    }
}
