package se.yarin.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class BufferedBlobChannel implements BlobChannel {
    private static final int PAGE_SIZE = 16384;
    private static final int DEFAULT_INSERT_CHUNK_SIZE = 1024*1024;
    private final FileChannel channel;
    private long size; // Should match channel.size()
    private int chunkSize;
    private final SimpleLRUCache<Integer, ByteBuffer> pageCache;

    public BufferedBlobChannel(FileChannel channel) throws IOException {
        this.channel = channel;
        this.size = this.channel.size();
        this.chunkSize = DEFAULT_INSERT_CHUNK_SIZE;
        this.pageCache = new SimpleLRUCache<>(8);
    }

    public static BufferedBlobChannel open(Path path, OpenOption... openOptions) throws IOException {
        return new BufferedBlobChannel(FileChannel.open(path, openOptions));
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public long size() {
        return size;
    }

    private ByteBuffer readPageUncached(int page) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(PAGE_SIZE);
        channel.position((long) page * PAGE_SIZE);
        channel.read(buf);
        buf.flip();
        return buf;
    }

    private List<ByteBuffer> getPages(int firstPage, int lastPage) throws IOException {
        ArrayList<ByteBuffer> pages = new ArrayList<>(lastPage - firstPage + 1);
        for (int page = firstPage; page <= lastPage; page++) {
            ByteBuffer cached = pageCache.get(page);
            if (cached != null) {
                pages.add(cached);
            } else {
                ByteBuffer data = readPageUncached(page);
                pages.add(data);
                pageCache.set(page, data);
            }
        }
        return pages;
    }

    public ByteBuffer read(long offset, int length) throws IOException {
        if (length == 0) {
            return ByteBuffer.allocate(0);
        }

        int startPage = (int) (offset / PAGE_SIZE), lastPage = (int) ((offset + length) / PAGE_SIZE);

        ByteBuffer buf = ByteBuffer.allocate(length);

        List<ByteBuffer> pages = getPages(startPage, lastPage);

        if (pages.size() == 1) {
            ByteBuffer pageBuf = pages.get(0);
            int start = (int) (offset % PAGE_SIZE);
            buf.put(pageBuf.array(), start, Math.max(0, Math.min(pageBuf.limit() - start, length)));
        } else {
            // First page
            ByteBuffer pageBuf = pages.get(0);
            buf.put(pageBuf.array(), (int) (offset % PAGE_SIZE), Math.max(0, (int) (PAGE_SIZE - (offset % PAGE_SIZE))));
            // Intermediate pages, if any
            for (int page = 1; page < pages.size() - 1; page++) {
                buf.put(pages.get(page).array(), 0, PAGE_SIZE);
            }
            // Last page (might be 0 bytes)
            ByteBuffer page = pages.get(pages.size() - 1);
            buf.put(page.array(), 0, (int) ((offset + length) % PAGE_SIZE));
        }

        buf.flip();
        return buf;
    }

    public int append(ByteBuffer buf) throws IOException {
        pageCache.evict((int) (size / PAGE_SIZE));
        return write(size, buf);
    }

    public int write(long offset, ByteBuffer buf) throws IOException {
        channel.position(offset);
        int written = channel.write(buf);
        size = Math.max(size, offset + written);

        int startPage = (int) (offset / PAGE_SIZE), lastPage = (int) ((offset + written - 1) / PAGE_SIZE);
        for (int page = startPage; page <= lastPage; page++) {
            pageCache.evict(page);
        }
        return written;
    }

    public void insert(long offset, long noBytes) throws IOException {
        if (noBytes < 0) {
            throw new IllegalArgumentException("Number of bytes to insert must be non-negative");
        }
        if (noBytes == 0) {
            return;
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(chunkSize);
        long pos = size;
        while (pos > offset) {
            // Invariant: All bytes at position pos and after have been shifted noBytes bytes
            pos -= chunkSize;
            int length = chunkSize;
            if (pos < offset) {
                length -= (offset - pos);
                pos = offset;
            }
            channel.position(pos);
            buf.limit(length);
            channel.read(buf);
            buf.flip();
            channel.position(pos + noBytes);
            channel.write(buf);
            buf.clear();
        }
        size += noBytes;
        pageCache.clear();
    }

    public void close() throws IOException {
        channel.close();
        pageCache.clear();
    }
}
