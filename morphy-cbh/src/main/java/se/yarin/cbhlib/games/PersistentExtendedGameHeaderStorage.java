package se.yarin.cbhlib.games;

import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

public class PersistentExtendedGameHeaderStorage extends ExtendedGameHeaderStorageBase {
    private static final Logger log = LoggerFactory.getLogger(PersistentGameHeaderStorage.class);

    private static final int FILE_HEADER_SIZE = 32;

    private int serializedExtendedGameHeaderSize;
    private final ExtendedGameHeaderSerializer serializer;
    private final String storageName;
    private FileChannel channel;

    @Getter
    private int version = 0;

    PersistentExtendedGameHeaderStorage(@NonNull File file, @NonNull ExtendedGameHeaderSerializer serializer)
            throws IOException {
        super(loadMetadata(file));

        this.serializedExtendedGameHeaderSize = getMetadata().getSerializedExtendedGameHeaderSize();
        this.storageName = file.getName();
        this.serializer = serializer;
        channel = FileChannel.open(file.toPath(), READ, WRITE);

        log.debug(String.format("Opening %s; number of extended game headers = %d",
                storageName, getMetadata().getNumHeaders()));
    }

    static void createEmptyStorage(File file, ExtendedGameHeaderStorageMetadata metadata)
            throws IOException {
        FileChannel channel = FileChannel.open(file.toPath(), CREATE_NEW, READ, WRITE);
        channel.write(serializeMetadata(metadata));
        channel.close();
    }

    public static ExtendedGameHeaderStorageBase open(@NonNull File file, @NonNull ExtendedGameHeaderSerializer serializer )
            throws IOException {
        return new PersistentExtendedGameHeaderStorage(file, serializer);
    }

    private static ExtendedGameHeaderStorageMetadata loadMetadata(File file) throws IOException {
        try (FileChannel channel = FileChannel.open(file.toPath(), READ)) {
            channel.position(0);
            ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_SIZE);
            channel.read(header);
            header.position(0);

            ExtendedGameHeaderStorageMetadata metadata = new ExtendedGameHeaderStorageMetadata();

            metadata.setVersion(ByteBufferUtil.getIntL(header));
            metadata.setSerializedExtendedGameHeaderSize(ByteBufferUtil.getIntL(header));
            metadata.setNumHeaders(ByteBufferUtil.getIntL(header));

            // By all accounts these are just random trash bytes,
            // but keep them around just in case they would actually contain something important
            byte[] fillerBytes = new byte[FILE_HEADER_SIZE - 12];
            header.get(fillerBytes);
            metadata.setFillers(fillerBytes);

            return metadata;
        }
    }

    @Override
    void setMetadata(ExtendedGameHeaderStorageMetadata metadata) throws IOException {
        // Update the in-memory metadata cache as well
        super.setMetadata(metadata);

        ByteBuffer buffer = serializeMetadata(metadata);

        channel.position(0);
        channel.write(buffer);
        channel.force(false);

        log.debug(String.format("Updated %s; numHeaders = %d", storageName, metadata.getNumHeaders()));
    }

    private static ByteBuffer serializeMetadata(ExtendedGameHeaderStorageMetadata metadata) {
        ByteBuffer buffer = ByteBuffer.allocate(FILE_HEADER_SIZE);

        ByteBufferUtil.putIntB(buffer, metadata.getVersion());
        ByteBufferUtil.putIntB(buffer, metadata.getSerializedExtendedGameHeaderSize());
        ByteBufferUtil.putIntB(buffer, metadata.getNumHeaders());

        if (metadata.getFillers() != null) {
            buffer.put(metadata.getFillers());
        } else {
            buffer.put(new byte[FILE_HEADER_SIZE - 12]);
        }
        buffer.flip();
        return buffer;
    }

    /**
     * Positions the channel at the start of the specified gameId.
     * Valid positions are between 1 and numHeaders+1
     * @param gameId the gameId (1-indexed) to position to channel against
     * @throws IOException if an IO error occurs
     */
    private void positionChannel(int gameId) throws IOException {
        channel.position(FILE_HEADER_SIZE + (gameId-1) * serializedExtendedGameHeaderSize);
    }

    @Override
    ExtendedGameHeader get(int id) throws IOException {
        positionChannel(id);
        ByteBuffer buf = ByteBuffer.allocate(serializedExtendedGameHeaderSize);
        channel.read(buf);
        buf.flip();
        if (!buf.hasRemaining()) {
            return null;
        }

        try {
            ExtendedGameHeader extendedGameHeader = serializer.deserialize(id, buf);

            if (log.isTraceEnabled()) {
                log.trace("Read extended game header " + id);
            }

            return extendedGameHeader;
        } catch (BufferUnderflowException e) {
            log.warn(String.format("Unexpected end of file reached when reading extended game header %d", id), e);
            return null;
        }
    }

    @Override
    List<ExtendedGameHeader> getRange(int startId, int endId) throws IOException {
        if (startId < 1) throw new IllegalArgumentException("startId must be 1 or greater");
        int count = endId - startId;
        ArrayList<ExtendedGameHeader> result = new ArrayList<>(count);

        positionChannel(startId);
        ByteBuffer buf = ByteBuffer.allocate(serializedExtendedGameHeaderSize * count);
        channel.read(buf);
        buf.flip();

        for (int id = startId; id < endId && buf.hasRemaining(); id++) {
            byte[] gameHeaderBuf = new byte[serializedExtendedGameHeaderSize];
            try {
                buf.get(gameHeaderBuf);
            } catch (BufferUnderflowException e) {
                log.warn(String.format("Unexpected end of file reached when reading game headers in range [%d, %d)", startId, endId), e);
                break;
            }
            result.add(serializer.deserialize(id, ByteBuffer.wrap(gameHeaderBuf)));
        }

        return result;
    }

    void put(ExtendedGameHeader extendedGameHeader) throws IOException {
        int gameId = extendedGameHeader.getId();
        if (gameId < 1 || gameId > getMetadata().getNumHeaders() + 1) {
            throw new IllegalArgumentException(String.format("gameId outside range (was %d, numHeaders is %d)", gameId, getMetadata().getNumHeaders()));
        }
        positionChannel(gameId);
        ByteBuffer src = serializer.serialize(extendedGameHeader);
        src.position(0);
        channel.write(src);

        version++;

        if (log.isDebugEnabled()) {
            log.debug(String.format("Successfully put game header %d to %s", gameId, storageName));
        }
    }

    @Override
    void close() throws IOException {
        channel.close();
    }
}
