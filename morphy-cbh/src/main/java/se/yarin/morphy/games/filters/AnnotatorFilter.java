package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Annotator;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.queries.GameEntityJoinCondition;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

public class AnnotatorFilter implements ItemStorageFilter<GameHeader>, GameFilter, GameEntityFilter<Annotator> {

    private final @NotNull Set<Integer> annotatorIds;

    public AnnotatorFilter(int annotatorId) {
        this(new int[] { annotatorId });
    }

    public AnnotatorFilter(int[] annotatorIds) {
        this.annotatorIds = Arrays.stream(annotatorIds).boxed().collect(Collectors.toUnmodifiableSet());
    }

    public AnnotatorFilter(@NotNull Annotator annotator) {
        this(Collections.singleton(annotator));
    }

    public AnnotatorFilter(@NotNull Collection<Annotator> annotators) {
        this.annotatorIds = annotators.stream().map(Annotator::id).collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public EntityType entityType() {
        return EntityType.ANNOTATOR;
    }

    @Override
    public List<Integer> entityIds() {
        return new ArrayList<>(annotatorIds);
    }

    @Override
    public boolean matches(int id, @NotNull GameHeader gameHeader) {
        return annotatorIds.contains(gameHeader.annotatorId());
    }

    @Override
    public boolean matchesSerialized(int id, @NotNull ByteBuffer buf) {
        int annotatorId;

        if (IsGameFilter.isGame(buf)) {
            // Regular game
            annotatorId = ByteBufferUtil.getUnsigned24BitB(buf, 18);
        } else {
            // Guiding text
            annotatorId = ByteBufferUtil.getUnsigned24BitB(buf, 13);
        }
        return annotatorIds.contains(annotatorId);
    }

    @Override
    public String toString() {
        if (annotatorIds.size() == 1) {
            return "annotatorId=" + annotatorIds.stream().findFirst().get();
        } else {
            return "annotatorId in ( " + annotatorIds.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
        }
    }
}
