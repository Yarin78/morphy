package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;

import java.nio.ByteBuffer;

public class TextStorageFilter implements ItemStorageFilter<GameHeader> {
    @Override
    public boolean matches(@NotNull GameHeader gameHeader) {
        return gameHeader.guidingText();
    }

    @Override
    public boolean matchesSerialized(@NotNull ByteBuffer buf) {
        return !GameStorageFilter.isGame(buf);
    }
}
