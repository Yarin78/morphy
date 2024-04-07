package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseContext;
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
        this(null);
    }

    public AnnotatorIndex(@Nullable DatabaseContext context) {
        this(new InMemoryItemStorage<>(context, "Annotator", EntityIndexHeader.empty(SERIALIZED_ANNOTATOR_SIZE)), context);
    }

    protected AnnotatorIndex(@NotNull File file, @NotNull Set<OpenOption> openOptions, @NotNull DatabaseContext context) throws IOException {
        this(new FileItemStorage<>(
                file, context, "Annotator", new EntityIndexSerializer(SERIALIZED_ANNOTATOR_SIZE), EntityIndexHeader.empty(SERIALIZED_ANNOTATOR_SIZE), openOptions), context);
    }

    protected AnnotatorIndex(@NotNull ItemStorage<EntityIndexHeader, EntityNode> storage, @Nullable DatabaseContext context) {
        super(storage, EntityType.ANNOTATOR, context);
    }

    public static @NotNull AnnotatorIndex create(@NotNull File file, @Nullable DatabaseContext context)
            throws IOException, MorphyInvalidDataException {
        return new AnnotatorIndex(file, Set.of(READ, WRITE, CREATE_NEW), context == null ? new DatabaseContext() : context);
    }

    public static @NotNull AnnotatorIndex open(@NotNull File file, @Nullable DatabaseContext context)
            throws IOException, MorphyInvalidDataException {
        return open(file, DatabaseMode.READ_WRITE, context);
    }

    public static @NotNull AnnotatorIndex open(@NotNull File file, @NotNull DatabaseMode mode, @Nullable DatabaseContext context)
            throws IOException, MorphyInvalidDataException {
        if (mode == DatabaseMode.IN_MEMORY) {
            AnnotatorIndex source = open(file, DatabaseMode.READ_ONLY, context);
            AnnotatorIndex target = new AnnotatorIndex(context);
            source.copyEntities(target);
            return target;
        }
        return new AnnotatorIndex(file, mode.openOptions(), context == null ? new DatabaseContext() : context);
    }

    @Override
    protected @NotNull Annotator deserialize(int entityId, int count, int firstGameId, byte[] serializedData) {
        itemMetricsRef().update(metrics -> metrics.addDeserialization(1));
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
        itemMetricsRef().update(metrics -> metrics.addSerialization(1));
        ByteBufferUtil.putFixedSizeByteString(buf, annotator.name(), 45);
    }

    public static void upgrade(@NotNull File file) throws IOException {
        EntityIndex.upgrade(file, new EntityIndexSerializer(SERIALIZED_ANNOTATOR_SIZE));
    }
}
