package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

public class FileBlobStorageWithInMemoryIndex extends FileBlobStorage {
    private static final Logger log = LoggerFactory.getLogger(FileBlobStorageWithInMemoryIndex.class);

    private final int keyLength;
    private final String storageName;
    private TreeMap<byte[], Integer> blobTree;

    public FileBlobStorageWithInMemoryIndex(File file, int keyLength) throws IOException {
        super(file, keyLength);
        this.storageName = file.getName();
        this.keyLength = keyLength;
    }

    protected FileBlobStorageWithInMemoryIndex(File file, int blobSize, int keyLength) throws IOException {
        super(file, blobSize, keyLength);
        this.storageName = file.getName();
        this.keyLength = keyLength;
    }

    public static FileBlobStorageWithInMemoryIndex create(File file, int blobSize, int keyLength)
            throws IOException {
        return new FileBlobStorageWithInMemoryIndex(file, blobSize, keyLength);
    }

    private void initIndex() throws IOException, BlobStorageException {
        log.info("Building in memory index for " + storageName + " containing " + getNumBlobs() + " entries");
        long start = System.currentTimeMillis();
        blobTree = new TreeMap<>(getBlobComparator(keyLength));
        int id = 0;
        for (byte[] bytes : super.getAllBlobs()) {
            if (bytes != null) {
                blobTree.put(bytes, id);
            }
            id++;
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("Finished building index for " + storageName + " in " + elapsed + " ms");
    }

    private void ensureIndexExists() throws IOException, BlobStorageException {
        if (blobTree == null) {
            initIndex();
        }
    }

    @Override
    public int addBlob(@NonNull byte[] blob) throws BlobStorageException, IOException {
        ensureIndexExists();
        Integer existingId = blobTree.get(blob);
        if (existingId != null) {
            throw new BlobStorageException(String.format(
                    "Tried to add blob %s but a blob with the same data exists having id %d",
                    CBUtil.toHexString(blob), existingId));
        }

        int id = super.addBlob(blob);
        blobTree.put(blob, id);
        return id;
    }

    @Override
    public void putBlob(int blobId, @NonNull byte[] blob) throws BlobStorageException {
        // TODO: Write through to super and update index
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBlob(int blobId) throws BlobStorageException {
        // TODO: Write through to super and update index
        throw new UnsupportedOperationException();
    }

    @Override
    public int firstId() throws IOException, BlobStorageException {
        ensureIndexExists();
        return blobTree.size() == 0 ? -1 : blobTree.firstEntry().getValue();
    }

    @Override
    public int lastId() throws IOException, BlobStorageException {
        ensureIndexExists();
        return blobTree.size() == 0 ? -1 : blobTree.lastEntry().getValue();
    }

    @Override
    public byte[] firstBlob() throws IOException, BlobStorageException {
        ensureIndexExists();
        return blobTree.size() == 0 ? null : blobTree.firstKey();
    }

    @Override
    public byte[] lastBlob() throws IOException, BlobStorageException {
        ensureIndexExists();
        return blobTree.size() == 0 ? null : blobTree.lastKey();
    }

    @Override
    public int nextBlobId(int blobId) throws BlobStorageException, IOException {
        ensureIndexExists();
        byte[] blob = getBlob(blobId);
        Map.Entry<byte[], Integer> entry = blobTree.higherEntry(blob);
        if (entry == null) return -1;
        return entry.getValue();
    }

    @Override
    public int previousBlobId(int blobId) throws BlobStorageException, IOException {
        ensureIndexExists();
        byte[] blob = getBlob(blobId);
        Map.Entry<byte[], Integer> entry = blobTree.higherEntry(blob);
        if (entry == null) return -1;
        return entry.getValue();
    }

    @Override
    public byte[] nextBlob(@NonNull byte[] blob) throws IOException, BlobStorageException {
        ensureIndexExists();
        return blobTree.higherKey(blob);
    }

    @Override
    public byte[] previousBlob(@NonNull byte[] blob) throws IOException, BlobStorageException {
        ensureIndexExists();
        return blobTree.lowerKey(blob);
    }
}
