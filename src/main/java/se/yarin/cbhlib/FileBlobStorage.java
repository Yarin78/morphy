package se.yarin.cbhlib;

import lombok.Data;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;

import static java.nio.file.StandardOpenOption.*;

public class FileBlobStorage extends OrderedBlobStorage {
    private static final Logger log = LoggerFactory.getLogger(FileBlobStorage.class);

    private final Comparator<byte[]> blobComparator;
    private final int MAGIC_CONSTANT = 1234567890;

    private int capacity;
    private int rootBlobId;
    private int blobSize;
    private int firstDeletedBlobId;
    private int numBlobs;
    private int blobOffset;
    private String storageName;
    private FileChannel channel;

    /**
     * A small structure preceding each blob in the storage, keeping track
     * of which blob comes before and after in the ordered blob storage.
     */
    @Data
    static class BlobHeader {
        // The id of the preceding blob id. If -999, this blob is deleted.
        private int leftBlobId = -1;
        // The id of the succeding blob id. If this blob is deleted, this points to the next deleted blob.
        private int rightBlobId = -1;
        // Height of left tree - height of right tree. Used for balancing the binary tree.
        private int heightDif = 0;

        private boolean isDeleted() {
            return leftBlobId == -999;
        }
    }

    public FileBlobStorage(File file, int keyLength) throws IOException {
        super(keyLength);
        this.storageName = file.getName();
        blobComparator = getBlobComparator(keyLength);

        channel = FileChannel.open(file.toPath(), READ, WRITE);
        ByteBuffer header = ByteBuffer.allocate(32);
        channel.read(header);
        header.position(0);

        capacity = ByteBufferUtil.getIntL(header);
        rootBlobId = ByteBufferUtil.getIntL(header);
        int headerInt = ByteBufferUtil.getIntL(header);
        if (headerInt != MAGIC_CONSTANT) {
            // Not sure what this is!?
            throw new IOException("Invalid header int: " + headerInt);
        }
        blobSize = ByteBufferUtil.getIntL(header);
        firstDeletedBlobId = ByteBufferUtil.getIntL(header);
        numBlobs = ByteBufferUtil.getIntL(header);
        blobOffset = 28 + ByteBufferUtil.getIntL(header);

        log.debug(String.format("Opening %s; capacity = %d, root = %d, numBlobs = %d, firstDeletedId = %d",
                storageName, capacity, rootBlobId, numBlobs, firstDeletedBlobId));
    }

    /**
     * Constructor when creating a new {@link FileBlobStorage}
     * @param file the file to create
     * @param blobSize the size of a blob entry
     * @param keyLength the length of the unique key of each blob entry
     * @throws IOException if something went wrong when creating the storage
     */
    protected FileBlobStorage(File file, int blobSize, int keyLength) throws IOException {
        super(keyLength);
        if (keyLength > blobSize) {
            throw new IllegalArgumentException("keyLength must be less than blobSize");
        }

        channel = FileChannel.open(file.toPath(), CREATE_NEW, READ, WRITE);

        storageName = file.getName();
        blobComparator = getBlobComparator(keyLength);
        capacity = 0;
        rootBlobId = -1;
        this.blobSize = blobSize;
        numBlobs = 0;
        firstDeletedBlobId = -1;
        blobOffset = 32;
        updateStorageHeader();
    }

    public static FileBlobStorage create(File file, int blobSize, int keyLength) throws IOException {
        return new FileBlobStorage(file, blobSize, keyLength);
    }

    private void updateStorageHeader() throws IOException {
        channel.position(0);

        ByteBuffer header = ByteBuffer.allocate(32);
        ByteBufferUtil.putIntL(header, capacity);
        ByteBufferUtil.putIntL(header, rootBlobId);
        ByteBufferUtil.putIntL(header, MAGIC_CONSTANT);
        ByteBufferUtil.putIntL(header, blobSize);
        ByteBufferUtil.putIntL(header, firstDeletedBlobId);
        ByteBufferUtil.putIntL(header, numBlobs);
        ByteBufferUtil.putIntL(header, blobOffset - 28);

        header.position(0);
        channel.write(header);

        log.debug(String.format("Updated %s; capacity = %d, root = %d, numBlobs = %d, firstDeletedId = %d",
                storageName, capacity, rootBlobId, numBlobs, firstDeletedBlobId));
    }

