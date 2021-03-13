package se.yarin.morphy.entities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.entities.PlayerEntity;
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

public class PlayerIndex extends EntityIndex<Player> {
    private static final Logger log = LoggerFactory.getLogger(PlayerIndex.class);

    private static final int SERIALIZED_PLAYER_SIZE = 58;

    public PlayerIndex() {
        this(new InMemoryItemStorage<>(EntityIndexHeader.empty(SERIALIZED_PLAYER_SIZE), OpenOption.RW()));
    }

    protected PlayerIndex(ItemStorage<EntityIndexHeader, EntityNode> storage) {
        super(storage, "Player");
    }

    public static PlayerIndex open(File file, OpenOption... options)
            throws IOException, MorphyInvalidDataException {
        OpenOption.validate(options);
        Set<OpenOption> optionSet = Set.of(options);
        return new PlayerIndex(new FileItemStorage<>(file, new EntityIndexSerializer(SERIALIZED_PLAYER_SIZE), optionSet));
    }

    @Override
    protected Player deserialize(int entityId, int count, int firstGameId, byte[] serializedData) {
        ByteBuffer buf = ByteBuffer.wrap(serializedData);
        return ImmutablePlayer.builder()
                .id(entityId)
                .count(count)
                .firstGameId(firstGameId)
                .lastName(ByteBufferUtil.getFixedSizeByteString(buf, 30))
                .firstName(ByteBufferUtil.getFixedSizeByteString(buf, 20))
                .build();
    }

    @Override
    protected void serialize(Player player, ByteBuffer buf) {
        ByteBufferUtil.putFixedSizeByteString(buf, player.lastName(), 30);
        ByteBufferUtil.putFixedSizeByteString(buf, player.firstName(), 20);
    }
}
