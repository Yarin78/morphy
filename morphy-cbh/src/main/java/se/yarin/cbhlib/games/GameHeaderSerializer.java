package se.yarin.cbhlib.games;

import java.nio.ByteBuffer;

public interface GameHeaderSerializer {
    ByteBuffer serialize(GameHeader gameHeader);
    GameHeader deserialize(int gameId, ByteBuffer buffer);
    int getSerializedGameHeaderLength();
}
