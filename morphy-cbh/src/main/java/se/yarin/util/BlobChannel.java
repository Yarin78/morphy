package se.yarin.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Set;

public interface BlobChannel {
    static BlobChannel open(Path path, OpenOption... openOptions) throws IOException {
        return PagedBlobChannel.open(path, openOptions);
    }

    static BlobChannel open(Path path, Set<? extends OpenOption> openOptions) throws IOException {
        return PagedBlobChannel.open(path, openOptions);
    }

    void setChunkSize(int chunkSize);

    /**
     * Reads binary data at a given position
     * @param offset the offset to start read data from
     * @param length number of bytes to read
     * @return a ByteBuffer that contains the read data. position will be 0 and limit will be number of bytes read,
     * which may be less than length if end of file was reached
     */
    ByteBuffer read(long offset, int length) throws IOException;

    /**
     * Reads binary data at a given position into a buffer.
     * @param offset the offset to start read data from
     * @param buffer an existing buffer that the data is read to.
     */
    void read(long offset, ByteBuffer buffer) throws IOException;

    int append(ByteBuffer buf) throws IOException;

    int write(long offset, ByteBuffer buf) throws IOException;

    void insert(long offset, long noBytes) throws IOException;

    void close() throws IOException;

    long size();
}
