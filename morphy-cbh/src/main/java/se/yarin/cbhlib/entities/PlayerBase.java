package se.yarin.cbhlib.entities;

import lombok.NonNull;
import se.yarin.cbhlib.storage.TreePath;
import se.yarin.cbhlib.storage.transaction.EntityStorage;
import se.yarin.cbhlib.storage.transaction.EntityStorageImpl;
import se.yarin.cbhlib.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.stream.Stream;

public class PlayerBase extends EntityBase<PlayerEntity> {

    private static final int SERIALIZED_PLAYER_SIZE = 58;

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

    /**
     * Searches for players using a case sensitive prefix search.
     * @param name a prefix of the last name of the player; first name can be specified after a comma
     * @return a stream over matching players
     */
    public Stream<PlayerEntity> prefixSearch(@NonNull String name) {
        if (name.contains(",")) {
            String[] parts = name.split(",", 2);
            return prefixSearch(parts[0].strip(), parts[1].strip());
        }
        return prefixSearch(name, null);
    }

    /**
     * Searches for players using a case sensitive prefix search.
     * If first name is specified, last name will have to match exactly.
     * @param lastName a prefix of the last name of the player
     * @param firstName a prefix of the first name of the player (or null/empty).
     * @return a stream of matching players
     */
    public Stream<PlayerEntity> prefixSearch(@NonNull String lastName, String firstName) {
        PlayerEntity startKey = new PlayerEntity(lastName, firstName == null ? "" : firstName);
        PlayerEntity endKey = firstName == null ? new PlayerEntity(lastName + "zzz", "") :
                new PlayerEntity(lastName, firstName + "zzz");

        TreePath<PlayerEntity> start = getStorage().lowerBound(startKey);
        TreePath<PlayerEntity> end = getStorage().upperBound(endKey);
        return getStorage().streamOrderedAscending(start, end);
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
        byte[] raw = buf.array().clone();
        return PlayerEntity.builder()
            .id(entityId)
            .raw(raw)
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
