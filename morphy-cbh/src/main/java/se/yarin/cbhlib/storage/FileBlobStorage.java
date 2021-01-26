package se.yarin.cbhlib.storage;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.exceptions.ChessBaseIOException;
import se.yarin.cbhlib.util.BlobChannel;
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
    private final BlobChannel channel;
    private final BlobSizeRetriever blobSizeRetriever;
    public static final int DEFAULT_SERIALIZED_HEADER_SIZE = 26; // Size of header to create for a new storage

    private int headerSize; // The actual header size according to the metadata
    private long unused; // The number of bytes in the storage that's not used for any data TODO: Needs to be updated

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
        this.channel = BlobChannel.open(file.toPath(), READ, WRITE);
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
        if (headerSize >= 256) {
            // Happens in ussr.cba; TODO: how does CB handle this?
            throw new IOException("Invalid header size in " + file.getName() + ": " + headerSize);
        }
        if (headerSize != 10 && headerSize != 26) {
            log.warn("Unknown header size in " + file.getName() + ": " + headerSize);
        }

        int shortSize = ByteBufferUtil.getIntB(buf);
        int shortUnused = ByteBufferUtil.getIntB(buf);
        long size = shortSize;
        unused = shortUnused;

        if (headerSize >= 26) {
            // In later versions of the header a long version of the same value was included
            // This value should be prioritized, except if they differ in the lower 32 bits;
            // then the 32-bit value should be used.
            // The values might differ if the database was created in a modern version of CB
            // and then later modified in an earlier version (TODO: confirm this)
            size = ByteBufferUtil.getLongB(buf);
            unused = ByteBufferUtil.getLongB(buf);

            if ((int) size != shortSize) {
                log.warn(String.format("%s: File size values don't match (%d != %d)", file.getName(), shortSize, size));
                size = shortSize;
            }

            if ((int) unused != shortUnused) {
                log.warn(String.format("%s: Unused bytes values don't match (%d != %d)", file.getName(), shortUnused, unused));
                unused = shortUnused;
            }
        }

        if (channel.size() != size) {
            log.warn(String.format("%s: File size doesn't match size in header (%d != %d)", file.getName(), channel.size(), size));
        }
    }

    private static ByteBuffer serializeMetadata(long size, int headerSize, long unused) {
        ByteBuffer buf = ByteBuffer.allocate(headerSize);
        ByteBufferUtil.putShortB(buf, headerSize);
        ByteBufferUtil.putIntB(buf, (int) size);
        ByteBufferUtil.putIntB(buf, (int) unused);
        if (headerSize >= 26) {
            ByteBufferUtil.putLongB(buf, size);
            ByteBufferUtil.putLongB(buf, unused);
        }
        buf.flip();
        return buf;
    }

    private void saveMetadata() throws IOException {
        ByteBuffer buf = serializeMetadata(channel.size(), headerSize, unused);
        channel.write(0, buf);
    }

    @Override
    public ByteBuffer readBlob(long offset) {
        try {
            ByteBuffer buf = channel.read(offset, prefetchSize);
            int size = blobSizeRetriever.getBlobSize(buf);
            return channel.read(offset, size);
        } catch (IOException e) {
            throw new ChessBaseIOException("Failed to read blob at offset " + offset + " in " + file.getName());
        }
    }

    @Override
    public long writeBlob(@NonNull ByteBuffer blob) {
        try {
            long offset = channel.size();
            channel.append(blob);
            saveMetadata();
            return offset;
        } catch (IOException e) {
            throw new ChessBaseIOException("Failed to append blob to " + file.getName(), e);
        }
    }

    @Override
    public void writeBlob(long offset, @NonNull ByteBuffer blob) {
        try {
            channel.write(offset, blob);
            saveMetadata();
        } catch (IOException e) {
            throw new ChessBaseIOException("Failed to write blob to " + file.getName() + " at offset " + offset, e);
        }
    }

    @Override
    public long getSize() {
        return channel.size();
    }

    public int getHeaderSize() {
        return headerSize;
    }

    @Override
    public void insert(long offset, long noBytes) {
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
