package se.yarin.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.OpenOption;
import java.nio.file.Path;

public interface BlobChannel {
    static BlobChannel open(Path path, OpenOption... openOptions) throws IOException {
        return BufferedBlobChannel.open(path, openOptions);
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

    int append(ByteBuffer buf) throws IOException;

    int write(long offset, ByteBuffer buf) throws IOException;

    void insert(long offset, long noBytes) throws IOException;

    void close() throws IOException;

    long size();
}
