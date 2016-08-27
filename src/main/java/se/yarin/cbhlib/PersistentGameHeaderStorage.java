package se.yarin.cbhlib;

import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class PersistentGameHeaderStorage extends GameHeaderStorageBase {
    private static final Logger log = LoggerFactory.getLogger(PersistentGameHeaderStorage.class);

    private int storageHeaderSize, serializedGameHeaderSize;
    private final GameHeaderSerializer serializer;
    private final String storageName;
    private FileChannel channel;

    @Getter
    private int version = 0;

    PersistentGameHeaderStorage(@NonNull File file, @NonNull GameHeaderSerializer serializer)
            throws IOException {
        super(loadMetadata(file));

        this.serializedGameHeaderSize = getMetadata().getGameHeaderSize();
        this.storageHeaderSize = getMetadata().getStorageHeaderSize();
        this.storageName = file.getName();
        this.serializer = serializer;
        channel = FileChannel.open(file.toPath(), READ, WRITE);

        log.debug(String.format("Opening %s; next game id = %d",
                storageName, getMetadata().getNextGameId()));
    }

    static void createEmptyStorage(File file, GameHeaderStorageMetadata metadata)
            throws IOException {
        FileChannel channel = FileChannel.open(file.toPath(), CREATE_NEW, READ, WRITE);
        channel.write(serializeMetadata(metadata));
        channel.close();
    }

    public static GameHeaderStorageBase open(@NonNull File file, @NonNull GameHeaderSerializer serializer )
            throws IOException {
        return new PersistentGameHeaderStorage(file, serializer);
    }

    private static GameHeaderStorageMetadata loadMetadata(File file) throws IOException {
        try (FileChannel channel = FileChannel.open(file.toPath(), READ)) {
            String storageName = file.getName();
            channel.position(0);
            ByteBuffer header = ByteBuffer.allocate(46);
            channel.read(header);
            header.position(0);

            GameHeaderStorageMetadata metadata = new GameHeaderStorageMetadata();

            metadata.setUnknownByte1(ByteBufferUtil.getUnsignedByte(header));
            metadata.setStorageHeaderSize(ByteBufferUtil.getUnsignedShortB(header));
            if (metadata.getStorageHeaderSize() != 0x2C) {
                // This value can be 0x24 on some old databases!?
                log.warn("Storage header size (?) = " + metadata.getStorageHeaderSize() + " in " + storageName);
            }
            metadata.setGameHeaderSize(ByteBufferUtil.getUnsignedShortB(header));
            metadata.setUnknownByte2(ByteBufferUtil.getUnsignedByte(header));

            if (metadata.getUnknownByte1() != 0) {
                log.warn("Unknown header byte 1 = " + metadata.getUnknownByte1() + " in " + storageName);
            }
            if (metadata.getUnknownByte2() != 1) {
                log.warn("Unknown header byte 2 = " + metadata.getUnknownByte2() + " in " + storageName);
            }

            metadata.setNextGameId(ByteBufferUtil.getIntB(header));
            for (int i = 0; i < 15; i++) {
                int value = ByteBufferUtil.getUnsignedShortB(header);
                metadata.getUnknownShort()[i] = value;
                if (value != 0) {
                    /*
                    INFO  ScanAllGameHeaders - Reading /Users/yarin/chessbasemedia/mediafiles/TEXT/Alexei Shirov - My Best Games in the Najdorf (only audio)/My best games in the Sicilian Najdorf.cbh
                    WARN  PersistentGameHeaderStorage - Unknown header short 6 = 12 in My best games in the Sicilian Najdorf.cbh
                    INFO  ScanAllGameHeaders - Reading /Users/yarin/chessbasemedia/mediafiles/TEXT/Andrew Martin - The ABC of the Caro-Kann/Caro-Kann.cbh
                    WARN  PersistentGameHeaderStorage - Unknown header short 6 = 26 in Caro-Kann.cbh
                    INFO  ScanAllGameHeaders - Reading /Users/yarin/chessbasemedia/mediafiles/TEXT/Andrew Martin - The ABC of the King's Indian/The ABC of the King's Indian.cbh
                    WARN  PersistentGameHeaderStorage - Unknown header short 6 = 27 in The ABC of the King's Indian.cbh
                    INFO  ScanAllGameHeaders - Reading /Users/yarin/chessbasemedia/mediafiles/TEXT/Andrew Martin - The ABC of the Ruy Lopez/TheRuyLopez.cbh
                    WARN  PersistentGameHeaderStorage - Unknown header short 6 = 42 in TheRuyLopez.cbh
                    INFO  ScanAllGameHeaders - Reading /Users/yarin/chessbasemedia/mediafiles/TEXT/Andrew Martin - The Basics of Winning Chess/The Basics of Winning Chess.cbh
                    WARN  PersistentGameHeaderStorage - Unknown header short 6 = 27 in The Basics of Winning Chess.cbh
                    INFO  ScanAllGameHeaders - Reading /Users/yarin/chessbasemedia/mediafiles/TEXT/Jacob Aagaard - Queen's Indian Defence/Queen's Indian Defence.cbh
                    WARN  PersistentGameHeaderStorage - Unknown header short 6 = 15 in Queen's Indian Defence.cbh
                    INFO  ScanAllGameHeaders - Reading /Users/yarin/chessbasemedia/mediafiles/TEXT/Karsten Müller - Chess Endgames 1 (only audio)/Grundlagen für Einsteiger - Teil 1 - Fundamentels for beginners - Vol.1.cbh
                    WARN  PersistentGameHeaderStorage - Unknown header short 6 = 86 in Grundlagen für Einsteiger - Teil 1 - Fundamentels for beginners - Vol.1.cbh
                    INFO  ScanAllGameHeaders - Reading /Users/yarin/chessbasemedia/mediafiles/TEXT/Viktor Kortchnoi - My Life for Chess - vol 1/My Life for Chess Vol. 1.cbh
                    WARN  PersistentGameHeaderStorage - Unknown header short 6 = 12 in My Life for Chess Vol. 1.cbh
                    INFO  ScanAllGameHeaders - Reading /Users/yarin/chessbasemedia/mediafiles/TEXT/Viktor Kortchnoi - My Life for Chess - vol 2/My Life for Chess Vol. 2.cbh
                    WARN  PersistentGameHeaderStorage - Unknown header short 6 = 17 in My Life for Chess Vol. 2.cbh
                     */
                    if (i != 6) {
                        // short 4 = 276 in megabase 2014 and megabase 2016
                        log.warn("Unknown header short " + i + " = " + value + " in " + storageName);
                    }
                }
            }

            metadata.setNextGameId2(ByteBufferUtil.getIntB(header));
            if (metadata.getNextGameId() != metadata.getNextGameId2()) {
                // This value can be 0 sometimes on old databases?!
                log.warn(String.format("Second nextGameId didn't match the first one in %s (%d != %d)",
                        storageName, metadata.getNextGameId(), metadata.getNextGameId2()));
            }

            int value = ByteBufferUtil.getUnsignedShortB(header);
            metadata.getUnknownShort()[15] = value;
            if (value != 0) {
                log.warn("Unknown header short " + 15 + " = " + value + " in " + storageName);
            }

            return metadata;
        }
    }

    @Override
    void setMetadata(GameHeaderStorageMetadata metadata) throws IOException {
        // Update the in-memory metadata cache as well
        super.setMetadata(metadata);

        ByteBuffer buffer = serializeMetadata(metadata);

        channel.position(0);
        channel.write(buffer);
        channel.force(false);

        log.debug(String.format("Updated %s; nextGameId = %d, nextGameId2 = %d, gameHeaderSize = %d, storageHeaderSize = %d",
                storageName, metadata.getNextGameId(), metadata.getNextGameId2(),
                metadata.getGameHeaderSize(),
                metadata.getStorageHeaderSize()));
    }

    private static ByteBuffer serializeMetadata(GameHeaderStorageMetadata metadata) {
        ByteBuffer buffer = ByteBuffer.allocate(metadata.getStorageHeaderSize());

        ByteBufferUtil.putByte(buffer, metadata.getUnknownByte1());
        ByteBufferUtil.putShortB(buffer, metadata.getStorageHeaderSize());
        ByteBufferUtil.putShortB(buffer, metadata.getGameHeaderSize());
        ByteBufferUtil.putByte(buffer, metadata.getUnknownByte2());
        ByteBufferUtil.putIntB(buffer, metadata.getNextGameId());
        for (int i = 0; i < 15; i++) {
            ByteBufferUtil.putShortB(buffer, metadata.getUnknownShort()[i]);
        }
        ByteBufferUtil.putIntB(buffer, metadata.getNextGameId2());

        buffer.position(0);
        return buffer;
    }

    /**
     * Positions the channel at the start of the specified entityId.
     * Valid positions are between 1 and nextGameId
     * @param gameId the gameId (1-indexed) to position to channel against
     * @throws IOException if an IO error occurs
     */
    private void positionChannel(int gameId) throws IOException {
        // TODO: storageHeaderSize doesn't seem to be correct, that field is something else
//        channel.position(storageHeaderSize + (gameId - 1) * (serializedGameHeaderSize));
        channel.position(46 + (gameId - 1) * serializedGameHeaderSize);
    }

    @Override
    GameHeader get(int id) throws IOException {
        positionChannel(id);
        ByteBuffer buf = ByteBuffer.allocate(serializedGameHeaderSize);
        channel.read(buf);
        buf.flip();
        if (!buf.hasRemaining()) {
            return null;
        }

        try {
            GameHeader gameHeader = serializer.deserialize(id, buf);

            if (log.isTraceEnabled()) {
                log.trace("Read game header " + id);
            }

            return gameHeader;
        } catch (BufferUnderflowException e) {
            log.warn(String.format("Unexpected end of file reached when reading game header %d", id), e);
            return null;
        }
    }

    @Override
    List<GameHeader> getRange(int startId, int endId) throws IOException {
        if (startId < 1) throw new IllegalArgumentException("startId must be 1 or greater");
        int count = endId - startId;
        ArrayList<GameHeader> result = new ArrayList<>(count);

        positionChannel(startId);
        ByteBuffer buf = ByteBuffer.allocate(serializedGameHeaderSize * count);
        channel.read(buf);
        buf.flip();

        for (int id = startId; id < endId && buf.hasRemaining(); id++) {
            byte[] gameHeaderBuf = new byte[serializedGameHeaderSize];
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

    void put(GameHeader gameHeader) throws IOException {
        int gameId = gameHeader.getId();
        if (gameId < 1 || gameId > getMetadata().getNextGameId()) {
            throw new IllegalArgumentException(String.format("gameId outside range (was %d, nextGameId is %d)", gameId, getMetadata().getNextGameId()));
        }
        positionChannel(gameId);
        ByteBuffer src = serializer.serialize(gameHeader);
        src.position(0);
        channel.write(src);

        version++;

        if (log.isDebugEnabled()) {
            log.debug(String.format("Successfully put game header %d to %s", gameId, storageName));
        }
    }

    @Override
    void adjustMovesOffset(int startGameId, int movesOffset, int insertedBytes) throws IOException {
        final int batchSize = 1000;

        positionChannel(startGameId);

        ByteBuffer buf = ByteBuffer.allocateDirect(batchSize * serializedGameHeaderSize);
        while (startGameId < getMetadata().getNextGameId()) {
            int noGames = Math.min(batchSize, getMetadata().getNextGameId() - startGameId);
            buf.limit(noGames * serializedGameHeaderSize);
            channel.read(buf);
            for (int i = 0; i < noGames; i++) {
                buf.position(i * serializedGameHeaderSize + 1);
                int oldOfs = ByteBufferUtil.getIntB(buf);
                if (oldOfs > movesOffset) {
                    buf.position(i * serializedGameHeaderSize + 1);
                    ByteBufferUtil.putIntB(buf, oldOfs + insertedBytes);
                }
            }
            buf.position(0);

            positionChannel(startGameId);
            channel.write(buf);

            startGameId += noGames;
        }
    }

    @Override
    void close() throws IOException {
        channel.close();
    }
}
