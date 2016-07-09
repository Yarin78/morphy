package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class InMemoryBlobStorage extends OrderedBlobStorage {
    private static final Logger log = LoggerFactory.getLogger(InMemoryBlobStorage.class);

    private Map<Integer, byte[]> blobMap;
    private TreeMap<byte[], Integer> blobTree;
    private int nextId = 0;

    /**
     * Creates a new in-memory blob storage
     * @param keyLength number of bytes in the blob that is part of the unique key
     */
    public InMemoryBlobStorage(int keyLength) {
        super(keyLength);
        this.blobMap = new HashMap<>();
        this.blobTree = new TreeMap<>(getBlobComparator(keyLength));
    }

    @Override
    public int getNumBlobs() {
        return blobMap.size();
    }

    @Override
    public byte[] getBlob(int blobId) throws BlobStorageException {
        byte[] blob = blobMap.get(blobId);
        if (blob == null) {
            throw new BlobStorageException("There is no blob with id " + blobId);
        }
        return blob;
    }

    @Override
    public byte[][] getAllBlobs() throws IOException, BlobStorageException {
        byte[][] allBlobs = new byte[nextId][];
        for (Map.Entry<Integer, byte[]> entry : blobMap.entrySet()) {
            allBlobs[entry.getKey()] = entry.getValue();
        }
        return allBlobs;
    }

    @Override
    public int addBlob(@NonNull byte[] blob) throws BlobStorageException {
        Integer existingId = blobTree.get(blob);
        if (existingId != null) {
            throw new BlobStorageException(String.format(
                    "Tried to add blob %s but a blob with the same data exists having id %d",
                    blob.toString(), existingId));
        }
        blobMap.put(nextId, blob);
        blobTree.put(blob, nextId);
        return nextId++;
    }

    @Override
    public void putBlob(int blobId, @NonNull byte[] blob) throws BlobStorageException {
        Integer existingId = blobTree.get(blob);
        if (existingId != null && existingId != blobId) {
            throw new BlobStorageException(String.format(
                    "Tried to replace blob %s with id %d but a blob with the same data exists having id %d",
                    CBUtil.toHexString(blob), blobId, existingId));
        }
        log.debug(String.format("Put blob id %d: %s", blobId, CBUtil.toHexString(blob)));

        byte[] oldBlob = blobMap.get(blobId);
        if (oldBlob != null) {
            blobTree.remove(oldBlob);
        }
        blobMap.put(blobId, blob);
        blobTree.put(blob, blobId);

        nextId = Math.max(nextId, blobId + 1);
    }

    @Override
    public void deleteBlob(int blobId) throws BlobStorageException {
        byte[] blob = blobMap.get(blobId);
        if (blob == null) {
            throw new BlobStorageException("There is no blob with id " + blobId);
        }
        blobTree.remove(blob);
        blobMap.remove(blobId);
    }

    @Override
    public int firstId() {
        return blobTree.size() == 0 ? -1 : blobTree.firstEntry().getValue();
    }

    @Override
    public int lastId() {
        return blobTree.size() == 0 ? -1 : blobTree.lastEntry().getValue();
    }

    @Override
    public byte[] firstBlob() {
        return blobTree.size() == 0 ? null : blobTree.firstKey();
    }

    @Override
    public byte[] lastBlob() {
        return blobTree.size() == 0 ? null : blobTree.lastKey();
    }

    @Override
    public int nextBlobId(int blobId) throws BlobStorageException {
        byte[] blob = blobMap.get(blobId);
        if (blob == null) {
            throw new BlobStorageException("There is no blob with id " + blobId);
        }
        Map.Entry<byte[], Integer> entry = blobTree.higherEntry(blob);
        if (entry == null) return -1;
        return entry.getValue();
    }

    @Override
    public int previousBlobId(int blobId) throws BlobStorageException {
        byte[] blob = blobMap.get(blobId);
        if (blob == null) {
            throw new BlobStorageException("There is no blob with id " + blobId);
        }
        Map.Entry<byte[], Integer> entry = blobTree.higherEntry(blob);
        if (entry == null) return -1;
        return entry.getValue();
    }

    @Override
    public byte[] nextBlob(@NonNull byte[] blob) {
        return blobTree.higherKey(blob);
    }

    @Override
    public byte[] previousBlob(@NonNull byte[] blob) {
        return blobTree.lowerKey(blob);
    }
}
