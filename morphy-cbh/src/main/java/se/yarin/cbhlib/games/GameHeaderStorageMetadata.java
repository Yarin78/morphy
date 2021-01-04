package se.yarin.cbhlib.games;

import lombok.Data;

@Data
public class GameHeaderStorageMetadata {
    private int unknownFlags;          // 0x24 in old bases, 0x2C in new ones
    private int serializedHeaderSize;  // Both file header and each game header
    private int nextGameId;
    private int nextEmbeddedSoundId;   // Deprecated feature, usually 0
    private int nextEmbeddedPictureId; // Deprecated feature, usually 0
    private int nextEmbeddedVideoId;   // Deprecated feature, usually 0

    private int unknownByte1;          // ? always 0
    private int unknownByte2;          // ? always 1
    private int[] unknownShort = new int[10];

    private int nextGameId2;  // Virtually always the same as nextGameId
}
