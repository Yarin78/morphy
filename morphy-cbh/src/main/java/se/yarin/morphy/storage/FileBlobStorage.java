package se.yarin.morphy.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.exceptions.MorphyException;
import se.yarin.morphy.exceptions.MorphyIOException;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.util.BlobChannel;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.OpenOption;
import java.util.Set;

import static java.nio.file.StandardOpenOption.*;
import static se.yarin.morphy.storage.BlobStorageHeader.DEFAULT_SERIALIZED_HEADER_SIZE;

public class FileBlobStorage implements BlobStorage {
    private static final Logger log = LoggerFactory.getLogger(FileBlobStorage.class);

    private static final int DEFAULT_PREFETCH_SIZE = 4096;

    private BlobStorageHeader header;
    private final BlobSizeRetriever blobSizeRetriever;
    private final File file;
    private final BlobChannel channel;
    private final boolean strict;

    public FileBlobStorage(@NotNull File file, @NotNull BlobSizeRetriever blobSizeRetriever, @NotNull Set<OpenOption> options)
            throws IOException, MorphyInvalidDataException {
        if (options.contains(WRITE) && options.contains(MorphyOpenOption.IGNORE_NON_CRITICAL_ERRORS)) {
            throw new IllegalArgumentException("A storage open in WRITE mode can't also ignore errors");
        }

        this.file = file;
        this.blobSizeRetriever = blobSizeRetriever;
        this.channel = BlobChannel.open(file.toPath(), MorphyOpenOption.valid(options));
        this.strict = !options.contains(MorphyOpenOption.IGNORE_NON_CRITICAL_ERRORS);

        if (this.channel.size() == 0) {
            if (options.contains(CREATE) || options.contains(CREATE_NEW)) {
                // The storage was presumably created
                putHeader(BlobStorageHeader.empty());
            } else {
                throw new IllegalStateException("File was empty");
            }
        } else {
            refreshHeader();
        }
    }

    @VisibleForTesting
    BlobChannel getChannel() {
        return channel;
    }

    private void refreshHeader() throws IOException, MorphyInvalidDataException {
        ByteBuffer buf = ByteBuffer.allocate(DEFAULT_SERIALIZED_HEADER_SIZE);
        channel.read(0, buf);
        buf.position(0); // No flip since serializer expects buf to be of length serializedHeaderSize()
        BlobStorageHeader header = BlobStorageHeader.deserialize(buf);
        header.validate(strict, file.getName(), channel.size());
        this.header = header;
    }

    @Override
    public @NotNull BlobStorageHeader getHeader() {
        return this.header;
    }

    @Override
    public long getSize() {
        return this.channel.size();
    }

    @Override
    public void putHeader(@NotNull BlobStorageHeader header) {
        ByteBuffer buf = header.serialize();

        try {
            channel.write(0, buf);
        } catch (IOException e) {
            throw new MorphyIOException(e);
        }
        this.header = header;
    }

    @Override
    public @NotNull ByteBuffer getBlob(long offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }

        try {
            ByteBuffer buf = ByteBuffer.allocate(DEFAULT_PREFETCH_SIZE);
            channel.read(offset, buf);
            buf.position(0);
            int blobSize = blobSizeRetriever.getBlobSize(buf);
            if (blobSize > DEFAULT_PREFETCH_SIZE) {
                buf = ByteBuffer.allocate(blobSize);
                channel.read(offset, buf);
                buf.position(0);
            } else {
                buf.limit(blobSize);
            }
            return buf;
        } catch (IOException e) {
            throw new MorphyIOException("Failed to get blob at offset " + offset + " in " + file.getName());
        }
    }

    @Override
    public long appendBlob(@NotNull ByteBuffer blob) {
        long offset = getSize();
        putBlob(offset, blob);
        return offset;
    }

    @Override
    public void putBlob(long offset, @NotNull ByteBuffer blob) {
        if (offset > getSize()) {
            throw new MorphyIOException(String.format("Tried to put blob at offset %d but file size was %d",
                    offset, getSize()));
        }
        try {
            channel.write(offset, blob);
            putHeader(BlobStorageHeader.of(this.header.headerSize(), getSize()));
        } catch (IOException e) {
            throw new MorphyIOException(e);
        }
    }

    @Override
    public void insert(long offset, long noBytes) {
        try {
            channel.insert(offset, noBytes);
        } catch (IOException e) {
            throw new MorphyIOException("Failed to insert " + noBytes + " in blob " + file.getName());
        }
        putHeader(BlobStorageHeader.of(this.header.headerSize(), getSize()));
    }

    @Override
    public void close() throws MorphyException {
        try {
            channel.close();
        } catch (IOException e) {
            throw new MorphyIOException("Failed to close storage", e);
        }
    }
}