    /**
     * Positions the channel at the start of the specified blobId.
     * Valid positions are between 0 and capacity (to allow for adding new blobs)
     * @param blobId the blobId to position to channel against
     * @throws IOException
     */
    private void positionChannel(int blobId) throws IOException {
        channel.position(blobOffset + blobId * (9 + blobSize));
    }

    /**
     * Gets the blob header for the specified blob id.
     * The channel position is updated and points to the blob data.
     * @param blobId the id of the blob to get the header for
     * @return the blob header
     * @throws IOException if some IO error occurred
     * @throws BlobStorageException if the blobId was outside the capacity
     */
    private BlobHeader getBlobHeader(int blobId) throws IOException, BlobStorageException {
        if (blobId < 0 || blobId >= capacity) {
            throw new BlobStorageException("Invalid blob id " + blobId + "; capacity is " + capacity);
        }

        positionChannel(blobId);
        ByteBuffer headerBuf = ByteBuffer.allocate(9);
        channel.read(headerBuf);
        headerBuf.position(0);

        BlobHeader header = new BlobHeader();
        header.leftBlobId = ByteBufferUtil.getIntL(headerBuf);
        header.rightBlobId = ByteBufferUtil.getIntL(headerBuf);
        header.heightDif = ByteBufferUtil.getSignedByte(headerBuf);

        if (log.isTraceEnabled()) {
            log.trace("Blob id " + blobId + " in " + storageName + " has header " + header);
        }

        return header;
    }

    private void putBlobHeader(int blobId, BlobHeader header) throws BlobStorageException, IOException {
        if (blobId < 0 || blobId > capacity) {
            throw new BlobStorageException("Can't write blob header with id " + blobId + "; capacity is " + capacity);
        }

        positionChannel(blobId);
        ByteBuffer headerBuf = ByteBuffer.allocate(9);
        ByteBufferUtil.putIntL(headerBuf, header.getLeftBlobId());
        ByteBufferUtil.putIntL(headerBuf, header.getRightBlobId());
        ByteBufferUtil.putByte(headerBuf, header.getHeightDif());

        headerBuf.position(0);
        channel.write(headerBuf);
    }

    @Override
    public int getNumBlobs() {
        return numBlobs;
    }

    @Override
    public byte[] getBlob(int blobId) throws BlobStorageException, IOException {
        BlobHeader blobHeader = getBlobHeader(blobId);
        if (blobHeader.isDeleted()) {
            throw new BlobStorageException("There is no blob with id " + blobId);
        }
        ByteBuffer blob = ByteBuffer.allocate(blobSize);
        channel.read(blob);
        return blob.array();
    }

    @Override
    public byte[][] getAllBlobs() throws IOException, BlobStorageException {
        byte[][] allBlobs = new byte[capacity][];
        for (int id = 0; id < capacity; id++) {
            BlobHeader blobHeader = getBlobHeader(id);
            if (!blobHeader.isDeleted()) {
                ByteBuffer blob = ByteBuffer.allocate(blobSize);
                channel.read(blob);
                allBlobs[id] = blob.array();
            }
        }
        return allBlobs;
    }

    public int firstDeletedBlobId() throws IOException, BlobStorageException {
        return firstDeletedBlobId;
    }

    public int nextDeletedBlobId(int blobId) throws IOException, BlobStorageException {
        BlobHeader blobHeader = getBlobHeader(blobId);
        return blobHeader.rightBlobId;
    }

    public int getInsertId() {
        return firstDeletedBlobId >= 0 ? firstDeletedBlobId : capacity;
    }

