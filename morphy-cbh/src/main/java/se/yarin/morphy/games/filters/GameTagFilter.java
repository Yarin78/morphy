package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.GameTag;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;

public class GameTagFilter implements ItemStorageFilter<ExtendedGameHeader>, GameFilter {
    private final @NotNull HashSet<Integer> gameTagIds;

    public GameTagFilter(@NotNull GameTag gameTag) {
        this(Collections.singleton(gameTag));
    }

    public GameTagFilter(@NotNull Collection<GameTag> gameTags) {
        this.gameTagIds = gameTags.stream().map(GameTag::id).collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public boolean matches(@NotNull ExtendedGameHeader extendedGameHeader) {
        return gameTagIds.contains(extendedGameHeader.gameTagId());
    }

    @Override
    public boolean matchesSerialized(@NotNull ByteBuffer buf) {
        return gameTagIds.contains(ByteBufferUtil.getIntB(buf, 116));
    }

    public @Nullable ItemStorageFilter<ExtendedGameHeader> extendedGameHeaderFilter() { return this; }

    @Override
    public String toString() {
        if (gameTagIds.size() == 1) {
            return "gameTagId=" + gameTagIds.stream().findFirst().get();
        } else {
            return "gameTagId in ( " + gameTagIds.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
        }
    }
}
