package se.yarin.cbhlib;

import lombok.Data;

@Data
class GameHeaderStorageMetadata {
    private int storageHeaderSize;
    private int gameHeaderSize;
    private int nextGameId;

    private int unknownByte1, unknownByte2;
    private int[] unknownShort = new int[16];

    private int nextGameId2;
}
