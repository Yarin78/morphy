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
    private final TItem emptyItem;
    private final BlobChannel channel;
    private final ItemStorageSerializer<THeader, TItem> serializer;
    private final boolean strict;

    public FileItemStorage(
            @NotNull File file,
            @NotNull ItemStorageSerializer<THeader, TItem> serializer,
            @NotNull THeader emptyHeader,
            @NotNull Set<OpenOption> options)
            throws IOException, MorphyInvalidDataException {
        this(file, serializer, emptyHeader, null, options);
    }

    public FileItemStorage(
            @NotNull File file,
            @NotNull ItemStorageSerializer<THeader, TItem> serializer,
            @NotNull THeader emptyHeader,
            @Nullable TItem emptyItem,
            @NotNull Set<OpenOption> options)
            throws IOException, MorphyInvalidDataException {

        if (options.contains(WRITE) && options.contains(MorphyOpenOption.IGNORE_NON_CRITICAL_ERRORS)) {
            throw new IllegalArgumentException("A storage open in WRITE mode can't also ignore errors");
        }

        this.channel = BlobChannel.open(file.toPath(), MorphyOpenOption.valid(options));
        this.strict = !options.contains(MorphyOpenOption.IGNORE_NON_CRITICAL_ERRORS);
        this.serializer = serializer;
        this.fileSize = this.channel.size();
        this.emptyItem = emptyItem;

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

    @Override
    public @NotNull TItem getItem(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        ByteBuffer buf = ByteBuffer.allocate(serializer.itemSize(this.header));

        try {
            long offset = serializer.itemOffset(this.header, index);
            if (offset >= this.fileSize) {
                if (strict) {
                    throw new MorphyIOException(String.format("Tried to get item with id %d at offset %d but file size was %d",
                            index, offset, this.fileSize));
                }
                if (emptyItem != null) {
                    return emptyItem;
                }
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
        if (index < 0 || count < 0 ) {
            throw new IllegalArgumentException("index and count must be non-negative");
        }

        ByteBuffer buf = ByteBuffer.allocate(serializer.itemSize(this.header) * count);
        try {
            long offset = serializer.itemOffset(this.header, index);
            if (offset >= this.fileSize) {
                if (strict) {
                    throw new MorphyIOException(String.format("Tried to get item with id %d at offset %d but file size was %d",
                            index, offset, this.fileSize));
                }
            } else {
                channel.read(offset, buf);
                buf.rewind();
            }
        } catch (IOException e) {
            throw new MorphyIOException(e);
        }

        ArrayList<TItem> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(serializer.deserializeItem(index + i, buf));
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
