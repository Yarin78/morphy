package se.yarin.cbhlib;

import lombok.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class PlayerBase extends EntityBase<PlayerEntity> {

    private int SERIALIZED_PLAYER_SIZE = 58;

    /**
     * Creates a new in-memory player database that is initially empty.
     */
    public PlayerBase() {
        super(new OrderedEntityStorageImpl<>(new InMemoryEntityStorage<>()));
    }

    private PlayerBase(@NonNull OrderedEntityStorageImpl<PlayerEntity> storage) {
        super(storage);
    }

    /**
     * Creates an in-memory player base initially populated with the data from the file
     * @param file the initial data of the database
     * @return an in-memory player base
     */
    public static PlayerBase openInMemory(@NonNull File file) throws IOException, EntityStorageException {
        OrderedEntityStorageImpl<PlayerEntity> outputStorage = loadInMemoryStorage(file, new PlayerBase());
        return new PlayerBase(outputStorage);
    }

    /**
     * Opens a player database from disk
     * @param file the player databases to open
     * @return the opened player database
     * @throws IOException if something went wrong when opening the database
     */
    public static PlayerBase open(@NonNull File file) throws IOException {
        FileEntityStorage<PlayerEntity> storage = FileEntityStorage.open(file, new PlayerBase());
        return new PlayerBase(new OrderedEntityStorageImpl<>(storage));
    }

    /**
     * Creates a new player database on disk.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @return the opened player database
     * @throws IOException if something went wrong when creating the databaes
     */
    public static PlayerBase create(@NonNull File file) throws IOException {
        FileEntityStorage<PlayerEntity> storage = FileEntityStorage.create(file, new PlayerBase());
        return new PlayerBase(new OrderedEntityStorageImpl<>(storage));
    }

    public ByteBuffer serialize(@NonNull PlayerEntity player) {
        ByteBuffer buf = ByteBuffer.allocate(SERIALIZED_PLAYER_SIZE);
        ByteBufferUtil.putByteString(buf, player.getLastName(), 30);
        ByteBufferUtil.putByteString(buf, player.getFirstName(), 20);
        ByteBufferUtil.putIntB(buf, player.getNoGames());
        ByteBufferUtil.putIntB(buf, player.getFirstGameId());
        return buf;
    }

    public PlayerEntity deserialize(int entityId, @NonNull ByteBuffer buf) {
        String last = ByteBufferUtil.getFixedSizeByteString(buf, 30);
        String first = ByteBufferUtil.getFixedSizeByteString(buf, 20);
        int noGames = ByteBufferUtil.getIntB(buf);
        int firstGame = ByteBufferUtil.getIntB(buf);
        return new PlayerEntity(entityId, last, first, noGames, firstGame);
    }

    public int getSerializedEntityLength() {
        return SERIALIZED_PLAYER_SIZE;
    }
}
