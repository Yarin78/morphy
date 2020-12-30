package se.yarin.cbhlib;

import java.nio.ByteBuffer;

public interface ExtendedGameHeaderSerializer {
    ByteBuffer serialize(ExtendedGameHeader extendedGameHeader);
    ExtendedGameHeader deserialize(int gameId, ByteBuffer buffer);

    // TODO: Is this one really needed? It usually contains the wrong value!
    int getSerializedExtendedGameHeaderLength();
}
