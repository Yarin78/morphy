package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

public class PersistentGameHeaderStorage extends GameHeaderStorageBase {
    private static final Logger log = LoggerFactory.getLogger(PersistentGameHeaderStorage.class);

    private int storageHeaderSize, serializedGameHeaderSize;
    private final GameHeaderSerializer serializer;
    private final String storageName;
    private FileChannel channel;

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

    static void createEmptyStorage(File file, int headerSize)
            throws IOException {
        if (headerSize < 44) {
            throw new IllegalArgumentException("The size of the header must be at least 44 bytes");
        }
        FileChannel channel = FileChannel.open(file.toPath(), CREATE_NEW, READ, WRITE);
        GameHeaderStorageMetadata metadata = new GameHeaderStorageMetadata();
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

    private static ByteBuffer serializeMetadata(GameHeaderStorageMetadata metadata) {
        ByteBuffer buffer = ByteBuffer.allocate(metadata.getStorageHeaderSize());

        ByteBufferUtil.putByte(buffer, metadata.getUnknownByte1());
        ByteBufferUtil.putShortB(buffer, metadata.getStorageHeaderSize());
        ByteBufferUtil.putShortB(buffer, metadata.getGameHeaderSize());
        ByteBufferUtil.putByte(buffer, metadata.getUnknownByte2());
        ByteBufferUtil.putIntB(buffer, metadata.getNextGameId());
        for (int i = 0; i < 15; i++) {
            ByteBufferUtil.putShortB(buffer, metadata.getGameHeaderSize());
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
//        channel.position(storageHeaderSize + (gameId - 1) * (serializedGameHeaderSize));
        channel.position(46 + (gameId - 1) * serializedGameHeaderSize);
    }

    @Override
    GameHeader get(int id) throws IOException {
        positionChannel(id);
        ByteBuffer buf = ByteBuffer.allocate(serializedGameHeaderSize);
        channel.read(buf);
        buf.position(0);

        GameHeader gameHeader = serializer.deserialize(id, buf);

        if (log.isTraceEnabled()) {
            log.trace("Read game header " + id);
        }

        return gameHeader;
    }

    void put(GameHeader gameHeader) throws IOException {
        positionChannel(gameHeader.getId());
        ByteBuffer src = serializer.serialize(gameHeader);
        src.position(0);
        channel.write(src);

        if (log.isDebugEnabled()) {
            log.debug(String.format("Successfully put game header %d to %s", gameHeader.getId(), storageName));
        }
    }

    @Override
    void close() throws IOException {
        channel.close();
    }
}
