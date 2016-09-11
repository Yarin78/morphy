package se.yarin.cbhlib;

import lombok.Data;

@Data
public class ExtendedGameHeaderStorageMetadata {
    private int version;          // cbj file version
    private int serializedExtendedGameHeaderSize;  // Size of each extended game header
    // TODO: This value may be much bigger than number of games in ordinary header!? Why?
    private int numHeaders;  // number of extended game headers in the file
    private byte[] fillers; // extra bytes in header; most likely unused
}
