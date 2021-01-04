package se.yarin.cbhlib;

import lombok.NonNull;
import se.yarin.cbhlib.entities.transaction.EntityStorage;
import se.yarin.cbhlib.entities.transaction.EntityStorageImpl;
import se.yarin.cbhlib.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class TeamBase extends EntityBase<TeamEntity> {

    private int SERIALIZED_TEAM_SIZE = 63;

    /**
     * Creates a new in-memory team database that is initially empty.
     */
    public TeamBase() {
        super(EntityStorageImpl.createInMemory());
    }

    private TeamBase(@NonNull EntityStorage<TeamEntity> storage) {
        super(storage);
    }

    /**
     * Creates an in-memory team base initially populated with the data from the file
     * @param file the initial data of the database
     * @return an in-memory team base
     */
    public static TeamBase openInMemory(@NonNull File file) throws IOException {
        return new TeamBase(loadInMemoryStorage(file, new TeamBase()));
    }

    /**
     * Opens a team database from disk
     * @param file the team databases to open
     * @return the opened team database
     * @throws IOException if something went wrong when opening the database
     */
    public static TeamBase open(@NonNull File file) throws IOException {
        return new TeamBase(EntityStorageImpl.open(file, new TeamBase()));
    }

    /**
     * Creates a new team database on disk.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @return the opened team database
     * @throws IOException if something went wrong when creating the database
     */
    public static TeamBase create(@NonNull File file) throws IOException {
        return new TeamBase(EntityStorageImpl.create(file, new TeamBase()));
    }

    public ByteBuffer serialize(@NonNull TeamEntity team) {
        ByteBuffer buf = ByteBuffer.allocate(SERIALIZED_TEAM_SIZE);
        ByteBufferUtil.putFixedSizeByteString(buf, team.getTitle(), 45);
        ByteBufferUtil.putIntL(buf, team.getTeamNumber());
        ByteBufferUtil.putByte(buf, team.isSeason() ? 1 : 0);
        ByteBufferUtil.putIntL(buf, team.getYear());
        ByteBufferUtil.putByte(buf, team.getNation().ordinal());
        ByteBufferUtil.putIntL(buf, team.getCount());
        ByteBufferUtil.putIntL(buf, team.getFirstGameId());

        return buf;
    }

    public TeamEntity deserialize(int entityId, @NonNull ByteBuffer buf) {
        return TeamEntity.builder()
                .id(entityId)
                .title(ByteBufferUtil.getFixedSizeByteString(buf, 45))
                .teamNumber(ByteBufferUtil.getIntL(buf))
                .season((ByteBufferUtil.getUnsignedByte(buf) & 1) > 0)
                .year(ByteBufferUtil.getIntL(buf))
                .nation(Nation.values()[ByteBufferUtil.getUnsignedByte(buf)])
                .count(ByteBufferUtil.getIntL(buf))
                .firstGameId(ByteBufferUtil.getIntL(buf))
                .build();
    }

    public int getSerializedEntityLength() {
        return SERIALIZED_TEAM_SIZE;
    }
}
