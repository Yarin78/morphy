package se.yarin.morphy.storage;

import se.yarin.cbhlib.storage.EntityNode;
import se.yarin.morphy.exceptions.MorphyException;
import se.yarin.morphy.exceptions.MorphyIOException;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

public class FileItemStorage<THeader, TItem> implements ItemStorage<THeader, TItem> {
    private long fileSize;
    private THeader header;
    private final FileChannel channel;
    private final ItemStorageSerializer<THeader, TItem> serializer;
    private final boolean strict;

    public FileItemStorage(File file, ItemStorageSerializer<THeader, TItem> serializer, Set<OpenOption> options)
            throws IOException, MorphyInvalidDataException {

        strict = options.contains(OpenOption.STRICT);

        if (options.contains(OpenOption.WRITE)) {
            this.channel = FileChannel.open(file.toPath(), READ, WRITE);
        } else {
            this.channel = FileChannel.open(file.toPath(), READ);
        }

        this.serializer = serializer;
        this.fileSize = this.channel.size();
        refreshHeader();
    }

    private void refreshHeader() throws IOException, MorphyInvalidDataException {
        channel.position(0);
        ByteBuffer buf = ByteBuffer.allocate(serializer.expectedHeaderSize());
        channel.read(buf);
        buf.position(0); // No flip since serializer expects buf to be of length prefetchHeaderSize()
        this.header = this.serializer.deserializeHeader(buf);
    }

    @Override
    public THeader getHeader() {
        return this.header;
    }

    @Override
    public void putHeader(THeader header) {
        ByteBuffer buf = ByteBuffer.allocate(this.serializer.expectedHeaderSize());
        this.serializer.serializeHeader(header, buf);
        buf.flip();
        try {
            channel.position(0);
            channel.write(buf);
        } catch (IOException e) {
            throw new MorphyIOException(e);
        }
        this.header = header;
    }

    @Override
    public TItem getItem(int index) {
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
            } else {
                channel.position(offset);
                channel.read(buf);
                buf.rewind();
            }
        } catch (IOException e) {
            throw new MorphyIOException(e);
        }

        return serializer.deserializeItem(index, buf);
    }

    @Override
    public List<TItem> getItems(int index, int count) {
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
                channel.position(offset);
                channel.read(buf);
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
    public void putItem(int index, TItem item) {
        long offset = serializer.itemOffset(this.header, index);
        if (offset > this.fileSize) {
            throw new MorphyIOException(String.format("Tried to put item with id %d at offset %d but file size was %d",
                    index, offset, this.fileSize));
        }
        ByteBuffer buf = ByteBuffer.allocate(serializer.itemSize(this.header));
        serializer.serializeItem(item, buf);
        buf.flip();
        try {
            channel.position(offset);
            channel.write(buf);
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
