package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Source;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.queries.GameEntityJoinCondition;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

public class SourceFilter implements ItemStorageFilter<GameHeader>, GameFilter, GameEntityFilter<Source> {

    private final @NotNull Set<Integer> sourceIds;

    public SourceFilter(int sourceId) {
        this(new int[] { sourceId });
    }

    public SourceFilter(int[] sourceIds) {
        this.sourceIds = Arrays.stream(sourceIds).boxed().collect(Collectors.toUnmodifiableSet());
    }

    public SourceFilter(@NotNull Source source) {
        this(Collections.singleton(source));
    }

    public SourceFilter(@NotNull Collection<Source> sources) {
        this.sourceIds = sources.stream().map(Source::id).collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public List<Integer> entityIds() {
        return new ArrayList<>(sourceIds);
    }

    @Override
    public EntityType entityType() {
        return EntityType.SOURCE;
    }

    @Override
    public boolean matches(int id, @NotNull GameHeader gameHeader) {
        return sourceIds.contains(gameHeader.sourceId());
    }

    @Override
    public boolean matchesSerialized(int id, @NotNull ByteBuffer buf) {
        int sourceId;

        if (IsGameFilter.isGame(buf)) {
            // Regular game
            sourceId = ByteBufferUtil.getUnsigned24BitB(buf, 21);
        } else {
            // Guiding text
            sourceId = ByteBufferUtil.getUnsigned24BitB(buf, 10);
        }
        return sourceIds.contains(sourceId);
    }

    @Override
    public String toString() {
        if (sourceIds.size() == 1) {
            return "sourceId=" + sourceIds.stream().findFirst().get();
        } else {
            return "sourceId in ( " + sourceIds.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
        }
    }
}
