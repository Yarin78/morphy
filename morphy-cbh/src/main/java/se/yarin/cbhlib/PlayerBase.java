package se.yarin.cbhlib;

import lombok.NonNull;
import se.yarin.cbhlib.entities.EntityStorage;
import se.yarin.cbhlib.entities.EntityStorageImpl;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class PlayerBase extends EntityBase<PlayerEntity> {

    private int SERIALIZED_PLAYER_SIZE = 58;

    /**
     * Creates a new in-memory player database that is initially empty.
     */
    public PlayerBase() {
        super(EntityStorageImpl.createInMemory());
    }

    private PlayerBase(@NonNull EntityStorage<PlayerEntity> storage) {
        super(storage);
    }

    /**
     * Creates an in-memory player base initially populated with the data from the file
     * @param file the initial data of the database
     * @return an in-memory player base
     */
    public static PlayerBase openInMemory(@NonNull File file) throws IOException {
        return new PlayerBase(loadInMemoryStorage(file, new PlayerBase()));
    }

    /**
     * Opens a player database from disk
     * @param file the player databases to open
     * @return the opened player database
     * @throws IOException if something went wrong when opening the database
     */
    public static PlayerBase open(@NonNull File file) throws IOException {
        return new PlayerBase(EntityStorageImpl.open(file, new PlayerBase()));
    }

    /**
     * Creates a new player database on disk.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @return the opened player database
     * @throws IOException if something went wrong when creating the database
     */
    public static PlayerBase create(@NonNull File file) throws IOException {
        return new PlayerBase(EntityStorageImpl.create(file, new PlayerBase()));
    }

    public ByteBuffer serialize(@NonNull PlayerEntity player) {
        ByteBuffer buf = ByteBuffer.allocate(SERIALIZED_PLAYER_SIZE);
        ByteBufferUtil.putFixedSizeByteString(buf, player.getLastName(), 30);
        ByteBufferUtil.putFixedSizeByteString(buf, player.getFirstName(), 20);
        ByteBufferUtil.putIntL(buf, player.getCount());
        ByteBufferUtil.putIntL(buf, player.getFirstGameId());
        return buf;
    }

    public PlayerEntity deserialize(int entityId, @NonNull ByteBuffer buf) {
        return PlayerEntity.builder()
            .id(entityId)
            .lastName(ByteBufferUtil.getFixedSizeByteString(buf, 30))
            .firstName(ByteBufferUtil.getFixedSizeByteString(buf, 20))
            .count(ByteBufferUtil.getIntL(buf))
            .firstGameId(ByteBufferUtil.getIntL(buf))
            .build();
    }

    public int getSerializedEntityLength() {
        return SERIALIZED_PLAYER_SIZE;
    }
}
