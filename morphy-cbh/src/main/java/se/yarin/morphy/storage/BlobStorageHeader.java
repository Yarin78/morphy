package se.yarin.morphy.storage;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.exceptions.MorphyNotSupportedException;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class BlobStorageHeader {
    private static final Logger log = LoggerFactory.getLogger(BlobStorageHeader.class);

    public static final int LEGACY_SERIALIZED_HEADER_SIZE = 10;
    public static final int DEFAULT_SERIALIZED_HEADER_SIZE = 26;

    private final int headerSize;
    private final int size; // File size
    private final int wasted; // Number of bytes in the file that's "unused" due to fragmentation
    private final long sizeLong; // Same as size but 64 bit
    private final long wastedLong; // Same as unused but 64 bit

    public static BlobStorageHeader empty() {
        return of(DEFAULT_SERIALIZED_HEADER_SIZE, DEFAULT_SERIALIZED_HEADER_SIZE, 0);
    }

    public static BlobStorageHeader of(int headerSize, long fileSize, long wasted) {
        return new BlobStorageHeader(headerSize, (int) fileSize, (int) wasted, fileSize, wasted);
    }

    public int headerSize() { return headerSize;}
    public int size() { return size; };
    public int wasted() { return wasted; };
    public long sizeLong() { return sizeLong; }
    public long wastedLong() { return wastedLong; }

    public BlobStorageHeader(int headerSize, int size, int wasted, long sizeLong, long wastedLong) {
        this.headerSize = headerSize;
        this.size = size;
        this.wasted = wasted;
        this.sizeLong = sizeLong;
        this.wastedLong = wastedLong;
    }

    public ByteBuffer serialize() {
        if (headerSize() != LEGACY_SERIALIZED_HEADER_SIZE && headerSize() != DEFAULT_SERIALIZED_HEADER_SIZE) {
            throw new MorphyNotSupportedException(String.format("Invalid header size (%d) in BlobStorageHeader", headerSize()));
        }
        ByteBuffer buf = ByteBuffer.allocate(headerSize());
        ByteBufferUtil.putShortB(buf, headerSize());
        ByteBufferUtil.putIntB(buf, size());
        ByteBufferUtil.putIntB(buf, wasted());
        if (headerSize() == DEFAULT_SERIALIZED_HEADER_SIZE) {
            ByteBufferUtil.putLongB(buf, sizeLong());
            ByteBufferUtil.putLongB(buf, wastedLong());
        }
        buf.flip();
        return buf;
    }

    public static BlobStorageHeader deserialize(ByteBuffer buf) {
        int headerSize = ByteBufferUtil.getUnsignedShortB(buf);
        int size = ByteBufferUtil.getIntB(buf);
        int unused = ByteBufferUtil.getIntB(buf);
        long sizeLong = size;
        long unusedLong = unused;

        if (headerSize >= DEFAULT_SERIALIZED_HEADER_SIZE) {
            sizeLong = ByteBufferUtil.getLongB(buf);
            unusedLong = ByteBufferUtil.getLongB(buf);
        }

        return new BlobStorageHeader(headerSize, size, unused, sizeLong, unusedLong);
    }

    public void validate(boolean strict, @NotNull String headerName, long expectedSize) {
        if (headerSize() != 10 && headerSize() != 26) {
            String msg = String.format("Invalid header size in %s: %d", headerName, headerSize());
            if (strict) {
                throw new MorphyInvalidDataException(msg);
            } else {
                log.warn(msg);
            }
            if (headerSize() >= 256) {
                // Happens in ussr.cba; TODO: how does CB handle this?
                throw new MorphyInvalidDataException("Invalid header size in " + headerName + ": " + headerSize());
            }
        }

        // In later versions of the header a long version of the same value was included
        // These values can sometimes be out of sync. The 32 bit version seems to be the one that is always correct.
        // However, we rely on neither of these values when appending blobs all because we use the actual
        // size of the underlying file instead.
        if (size() != (int) sizeLong()) {
            // This seems to be a critical error in the ChessBase integrity checker ("CBG Prolog damaged")
            String msg = String.format("Blob storage file size values don't match (%d != %d)", size(), sizeLong());
            if (strict) {
                throw new MorphyInvalidDataException(msg);
            } else {
                log.warn(msg);
            }
        }

        if (wasted() != (int) wastedLong()) {
            String msg = String.format("Blob storage wasted values don't match (%d != %d)", wasted(), wastedLong());
            if (strict) {
                throw new MorphyInvalidDataException(msg);
            } else {
                log.warn(msg);
            }
        }

        if (expectedSize != size()) {
            String msg = String.format("%s: File size doesn't match size in header (%d != %d)", headerName, expectedSize, size());
            if (strict) {
                throw new MorphyInvalidDataException(msg);
            } else {
                log.warn(msg);
            }
        }
    }
}
