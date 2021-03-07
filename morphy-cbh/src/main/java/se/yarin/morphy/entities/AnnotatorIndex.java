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

public class AnnotatorIndex extends EntityIndex<Annotator> {

    private static final int SERIALIZED_ANNOTATOR_SIZE = 53;

    public AnnotatorIndex() {
        this(new InMemoryItemStorage<>(EntityIndexHeader.empty(SERIALIZED_ANNOTATOR_SIZE)));
    }

    protected AnnotatorIndex(ItemStorage<EntityIndexHeader, EntityNode> storage) {
        super(storage, "Annotator");
    }

    public static AnnotatorIndex open(File file, OpenOption... options)
            throws IOException, MorphyInvalidDataException {
        OpenOption.validate(options);
        Set<OpenOption> optionSet = Set.of(options);
        return new AnnotatorIndex(new FileItemStorage<>(file, new EntityIndexSerializer(SERIALIZED_ANNOTATOR_SIZE), optionSet));
    }

    @Override
    protected Annotator deserialize(int entityId, int count, int firstGameId, byte[] serializedData) {
        ByteBuffer buf = ByteBuffer.wrap(serializedData);
        return ImmutableAnnotator.builder()
                .id(entityId)
                .count(count)
                .firstGameId(firstGameId)
                .name(ByteBufferUtil.getFixedSizeByteString(buf, 45))
                .build();
    }

    @Override
    protected void serialize(Annotator annotator, ByteBuffer buf) {
        ByteBufferUtil.putFixedSizeByteString(buf, annotator.name(), 45);
    }
}
