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

import static java.nio.file.StandardOpenOption.*;

public class AnnotatorIndex extends EntityIndex<Annotator> {

    private static final int SERIALIZED_ANNOTATOR_SIZE = 53;

    public AnnotatorIndex() {
        this(new InMemoryItemStorage<>(EntityIndexHeader.empty(SERIALIZED_ANNOTATOR_SIZE)));
    }

    protected AnnotatorIndex(@NotNull File file, @NotNull Set<OpenOption> openOptions) throws IOException {
        this(new FileItemStorage<>(
                file, new EntityIndexSerializer(SERIALIZED_ANNOTATOR_SIZE), EntityIndexHeader.empty(SERIALIZED_ANNOTATOR_SIZE), openOptions));
    }

    protected AnnotatorIndex(ItemStorage<EntityIndexHeader, EntityNode> storage) {
        super(storage, "Annotator");
    }

    public static @NotNull AnnotatorIndex create(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return new AnnotatorIndex(file, Set.of(READ, WRITE, CREATE_NEW));
    }

    public static @NotNull AnnotatorIndex open(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return open(file, DatabaseMode.READ_WRITE);
    }

    public static @NotNull AnnotatorIndex open(@NotNull File file, @NotNull DatabaseMode mode)
            throws IOException, MorphyInvalidDataException {
        if (mode == DatabaseMode.IN_MEMORY) {
            AnnotatorIndex source = open(file, DatabaseMode.READ_ONLY);
            AnnotatorIndex target = new AnnotatorIndex();
            source.copyEntities(target);
            return target;
        }
        return new AnnotatorIndex(file, mode.openOptions());
    }

    @Override
    protected @NotNull Annotator deserialize(int entityId, int count, int firstGameId, byte[] serializedData) {
        ByteBuffer buf = ByteBuffer.wrap(serializedData);
        return ImmutableAnnotator.builder()
                .id(entityId)
                .count(count)
                .firstGameId(firstGameId)
                .name(ByteBufferUtil.getFixedSizeByteString(buf, 45))
                .build();
    }

    @Override
    protected void serialize(@NotNull Annotator annotator, @NotNull ByteBuffer buf) {
        ByteBufferUtil.putFixedSizeByteString(buf, annotator.name(), 45);
    }

    public static void upgrade(@NotNull File file) throws IOException {
        EntityIndex.upgrade(file, new EntityIndexSerializer(SERIALIZED_ANNOTATOR_SIZE));
    }
}
