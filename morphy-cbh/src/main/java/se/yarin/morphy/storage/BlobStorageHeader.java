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
    private final int size;
    private final int unused;
    private final long sizeLong;
    private final long unusedLong;

    public static BlobStorageHeader empty() {
        return of(DEFAULT_SERIALIZED_HEADER_SIZE, DEFAULT_SERIALIZED_HEADER_SIZE);
    }

    public static BlobStorageHeader of(int headerSize, long fileSize) {
        return new BlobStorageHeader(headerSize, (int) fileSize, 0, fileSize, 0);
    }

    int headerSize() { return headerSize;}
    int size() { return size; };
    int unused() { return unused; };
    long sizeLong() { return sizeLong; }
    long unusedLong() { return unusedLong; }

    public BlobStorageHeader(int headerSize, int size, int unused, long sizeLong, long unusedLong) {
        this.headerSize = headerSize;
        this.size = size;
        this.unused = unused;
        this.sizeLong = sizeLong;
        this.unusedLong = unusedLong;
    }

    public ByteBuffer serialize() {
        if (headerSize() != LEGACY_SERIALIZED_HEADER_SIZE && headerSize() != DEFAULT_SERIALIZED_HEADER_SIZE) {
            throw new MorphyNotSupportedException(String.format("Invalid header size (%d) in BlobStorageHeader", headerSize()));
        }
        ByteBuffer buf = ByteBuffer.allocate(headerSize());
        ByteBufferUtil.putShortB(buf, headerSize());
        ByteBufferUtil.putIntB(buf, size());
        ByteBufferUtil.putIntB(buf, unused());
        if (headerSize() == DEFAULT_SERIALIZED_HEADER_SIZE) {
            ByteBufferUtil.putLongB(buf, sizeLong());
            ByteBufferUtil.putLongB(buf, unusedLong());
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
            unusedLong = ByteBufferUtil.getIntB(buf);
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

        if (unused() != (int) unusedLong()) {
            String msg = String.format("Blob storage unused values don't match (%d != %d)", unused(), unusedLong());
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
