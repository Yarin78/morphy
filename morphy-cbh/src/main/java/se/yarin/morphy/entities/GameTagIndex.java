package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
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

public class GameTagIndex extends EntityIndex<GameTag> {
    private static final int SERIALIZED_GAME_TAG_SIZE = 1608;

    public GameTagIndex() {
        this(new InMemoryItemStorage<>(EntityIndexHeader.empty(SERIALIZED_GAME_TAG_SIZE)));
    }

    protected GameTagIndex(ItemStorage<EntityIndexHeader, EntityNode> storage) {
        super(storage, "GameTag");
    }

    public static GameTagIndex create(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return open(file, Set.of(READ, WRITE, CREATE_NEW));
    }

    public static GameTagIndex open(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return open(file, Set.of(READ, WRITE));
    }

    public static GameTagIndex open(@NotNull File file, @NotNull Set<OpenOption> options)
            throws IOException, MorphyInvalidDataException {
        return new GameTagIndex(new FileItemStorage<>(
                file, new EntityIndexSerializer(SERIALIZED_GAME_TAG_SIZE), EntityIndexHeader.empty(SERIALIZED_GAME_TAG_SIZE), options));
    }

    public static GameTagIndex openInMemory(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return openInMemory(file, Set.of(READ));
    }

    public static GameTagIndex openInMemory(@NotNull File file, @NotNull Set<OpenOption> options)
            throws IOException, MorphyInvalidDataException {
        GameTagIndex source = open(file, options);
        GameTagIndex target = new GameTagIndex();
        source.copyEntities(target);
        return target;
    }

    @Override
    protected GameTag deserialize(int entityId, int count, int firstGameId, byte[] serializedData) {
        ByteBuffer buf = ByteBuffer.wrap(serializedData);
        return ImmutableGameTag.builder()
                .id(entityId)
                .count(count)
                .firstGameId(firstGameId)
                .englishTitle(ByteBufferUtil.getFixedSizeByteString(buf, 200))
                .germanTitle(ByteBufferUtil.getFixedSizeByteString(buf, 200))
                .frenchTitle(ByteBufferUtil.getFixedSizeByteString(buf, 200))
                .spanishTitle(ByteBufferUtil.getFixedSizeByteString(buf, 200))
                .italianTitle(ByteBufferUtil.getFixedSizeByteString(buf, 200))
                .dutchTitle(ByteBufferUtil.getFixedSizeByteString(buf, 200))
                .slovenianTitle(ByteBufferUtil.getFixedSizeByteString(buf, 200))
                .resTitle(ByteBufferUtil.getFixedSizeByteString(buf, 200))
                .build();
    }

    @Override
    protected void serialize(GameTag gameTag, ByteBuffer buf) {
        ByteBufferUtil.putFixedSizeByteString(buf, gameTag.englishTitle(), 200);
        ByteBufferUtil.putFixedSizeByteString(buf, gameTag.germanTitle(), 200);
        ByteBufferUtil.putFixedSizeByteString(buf, gameTag.frenchTitle(), 200);
        ByteBufferUtil.putFixedSizeByteString(buf, gameTag.spanishTitle(), 200);
        ByteBufferUtil.putFixedSizeByteString(buf, gameTag.italianTitle(), 200);
        ByteBufferUtil.putFixedSizeByteString(buf, gameTag.dutchTitle(), 200);
        ByteBufferUtil.putFixedSizeByteString(buf, gameTag.slovenianTitle(), 200);
        ByteBufferUtil.putFixedSizeByteString(buf, gameTag.resTitle(), 200);
    }
}
