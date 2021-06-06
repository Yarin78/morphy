package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Annotator;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

public class AnnotatorFilter implements ItemStorageFilter<GameHeader> {

    private final @NotNull HashSet<Integer> annotatorIds;

    public AnnotatorFilter(@NotNull Annotator annotator) {
        this(Collections.singleton(annotator));
    }

    public AnnotatorFilter(@NotNull Collection<Annotator> annotators) {
        this.annotatorIds = annotators.stream().map(Annotator::id).collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public boolean matches(@NotNull GameHeader gameHeader) {
        return annotatorIds.contains(gameHeader.annotatorId());
    }

    @Override
    public boolean matchesSerialized(@NotNull ByteBuffer buf) {
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
