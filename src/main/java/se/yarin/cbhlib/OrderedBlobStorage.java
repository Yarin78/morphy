package se.yarin.cbhlib;

import lombok.NonNull;

import java.io.IOException;
import java.util.Comparator;

/**
 * <p>
 * Base class for storing blobs that can be fetched either by key (an id starting from 1),
 * or by content.
 * </p>
 */
public abstract class OrderedBlobStorage {
    private final byte[] firstKey, lastKey;

    public OrderedBlobStorage(int keyLength) {
        firstKey = new byte[keyLength];
        lastKey = new byte[keyLength];
        for (int i = 0; i < keyLength; i++) {
            lastKey[i] = -1;
        }
    }

    protected byte[] getFirstKeySentinel() {
        return firstKey;
    }

    protected byte[] getLastKeySentinel() {
        return lastKey;
    }

    public abstract int getNumBlobs();

    public abstract byte[] getBlob(int blobId) throws BlobStorageException, IOException;
    public abstract byte[][] getAllBlobs() throws IOException, BlobStorageException;
    public abstract int addBlob(@NonNull byte[] blob) throws BlobStorageException, IOException;
    public abstract void putBlob(int blobId, @NonNull byte[] blob) throws BlobStorageException;
    public abstract void deleteBlob(int blobId) throws BlobStorageException;

    public abstract int firstId() throws BlobStorageException, IOException;
    public abstract int lastId() throws BlobStorageException, IOException;
    public abstract byte[] firstBlob() throws BlobStorageException, IOException;
    public abstract byte[] lastBlob() throws BlobStorageException, IOException;
    public abstract int nextBlobId(int blobId) throws BlobStorageException, IOException;
    public abstract int previousBlobId(int blobId) throws BlobStorageException, IOException;
    public abstract byte[] nextBlob(@NonNull byte[] blob) throws BlobStorageException, IOException;
    public abstract byte[] previousBlob(@NonNull byte[] blob) throws BlobStorageException, IOException;

    protected Comparator<byte[]> getBlobComparator(final int keyLength) {
        return (o1, o2) -> {
            for (int i = 0; i < keyLength; i++) {
                int b1 = i < o1.length ? o1[i] : 0;
                int b2 = i < o2.length ? o2[i] : 0;
                if (b1 != b2) {
                    if (b1 < 0) b1 += 256;
                    if (b2 < 0) b2 += 256;
                    return b1 - b2;
                }
            }
            return 0;
        };
    }
}
