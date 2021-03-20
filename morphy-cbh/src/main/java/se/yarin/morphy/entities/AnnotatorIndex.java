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

public class AnnotatorIndex extends EntityIndex<Annotator> {

    private static final int SERIALIZED_ANNOTATOR_SIZE = 53;

    public AnnotatorIndex() {
        this(new InMemoryItemStorage<>(EntityIndexHeader.empty(SERIALIZED_ANNOTATOR_SIZE)));
    }

    protected AnnotatorIndex(ItemStorage<EntityIndexHeader, EntityNode> storage) {
        super(storage, "Annotator");
    }

    public static AnnotatorIndex create(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return open(file, Set.of(READ, WRITE, CREATE_NEW), true);
    }

    public static AnnotatorIndex open(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return open(file, Set.of(READ, WRITE), true);
    }

    public static AnnotatorIndex open(@NotNull File file, @NotNull Set<OpenOption> options, boolean strict)
            throws IOException, MorphyInvalidDataException {
        return new AnnotatorIndex(new FileItemStorage<>(
                file, new EntityIndexSerializer(SERIALIZED_ANNOTATOR_SIZE), EntityIndexHeader.empty(SERIALIZED_ANNOTATOR_SIZE), options, strict));
    }

    public static AnnotatorIndex openInMemory(@NotNull File file, @NotNull Set<OpenOption> options, boolean strict)
            throws IOException, MorphyInvalidDataException {
        AnnotatorIndex source = open(file, options, strict);
        AnnotatorIndex target = new AnnotatorIndex();
        source.copyEntities(target);
        return target;
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