    @Override
    public int addBlob(@NonNull byte[] blob) throws BlobStorageException, IOException {
        if (blob.length > blobSize) {
            throw new IllegalArgumentException("Blob size " + blob.length + " exceeded the storage blob size " + blobSize);
        }
        int blobId = getInsertId();
        putBlobHeader(blobId, new BlobHeader());
        channel.write(ByteBuffer.wrap(blob));
        if (blobId == firstDeletedBlobId) {
            firstDeletedBlobId = nextDeletedBlobId(firstDeletedBlobId);
        }
        if (blobId == capacity) {
            capacity++;
        }
        numBlobs++;
        log.debug("Successfully added new blob to " + storageName + " that received id " + blobId);
        updateStorageHeader();
        return blobId;
    }

    @Override
    public void putBlob(int blobId, @NonNull byte[] blob) throws BlobStorageException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBlob(int blobId) throws BlobStorageException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int firstId() throws BlobStorageException, IOException {
        return searchNextBlob(rootBlobId, getFirstKeySentinel());
    }

    @Override
    public int lastId() throws BlobStorageException, IOException {
        return searchPreviousBlob(rootBlobId, getLastKeySentinel());
    }

    @Override
    public byte[] firstBlob() throws BlobStorageException, IOException {
        int id = firstId();
        return id < 0 ? null : getBlob(id);
    }

    @Override
    public byte[] lastBlob() throws BlobStorageException, IOException {
        int id = lastId();
        return id < 0 ? null : getBlob(id);
    }

    @Override
    public int nextBlobId(int blobId) throws BlobStorageException, IOException {
        return searchNextBlob(rootBlobId, getBlob(blobId));
    }

    @Override
    public int previousBlobId(int blobId) throws BlobStorageException, IOException {
        return searchPreviousBlob(rootBlobId, getBlob(blobId));
    }

    @Override
    public byte[] nextBlob(@NonNull byte[] blob) throws BlobStorageException, IOException {
        int id = searchNextBlob(rootBlobId, blob);
        return id < 0 ? null : getBlob(id);
    }

    @Override
    public byte[] previousBlob(@NonNull byte[] blob) throws BlobStorageException, IOException {
        int id = searchPreviousBlob(rootBlobId, blob);
        return id < 0 ? null : getBlob(id);
    }


    /**
     * Finds the first blob strictly greater than blob from a specific point in the tree
     * @param id the current node in the tree
     * @param blob the blob to find the successor to
     * @return the id of the successor blob
     * @throws BlobStorageException
     * @throws IOException
     */
    private int searchNextBlob(int id, @NonNull byte[] blob) throws BlobStorageException, IOException {
        byte[] currentBlob = getBlob(id);
        BlobHeader header = getBlobHeader(id);
        int dif = blobComparator.compare(currentBlob, blob);
        if (dif <= 0) {
            if (header.rightBlobId < 0) {
                return -1;
            }
            return searchNextBlob(header.rightBlobId, blob);
        }
        if (header.leftBlobId < 0) {
            return id;
        }
        int leftCeilingId = searchNextBlob(header.leftBlobId, blob);
        return leftCeilingId >= 0 ? leftCeilingId : id;
    }

    /**
     * Finds the first blob strictly lesser than blob from a specific point in the tree
     * @param id the current node in the tree
     * @param blob the blob to find the predecessor to
     * @return the id of the predecessor blob
     * @throws BlobStorageException
     * @throws IOException
     */
    private int searchPreviousBlob(int id, @NonNull byte[] blob) throws BlobStorageException, IOException {
        byte[] currentBlob = getBlob(id);
        BlobHeader header = getBlobHeader(id);
        int dif = blobComparator.compare(currentBlob, blob);
        if (dif >= 0) {
            if (header.leftBlobId < 0) {
                return -1;
            }
            return searchPreviousBlob(header.leftBlobId, blob);
        }
        if (header.rightBlobId < 0) {
            return id;
        }
        int rightCeilingId = searchPreviousBlob(header.rightBlobId, blob);
        return rightCeilingId >= 0 ? rightCeilingId : id;
    }
}
