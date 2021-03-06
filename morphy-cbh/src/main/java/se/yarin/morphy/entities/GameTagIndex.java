package se.yarin.morphy.entities;

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

public class GameTagIndex extends EntityIndex<GameTag> {
    private static final int SERIALIZED_GAME_TAG_SIZE = 1608;

    public GameTagIndex() {
        this(new InMemoryItemStorage<>(EntityIndexHeader.empty(SERIALIZED_GAME_TAG_SIZE)));
    }

    protected GameTagIndex(ItemStorage<EntityIndexHeader, EntityNode> storage) {
        super(storage);
    }

    public static GameTagIndex open(File file, OpenOption... options)
            throws IOException, MorphyInvalidDataException {
        OpenOption.validate(options);
        Set<OpenOption> optionSet = Set.of(options);
        return new GameTagIndex(new FileItemStorage<>(file, new EntityIndexSerializer(SERIALIZED_GAME_TAG_SIZE), optionSet));
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
