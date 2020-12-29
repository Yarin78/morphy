package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

public class FileBlobStorage implements BlobStorage {
    private static final Logger log = LoggerFactory.getLogger(FileBlobStorage.class);
    private static final int DEFAULT_CHUNK_SIZE = 1024*1024;
    private static final int DEFAULT_PREFETCH_SIZE = 4096;

    private FileChannel channel;
    private BlobSizeRetriever blobSizeRetriever;
    private static final int DEFAULT_SERIALIZED_HEADER_SIZE = 26; // Size of header to create for a new storage
    private int size; // Should match channel.size()
    private int headerSize; // The actual header size according to the metadata
    private int trashBytes; // The number of bytes in the storage that's not used for any data

    private final int chunkSize, prefetchSize;


    FileBlobStorage(
            @NonNull File file,
            @NonNull BlobSizeRetriever blobSizeRetriever) throws IOException {
        this(file, blobSizeRetriever, DEFAULT_CHUNK_SIZE, DEFAULT_PREFETCH_SIZE);
    }

    FileBlobStorage(
            @NonNull File file,
            @NonNull BlobSizeRetriever blobSizeRetriever,
            int chunkSize, int prefetchSize) throws IOException {
        this.channel = FileChannel.open(file.toPath(), READ, WRITE);
        this.blobSizeRetriever = blobSizeRetriever;
        this.chunkSize = chunkSize;
        this.prefetchSize = prefetchSize;

        loadMetadata();
    }

    static void createEmptyStorage(@NonNull File file)
            throws IOException {
        FileChannel channel = FileChannel.open(file.toPath(), CREATE_NEW, READ, WRITE);
        channel.write(serializeMetadata(DEFAULT_SERIALIZED_HEADER_SIZE, DEFAULT_SERIALIZED_HEADER_SIZE, 0));
        channel.close();
    }

    private void loadMetadata() throws IOException {
        channel.position(0);

        ByteBuffer buf = ByteBuffer.allocate(DEFAULT_SERIALIZED_HEADER_SIZE);
        channel.read(buf);
        buf.flip();

        headerSize = ByteBufferUtil.getUnsignedShortB(buf);
        size = ByteBufferUtil.getIntB(buf);
        trashBytes = ByteBufferUtil.getIntB(buf);
        int unknown2 = 0, unknown3 = 0, trashBytes2 = trashBytes, size2 = size;

        if (headerSize >= 14) unknown2 = ByteBufferUtil.getIntB(buf);
        if (headerSize >= 18) size2 = ByteBufferUtil.getIntB(buf);
        if (headerSize >= 22) unknown3 = ByteBufferUtil.getIntB(buf);
        if (headerSize >= 26) trashBytes2 = ByteBufferUtil.getIntB(buf);

        if (unknown2 != 0) log.warn(String.format("Unknown int2 is %08X", unknown2));
        if (unknown3 != 0) log.warn(String.format("Unknown int3 is %08X", unknown3));

        if (trashBytes != trashBytes2) log.warn(String.format("Second trash bytes int is not same as the first (%d != %d)", trashBytes, trashBytes2));
        if (size != size2) log.warn(String.format("Second size int is not the same as first (%d != %d)", size, size2));
        if (channel.size() != size) log.warn(String.format("File size doesn't match size in header (%d != %d)", channel.size(), size));
    }

    private static ByteBuffer serializeMetadata(int size, int headerSize, int trashBytes) {
        ByteBuffer buf = ByteBuffer.allocate(DEFAULT_SERIALIZED_HEADER_SIZE);
        ByteBufferUtil.putShortB(buf, headerSize);
        ByteBufferUtil.putIntB(buf, size);
        ByteBufferUtil.putIntB(buf, trashBytes);
        ByteBufferUtil.putIntB(buf, 0);
        ByteBufferUtil.putIntB(buf, size);
        ByteBufferUtil.putIntB(buf, 0);
        ByteBufferUtil.putIntB(buf, trashBytes);
        buf.flip();
        return buf;
    }

    private void saveMetadata() throws IOException {
        ByteBuffer buf = serializeMetadata(size, headerSize, trashBytes);
        channel.position(0);
        channel.write(buf);
        channel.force(false);
    }

    @Override
    public ByteBuffer readBlob(int offset) throws IOException {
        channel.position(offset);
        ByteBuffer buf = ByteBuffer.allocate(prefetchSize);
        channel.read(buf);
        buf.position(0);
        int size = blobSizeRetriever.getBlobSize(buf);
        if (size > prefetchSize) {
            ByteBuffer newBuf = ByteBuffer.allocate(size);
            newBuf.put(buf);
            channel.read(newBuf);
            buf = newBuf;
        }
        buf.position(0);
        buf.limit(size);
        return buf;
    }

    @Override
    public int writeBlob(@NonNull ByteBuffer blob) throws IOException {
        int offset = size;
        channel.position(size);
        size += channel.write(blob);
        saveMetadata();
        return offset;
    }

    @Override
    public void writeBlob(int offset, @NonNull ByteBuffer blob) throws IOException {
        channel.position(offset);
        channel.write(blob);
        channel.force(false);
        size = (int) channel.size();
        saveMetadata();
    }

    @Override
    public int getSize() {
        return size;
    }

    public int getHeaderSize() {
        return headerSize;
    }

    @Override
    public void insert(int offset, int noBytes) throws IOException {
        if (noBytes < 0) {
            throw new IllegalArgumentException("Number of bytes to insert must be non-negative");
        }
        if (noBytes == 0) {
            return;
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(chunkSize);
        int pos = size;
        while (pos > offset) {
            // Invariant: All bytes at position pos and after have been shifted noBytes bytes
            pos -= chunkSize;
            int length = chunkSize;
            if (pos < offset) {
                length -= (offset - pos);
                pos = offset;
            }
            channel.position(pos);
            buf.limit(length);
            channel.read(buf);
            buf.flip();
            channel.position(pos + noBytes);
            channel.write(buf);
            buf.clear();
        }

        size += noBytes;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
