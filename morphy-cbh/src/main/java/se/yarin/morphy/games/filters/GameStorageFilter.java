package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class GameStorageFilter implements ItemStorageFilter<GameHeader> {
    @Override
    public boolean matches(@NotNull GameHeader gameHeader) {
        return !gameHeader.guidingText();
    }

    @Override
    public boolean matchesSerialized(@NotNull ByteBuffer buf) {
        return isGame(buf);
    }

    static boolean isGame(@NotNull ByteBuffer buf) {
        return (ByteBufferUtil.getUnsignedByte(buf, 0) & 2) == 0;
    }
}
