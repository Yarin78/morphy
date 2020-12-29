package se.yarin.cbhlib;

import lombok.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;

public class InMemoryBlobStorage implements BlobStorage {

    private ByteBuffer data;
    private BlobSizeRetriever blobSizeRetriever;

    InMemoryBlobStorage(
            @NonNull BlobSizeRetriever blobSizeRetriever) {
        this((ByteBuffer) ByteBuffer.allocate(32).limit(1), blobSizeRetriever);
    }

    InMemoryBlobStorage(
            @NonNull ByteBuffer data,
            @NonNull BlobSizeRetriever blobSizeRetriever) {
        this.data = data;
        this.blobSizeRetriever = blobSizeRetriever;
    }

    private void grow() {
        int oldPos = data.position();
        int oldLimit = data.limit();
        ByteBuffer newBuffer = ByteBuffer.allocate(data.capacity() * 2);
        data.position(0);
        newBuffer.put(data);
        newBuffer.position(oldPos);
        newBuffer.limit(oldLimit);
        data = newBuffer;
    }

    @Override
    public ByteBuffer readBlob(int offset) {
        data.position(offset);
        byte[] result = new byte[blobSizeRetriever.getBlobSize(data)];
        data.get(result);
        return ByteBuffer.wrap(result);
    }

    @Override
    public int writeBlob(@NonNull ByteBuffer blob) {
        while (data.limit() + blob.limit() > data.capacity()) {
            grow();
        }
        int offset = data.limit();
        data.position(offset);
        data.limit(data.limit() + blob.limit());
        data.put(blob);
        return offset;
    }

    @Override
    public void writeBlob(int offset, @NonNull ByteBuffer blob) {
        while (offset + blob.limit() > data.capacity()) {
            grow();
        }
        data.position(offset);
        data.put(blob);
    }

    @Override
    public int getSize() {
        return data.limit();
    }

    @Override
    public void insert(int offset, int noBytes) {
        while (data.limit() + noBytes > data.capacity()) {
            grow();
        }
        data.limit(data.limit() + noBytes);
        for (int i = data.limit() - 1; i >= offset+noBytes; i--) {
            byte b = data.get(i - noBytes);
            data.put(i, b);
        }
    }

    @Override
    public void close() throws IOException { }
}
