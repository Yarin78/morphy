package se.yarin.cbhlib.storage;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.exceptions.ChessBaseIOException;
import se.yarin.cbhlib.util.BufferedFileChannel;
import se.yarin.cbhlib.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import static java.nio.file.StandardOpenOption.*;

public class FileBlobStorage implements BlobStorage {
    private static final Logger log = LoggerFactory.getLogger(FileBlobStorage.class);

    private static final int DEFAULT_PREFETCH_SIZE = 4096;

    private final File file;
    private final BufferedFileChannel channel;
    private final BlobSizeRetriever blobSizeRetriever;
    public static final int DEFAULT_SERIALIZED_HEADER_SIZE = 26; // Size of header to create for a new storage

    private int headerSize; // The actual header size according to the metadata
    private int trashBytes; // The number of bytes in the storage that's not used for any data

    private final int prefetchSize;


    public FileBlobStorage(
            @NonNull File file,
            @NonNull BlobSizeRetriever blobSizeRetriever) throws IOException {
        this(file, blobSizeRetriever, 0, DEFAULT_PREFETCH_SIZE);
    }

    public FileBlobStorage(
            @NonNull File file,
            @NonNull BlobSizeRetriever blobSizeRetriever,
            int chunkSize, int prefetchSize) throws IOException {
        this.file = file;
        this.channel = BufferedFileChannel.open(file.toPath(), READ, WRITE);
        this.blobSizeRetriever = blobSizeRetriever;
        this.prefetchSize = prefetchSize;
        if (chunkSize > 0) {
            this.channel.setChunkSize(chunkSize);
        }

        loadMetadata();
    }

    public static void createEmptyStorage(@NonNull File file)
            throws IOException {
        FileChannel channel = FileChannel.open(file.toPath(), CREATE_NEW, READ, WRITE);
        channel.write(serializeMetadata(DEFAULT_SERIALIZED_HEADER_SIZE, DEFAULT_SERIALIZED_HEADER_SIZE, 0));
        channel.close();
    }

    private void loadMetadata() throws IOException {
        ByteBuffer buf = channel.read(0, DEFAULT_SERIALIZED_HEADER_SIZE);

        headerSize = ByteBufferUtil.getUnsignedShortB(buf);
        int size = ByteBufferUtil.getIntB(buf);
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
        ByteBuffer buf = serializeMetadata((int) channel.size(), headerSize, trashBytes);
        channel.write(0, buf);
    }

    @Override
    public ByteBuffer readBlob(int offset) {
        try {
            ByteBuffer buf = channel.read(offset, prefetchSize);
            int size = blobSizeRetriever.getBlobSize(buf);
            return channel.read(offset, size);
        } catch (IOException e) {
            throw new ChessBaseIOException("Failed to read blob at offset " + offset + " in " + file.getName());
        }
    }

    @Override
    public int writeBlob(@NonNull ByteBuffer blob) {
        try {
            int offset = (int) channel.size();
            channel.append(blob);
            saveMetadata();
            return offset;
        } catch (IOException e) {
            throw new ChessBaseIOException("Failed to append blob to " + file.getName(), e);
        }
    }

    @Override
    public void writeBlob(int offset, @NonNull ByteBuffer blob) {
        try {
            channel.write(offset, blob);
            saveMetadata();
        } catch (IOException e) {
            throw new ChessBaseIOException("Failed to write blob to " + file.getName() + " at offset " + offset, e);
        }
    }

    @Override
    public int getSize() {
        return (int) channel.size();
    }

    public int getHeaderSize() {
        return headerSize;
    }

    @Override
    public void insert(int offset, int noBytes) {
        try {
            channel.insert(offset, noBytes);
        } catch (IOException e) {
            throw new ChessBaseIOException("Failed to insert " + noBytes + " in blob " + file.getName());
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
