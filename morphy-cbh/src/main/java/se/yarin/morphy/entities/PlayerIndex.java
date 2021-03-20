package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

public class PlayerIndex extends EntityIndex<Player> {
    private static final Logger log = LoggerFactory.getLogger(PlayerIndex.class);

    private static final int SERIALIZED_PLAYER_SIZE = 58;

    public PlayerIndex() {
        this(new InMemoryItemStorage<>(EntityIndexHeader.empty(SERIALIZED_PLAYER_SIZE)));
    }

    protected PlayerIndex(@NotNull ItemStorage<EntityIndexHeader, EntityNode> storage) {
        super(storage, "Player");
    }

    public static PlayerIndex create(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return open(file, Set.of(READ, WRITE, CREATE_NEW), true);
    }

    public static PlayerIndex open(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return open(file, Set.of(READ, WRITE), true);
    }

    public static PlayerIndex open(@NotNull File file, @NotNull Set<OpenOption> options, boolean strict)
            throws IOException, MorphyInvalidDataException {
        return new PlayerIndex(new FileItemStorage<>(
                file, new EntityIndexSerializer(SERIALIZED_PLAYER_SIZE), EntityIndexHeader.empty(SERIALIZED_PLAYER_SIZE), options, strict));
    }

    public static PlayerIndex openInMemory(@NotNull File file, @NotNull Set<OpenOption> options, boolean strict)
            throws IOException, MorphyInvalidDataException {
        PlayerIndex source = open(file, options, strict);
        PlayerIndex target = new PlayerIndex();
        source.copyEntities(target);
        return target;
    }

    @Override
    protected Player deserialize(int entityId, int count, int firstGameId, @NotNull byte[] serializedData) {
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
    protected void serialize(@NotNull Player player, @NotNull ByteBuffer buf) {
        ByteBufferUtil.putFixedSizeByteString(buf, player.lastName(), 30);
        ByteBufferUtil.putFixedSizeByteString(buf, player.firstName(), 20);
    }
}
