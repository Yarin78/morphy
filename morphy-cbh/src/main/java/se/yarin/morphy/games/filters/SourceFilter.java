package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Source;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;

public class SourceFilter implements ItemStorageFilter<GameHeader> {

    private final @NotNull HashSet<Integer> sourceIds;

    public SourceFilter(@NotNull Source source) {
        this(Collections.singleton(source));
    }

    public SourceFilter(@NotNull Collection<Source> sources) {
        this.sourceIds = sources.stream().map(Source::id).collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public boolean matches(@NotNull GameHeader gameHeader) {
        return sourceIds.contains(gameHeader.sourceId());
    }

    @Override
    public boolean matchesSerialized(@NotNull ByteBuffer buf) {
        int sourceId;

        if (GameStorageFilter.isGame(buf)) {
            // Regular game
            sourceId = ByteBufferUtil.getUnsigned24BitB(buf, 21);
        } else {
            // Guiding text
            sourceId = ByteBufferUtil.getUnsigned24BitB(buf, 10);
        }
        return sourceIds.contains(sourceId);
    }
}
