package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseMode;
import se.yarin.util.ByteBufferUtil;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.storage.FileItemStorage;
import se.yarin.morphy.storage.InMemoryItemStorage;
import se.yarin.morphy.storage.ItemStorage;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.OpenOption;
import java.util.Set;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.*;

public class GameTagIndex extends EntityIndex<GameTag> {
    private static final int SERIALIZED_GAME_TAG_SIZE = 1608;

    public GameTagIndex() {
        this(new InMemoryItemStorage<>(EntityIndexHeader.empty(SERIALIZED_GAME_TAG_SIZE)));
    }

    protected GameTagIndex(@NotNull File file, @NotNull Set<OpenOption> openOptions) throws IOException {
        this(new FileItemStorage<>(
                file, new EntityIndexSerializer(SERIALIZED_GAME_TAG_SIZE), EntityIndexHeader.empty(SERIALIZED_GAME_TAG_SIZE), openOptions));
    }

    protected GameTagIndex(ItemStorage<EntityIndexHeader, EntityNode> storage) {
        super(storage, "GameTag");
    }

    public static GameTagIndex create(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return new GameTagIndex(file, Set.of(READ, WRITE, CREATE_NEW));
    }

    public static GameTagIndex open(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return open(file, DatabaseMode.READ_WRITE);
    }

    public static GameTagIndex open(@NotNull File file, @NotNull DatabaseMode mode)
            throws IOException, MorphyInvalidDataException {
        if (mode == DatabaseMode.IN_MEMORY) {
            GameTagIndex source = open(file, DatabaseMode.READ_ONLY);
            GameTagIndex target = new GameTagIndex();
            source.copyEntities(target);
            return target;
        }
        return new GameTagIndex(file, mode.openOptions());
    }


    /**
     * Searches for game tags using a case sensitive prefix search.
     * @param title a prefix of the game tag
     * @return a stream of matching game tags
     */
    public Stream<GameTag> prefixSearch(@NotNull String title) {
        GameTag startKey = GameTag.of(title);
        GameTag endKey = GameTag.of(title + "zzz");

        return streamOrderedAscending(startKey, endKey);
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

    public static void upgrade(@NotNull File file) throws IOException {
        EntityIndex.upgrade(file, new EntityIndexSerializer(SERIALIZED_GAME_TAG_SIZE));
    }
}
