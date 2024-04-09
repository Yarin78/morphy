package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;

import java.nio.ByteBuffer;

public class TextStorageFilter implements ItemStorageFilter<GameHeader>, GameFilter {
    @Override
    public boolean matches(int id, @NotNull GameHeader gameHeader) {
        return gameHeader.guidingText();
    }

    @Override
    public boolean matchesSerialized(int id, @NotNull ByteBuffer buf) {
        return !IsGameFilter.isGame(buf);
    }

    @Override
    public String toString() {
        return "isText";
    }
}
