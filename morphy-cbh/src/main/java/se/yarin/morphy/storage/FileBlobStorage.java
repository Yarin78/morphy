package se.yarin.morphy.storage;

import org.jetbrains.annotations.NotNull;
import se.yarin.cbhlib.exceptions.ChessBaseIOException;
import se.yarin.morphy.exceptions.MorphyException;
import se.yarin.morphy.exceptions.MorphyIOException;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.exceptions.MorphyNotSupportedException;

import java.io.File;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.util.Set;

import static java.nio.file.StandardOpenOption.*;

public class FileBlobStorage<THeader, TBlob> implements BlobStorage<THeader, TBlob> {
    private long fileSize;
    private THeader header;
    private final File file;
    private final FileChannel channel;
    private final BlobStorageSerializer<THeader, TBlob> serializer;
    private final boolean strict;

    public FileBlobStorage(
            @NotNull File file,
            @NotNull BlobStorageSerializer<THeader, TBlob> serializer,
            @NotNull THeader emptyHeader,
            @NotNull Set<OpenOption> options)
            throws IOException, MorphyInvalidDataException {

        if (options.contains(WRITE) && options.contains(MorphyOpenOption.IGNORE_NON_CRITICAL_ERRORS)) {
            throw new IllegalArgumentException("A storage open in WRITE mode can't also ignore errors");
        }

        this.file = file;
        this.channel = FileChannel.open(file.toPath(), MorphyOpenOption.valid(options));
        this.strict = !options.contains(MorphyOpenOption.IGNORE_NON_CRITICAL_ERRORS);
        this.serializer = serializer;
        this.fileSize = this.channel.size();

        if (this.fileSize == 0) {
            if (options.contains(CREATE) || options.contains(CREATE_NEW)) {
                // The storage was presumably created
                putHeader(emptyHeader);
                this.fileSize = this.channel.size();
            } else {
                throw new IllegalStateException("File was empty");
            }
        } else {
            refreshHeader();
            if (this.serializer.serializedHeaderSize() != this.serializer.headerSize(this.header)
                    && options.contains(WRITE)) {
                throw new MorphyNotSupportedException(String.format(
                        "The header in %s was %d bytes (%d bytes expected) and needs to be upgraded which is not yet supported",
                        file, this.serializer.headerSize(this.header), this.serializer.serializedHeaderSize()));
            }
        }
    }

    private void refreshHeader() throws IOException, MorphyInvalidDataException {
        channel.position(0);
        ByteBuffer buf = ByteBuffer.allocate(serializer.serializedHeaderSize());
        channel.read(buf);
        buf.position(0); // No flip since serializer expects buf to be of length prefetchHeaderSize()
        this.header = this.serializer.deserializeHeader(buf);
    }

    @Override
    public @NotNull THeader getHeader() {
        return this.header;
    }

    @Override
    public long getSize() {
        return this.fileSize;
    }

    @Override
    public void putHeader(@NotNull THeader header) {
        ByteBuffer buf = ByteBuffer.allocate(this.serializer.serializedHeaderSize());
        this.serializer.serializeHeader(header, buf);
        buf.flip();
        try {
            channel.position(0);
            channel.write(buf);
        } catch (IOException e) {
            throw new MorphyIOException(e);
        }
        this.header = header;
    }

    @Override
    public @NotNull TBlob getBlob(long offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }

        try {
            ByteBuffer buf = ByteBuffer.allocate(serializer.prefetchSize());
            channel.position(offset);
            channel.read(buf);
            buf.position(0);
            int blobSize = serializer.getBlobSize(buf);
            if (blobSize > serializer.prefetchSize()) {
                buf = ByteBuffer.allocate(blobSize);
                channel.position(offset);
                channel.read(buf);
                buf.position(0);
            }
            return serializer.deserializeBlob(buf);
        } catch (IOException e) {
            throw new ChessBaseIOException("Failed to get blob at offset " + offset + " in " + file.getName());
        }
    }

    @Override
    public long appendBlob(@NotNull TBlob blob) {
        long offset = fileSize;
        putBlob(offset, blob);
        return offset;
    }

    @Override
    public void putBlob(long offset, @NotNull TBlob blob) {
        if (offset > this.fileSize) {
            throw new MorphyIOException(String.format("Tried to put blob at offset %d but file size was %d",
                    offset, this.fileSize));
        }
        ByteBuffer buf = ByteBuffer.allocate(16384);
        while (true) {
            try {
                serializer.serializeBlob(blob, buf);
                break;
            } catch (BufferOverflowException e) {
                buf = ByteBuffer.allocate(buf.capacity() * 2);
            }
        }
        buf.flip();
        try {
            channel.position(offset);
            channel.write(buf);
        } catch (IOException e) {
            throw new MorphyIOException(e);
        }
        this.fileSize = Math.max(this.fileSize, offset + buf.position());
    }

    @Override
    public void insert(long offset, long noBytes) {
        /*
        try {
            channel.insert(offset, noBytes);
        } catch (IOException e) {
            throw new ChessBaseIOException("Failed to insert " + noBytes + " in blob " + file.getName());
        }

         */
        throw new MorphyNotSupportedException();
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
