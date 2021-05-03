package se.yarin.morphy.search;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;

import java.nio.ByteBuffer;

public class GameHeaderGameFilter implements ItemStorageFilter<GameHeader> {
    @Override
    public boolean matches(@NotNull GameHeader gameHeader) {
        return !gameHeader.guidingText();
    }

    @Override
    public boolean matchesSerialized(@NotNull ByteBuffer buf) {
        return (buf.get(0) & 2) == 0;
    }
}
