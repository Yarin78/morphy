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

// TODO: Rename to PersistentDynamicBlobStorage!?
public class FileDynamicBlobStorage implements DynamicBlobStorage {
    private static final Logger log = LoggerFactory.getLogger(FileDynamicBlobStorage.class);
    private static final int DEFAULT_CHUNK_SIZE = 1024*1024;
    private static final int DEFAULT_PREFETCH_SIZE = 4096;

    private FileChannel channel;
    private BlobSizeRetriever blobSizeRetriever;
    private static final int HEADER_SIZE = 26; // The number of header bytes this implementation understands
    private int size; // Should match channel.size()
    private int headerSize; // The actual header size according to the metadata

    private final int chunkSize, prefetchSize;


    FileDynamicBlobStorage(
            @NonNull File file,
            @NonNull BlobSizeRetriever blobSizeRetriever) throws IOException {
        this(file, blobSizeRetriever, DEFAULT_CHUNK_SIZE, DEFAULT_PREFETCH_SIZE);
    }

    FileDynamicBlobStorage(
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
        channel.write(serializeMetadata(HEADER_SIZE, HEADER_SIZE));
        channel.close();
    }

    private void loadMetadata() throws IOException {
        channel.position(0);

        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE);
        channel.read(buf);
        buf.flip();

        headerSize = ByteBufferUtil.getUnsignedShortB(buf);
        size = ByteBufferUtil.getIntB(buf);
        int unknown1 = ByteBufferUtil.getIntB(buf);
        int unknown2 = ByteBufferUtil.getIntB(buf);
        int size2 = ByteBufferUtil.getIntB(buf);
        int unknown3 = ByteBufferUtil.getIntB(buf);
        int unknown4 = ByteBufferUtil.getIntB(buf);

        // TODO: unknown1 and unknown4 might be number of trash bytes at the end of the file
        if (unknown1 != 0) log.warn(String.format("Unknown int1 is %08X", unknown1));
        if (unknown2 != 0) log.warn(String.format("Unknown int2 is %08X", unknown2));
        if (unknown3 != 0) log.warn(String.format("Unknown int3 is %08X", unknown3));
        if (unknown4 != 0) log.warn(String.format("Unknown int4 is %08X", unknown4));
        if (size != size2) log.warn(String.format("Second size int is not the same as first (%08X != %08X)", size, size2));
        if (channel.size() != size) log.warn(String.format("File size doesn't match size in header (%d != %d)", channel.size(), size));
    }

    private static ByteBuffer serializeMetadata(int size, int headerSize) {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE);
        ByteBufferUtil.putShortB(buf, headerSize);
        ByteBufferUtil.putIntB(buf, size);
        ByteBufferUtil.putIntB(buf, 0);
        ByteBufferUtil.putIntB(buf, 0);
        ByteBufferUtil.putIntB(buf, size);
        ByteBufferUtil.putIntB(buf, 0);
        ByteBufferUtil.putIntB(buf, 0);
        buf.flip();
        return buf;
    }

    private void saveMetadata() throws IOException {
        ByteBuffer buf = serializeMetadata(size, headerSize);
        channel.position(0);
        channel.write(buf);
        channel.force(false);
    }

    @Override
    public ByteBuffer getBlob(int offset) throws IOException {
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
    public int addBlob(@NonNull ByteBuffer blob) throws IOException {
        int offset = size;
        channel.position(size);
        size += channel.write(blob);
        saveMetadata();
        return offset;
    }

    @Override
    public int putBlob(int oldOffset, @NonNull ByteBuffer blob) throws IOException {
        ByteBuffer oldBlob = getBlob(oldOffset);
        int oldSize = blobSizeRetriever.getBlobSize(oldBlob);
        int newSize = blobSizeRetriever.getBlobSize(blob);
        if (newSize > oldSize) {
            return addBlob(blob);
        }
        channel.position(oldOffset);
        channel.write(blob);
        channel.force(false);
        return oldOffset;
    }

    @Override
    public void forcePutBlob(int offset, @NonNull ByteBuffer blob) throws IOException {
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
