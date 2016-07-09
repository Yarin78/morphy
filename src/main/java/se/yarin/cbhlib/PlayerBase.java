package se.yarin.cbhlib;

import lombok.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class PlayerBase {

    private OrderedBlobStorage storage;

    private final static int SERIALIZED_PLAYER_SIZE = 58;
    private final static int SERIALIZED_PLAYER_KEY_LENGTH = 50;

    /**
     * Creates a new in-memory player base that is initially empty.
     */
    public PlayerBase() {
        storage = new InMemoryBlobStorage(SERIALIZED_PLAYER_KEY_LENGTH);
    }

    PlayerBase(@NonNull OrderedBlobStorage storage) {
        this.storage = storage;
    }

    /**
     * Creates an in-memory player base initially populated with the data from the file
     * @param file the initial data of the database
     * @return an in-memory player base
     */
    public static PlayerBase openInMemory(@NonNull File file) throws IOException, BlobStorageException {
        FileBlobStorage inputStorage = new FileBlobStorage(file, SERIALIZED_PLAYER_KEY_LENGTH);
        InMemoryBlobStorage outputStorage = new InMemoryBlobStorage(SERIALIZED_PLAYER_KEY_LENGTH);
        byte[][] allBlobs = inputStorage.getAllBlobs();
        for (int i = 0; i < allBlobs.length; i++) {
            if (allBlobs[i] != null) {
                outputStorage.putBlob(i, allBlobs[i]);
            }
        }
        return new PlayerBase(outputStorage);
    }

    public static PlayerBase open(@NonNull File file) throws IOException {
        return new PlayerBase(new FileBlobStorageWithInMemoryIndex(file, SERIALIZED_PLAYER_KEY_LENGTH));
    }

    public static PlayerBase create(@NonNull File file) throws IOException {
        return new PlayerBase(FileBlobStorageWithInMemoryIndex.create(file, SERIALIZED_PLAYER_SIZE, SERIALIZED_PLAYER_KEY_LENGTH));
    }

    public int getNumPlayers() {
        return storage.getNumBlobs();
    }

    public PlayerEntity get(int id) throws BlobStorageException, IOException {
        return deserialize(id, ByteBuffer.wrap(storage.getBlob(id)));
    }

    public PlayerEntity put(@NonNull PlayerEntity player) throws BlobStorageException, IOException {
        if (player.getId() == -1) {
            int id = storage.addBlob(serialize(player));
            return get(id);
        }
        storage.putBlob(player.getId(), serialize(player));
        return player;
    }

    public List<PlayerEntity> getAll() throws IOException, BlobStorageException {
        ArrayList<PlayerEntity> players = new ArrayList<>(storage.getNumBlobs());
        int id = 0;
        for (byte[] bytes : storage.getAllBlobs()) {
            if (bytes != null) {
                players.add(deserialize(id++, ByteBuffer.wrap(bytes)));
            }
        }
        assert id == storage.getNumBlobs();
        return players;
    }

    public PlayerEntity getFirst() throws BlobStorageException, IOException {
        int id = storage.firstId();
        return id < 0 ? null : get(id);
    }

    public PlayerEntity getLast() throws BlobStorageException, IOException {
        int id = storage.lastId();
        return id < 0 ? null : get(id);
    }

    public PlayerEntity getNext(PlayerEntity player) throws BlobStorageException, IOException {
        int id = storage.nextBlobId(player.getId());
        if (id < 0) return null;
        return get(id);
    }

    public PlayerEntity getPrevious(PlayerEntity player) throws BlobStorageException, IOException {
        int id = storage.previousBlobId(player.getId());
        if (id < 0) return null;
        return get(id);
    }

    private byte[] serialize(@NonNull PlayerEntity player) {
        ByteBuffer buf = ByteBuffer.allocate(SERIALIZED_PLAYER_SIZE);
        ByteBufferUtil.putByteString(buf, player.getLastName(), 30);
        ByteBufferUtil.putByteString(buf, player.getFirstName(), 20);
        ByteBufferUtil.putIntB(buf, player.getNoGames());
        ByteBufferUtil.putIntB(buf, player.getFirstGameId());
        return buf.array();
    }

    private PlayerEntity deserialize(int id, @NonNull ByteBuffer buf) {
        String last = ByteBufferUtil.getFixedSizeByteString(buf, 30);
        String first = ByteBufferUtil.getFixedSizeByteString(buf, 20);
        int noGames = ByteBufferUtil.getIntB(buf);
        int firstGame = ByteBufferUtil.getIntB(buf);
        return new PlayerEntity(id, last, first, noGames, firstGame);
    }

}
