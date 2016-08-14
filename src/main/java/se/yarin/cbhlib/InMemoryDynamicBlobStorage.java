package se.yarin.cbhlib;

import lombok.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;

public class InMemoryDynamicBlobStorage implements DynamicBlobStorage {

    private ByteBuffer data;
    private BlobSizeRetriever blobSizeRetriever;

    InMemoryDynamicBlobStorage(
            @NonNull BlobSizeRetriever blobSizeRetriever) {
        this((ByteBuffer) ByteBuffer.allocate(32).limit(0), blobSizeRetriever);
    }

    InMemoryDynamicBlobStorage(
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
    public ByteBuffer getBlob(int offset) {
        data.position(offset);
        byte[] result = new byte[blobSizeRetriever.getBlobSize(data)];
        data.get(result);
        return ByteBuffer.wrap(result);
    }

    @Override
    public int addBlob(@NonNull ByteBuffer blob) {
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
    public int putBlob(int oldOffset, @NonNull ByteBuffer blob) {
        data.position(oldOffset);
        int oldSize = blobSizeRetriever.getBlobSize(data);
        int newSize = blobSizeRetriever.getBlobSize(blob);
        if (newSize > oldSize) {
            return addBlob(blob);
        }
        data.put(blob);
        return oldOffset;
    }

    @Override
    public int getSize() {
        return data.limit();
    }

    @Override
    public void close() throws IOException { }
}
