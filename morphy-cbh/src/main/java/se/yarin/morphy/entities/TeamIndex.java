package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import se.yarin.cbhlib.entities.Nation;
import se.yarin.cbhlib.util.ByteBufferUtil;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.storage.FileItemStorage;
import se.yarin.morphy.storage.InMemoryItemStorage;
import se.yarin.morphy.storage.ItemStorage;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.OpenOption;
import java.util.Set;

import static java.nio.file.StandardOpenOption.*;

public class TeamIndex extends EntityIndex<Team> {

    private static final int SERIALIZED_TEAM_SIZE = 63;

    public TeamIndex() {
        this(new InMemoryItemStorage<>(EntityIndexHeader.empty(SERIALIZED_TEAM_SIZE)));
    }

    protected TeamIndex(ItemStorage<EntityIndexHeader, EntityNode> storage) {
        super(storage, "Team");
    }

    public static TeamIndex create(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return open(file, Set.of(READ, WRITE, CREATE_NEW), true);
    }

    public static TeamIndex open(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return open(file, Set.of(READ, WRITE), true);
    }

    public static TeamIndex open(@NotNull File file, @NotNull Set<OpenOption> options, boolean strict)
            throws IOException, MorphyInvalidDataException {
        return new TeamIndex(new FileItemStorage<>(
                file, new EntityIndexSerializer(SERIALIZED_TEAM_SIZE), EntityIndexHeader.empty(SERIALIZED_TEAM_SIZE), options, strict));
    }

    public static TeamIndex openInMemory(@NotNull File file, @NotNull Set<OpenOption> options, boolean strict)
            throws IOException, MorphyInvalidDataException {
        TeamIndex source = open(file, options, strict);
        TeamIndex target = new TeamIndex();
        source.copyEntities(target);
        return target;
    }

    @Override
    protected Team deserialize(int entityId, int count, int firstGameId, byte[] serializedData) {
        ByteBuffer buf = ByteBuffer.wrap(serializedData);
        return ImmutableTeam.builder()
                .id(entityId)
                .count(count)
                .firstGameId(firstGameId)
                .title(ByteBufferUtil.getFixedSizeByteString(buf, 45))
                .teamNumber(ByteBufferUtil.getIntL(buf))
                .season((ByteBufferUtil.getUnsignedByte(buf) & 1) > 0)
                .year(ByteBufferUtil.getIntL(buf))
                .nation(Nation.values()[ByteBufferUtil.getUnsignedByte(buf)])
                .build();
    }

    @Override
    protected void serialize(Team team, ByteBuffer buf) {
        ByteBufferUtil.putFixedSizeByteString(buf, team.title(), 45);
        ByteBufferUtil.putIntL(buf, team.teamNumber());
        ByteBufferUtil.putByte(buf, team.season() ? 1 : 0);
        ByteBufferUtil.putIntL(buf, team.year());
        ByteBufferUtil.putByte(buf, team.nation().ordinal());
    }
}
