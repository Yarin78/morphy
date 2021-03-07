package se.yarin.morphy.entities;

import se.yarin.cbhlib.entities.Nation;
import se.yarin.cbhlib.util.ByteBufferUtil;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.storage.FileItemStorage;
import se.yarin.morphy.storage.InMemoryItemStorage;
import se.yarin.morphy.storage.ItemStorage;
import se.yarin.morphy.storage.OpenOption;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;

public class TeamIndex extends EntityIndex<Team> {

    private static final int SERIALIZED_TEAM_SIZE = 63;

    public TeamIndex() {
        this(new InMemoryItemStorage<>(EntityIndexHeader.empty(SERIALIZED_TEAM_SIZE)));
    }

    protected TeamIndex(ItemStorage<EntityIndexHeader, EntityNode> storage) {
        super(storage, "Team");
    }

    public static TeamIndex open(File file, OpenOption... options)
            throws IOException, MorphyInvalidDataException {
        OpenOption.validate(options);
        Set<OpenOption> optionSet = Set.of(options);
        return new TeamIndex(new FileItemStorage<>(file, new EntityIndexSerializer(SERIALIZED_TEAM_SIZE), optionSet));
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
