package se.yarin.morphy.games;

import java.util.EnumSet;

public enum GameHeaderFlags {
    SETUP_POSITION(0x00000001), // doesn't start from the initial position
    VARIATIONS(0x00000002), // has variations
    COMMENTARY(0x00000004), // has annotation 0x02 and/or 0x82
    SYMBOLS(0x00000008), // has annotation 0x03
    GRAPHICAL_SQUARES(0x00000010), // has annotation 0x04
    GRAPHICAL_ARROWS(0x00000020), // has annotation 0x05
    TIME_SPENT(0x00000080), // has annotation 0x07
    ANNO_TYPE_8(0x00000100), // has annotation 0x08
    TRAINING(0x00000200), // has annotation 0x09
    EMBEDDED_AUDIO(0x00010000), // has annotation 0x10 (?)
    EMBEDDED_PICTURE(0x00020000), // has annotation 0x11 (?)
    EMBEDDED_VIDEO(0x00040000), // has annotation 0x20 (?)
    GAME_QUOTATION(0x00080000), // has annotation 0x13
    PAWN_STRUCTURE(0x00100000), // has annotation 0x14
    PIECE_PATH(0x00200000), // has annotation 0x15
    WHITE_CLOCK(0x00400000), // has annotation 0x16
    BLACK_CLOCK(0x00800000), // has annotation 0x17
    CRITICAL_POSITION(0x01000000),  // has annotation 0x18
    CORRESPONDENCE_HEADER(0x02000000), // has annotation 0x61 (?)
    ANNO_TYPE_1A(0x04000000), // has annotation 0x1a (media? denoted with M in the AIT column)
    UNORTHODOX(0x08000000), // if the game is an unorthodox chess game (e.g. Chess 960)
    WEB_LINK(0x10000000); // has annotation 0x1C

    private final int value;

    public int getValue() {
        return value;
    }

    GameHeaderFlags(int value) {
        this.value = value;
    }

    public static EnumSet<GameHeaderFlags> decodeFlags(int flagInt) {
        EnumSet<GameHeaderFlags> flags = EnumSet.noneOf(GameHeaderFlags.class);
        for (GameHeaderFlags flag : GameHeaderFlags.values()) {
            if ((flagInt & flag.getValue()) > 0) {
                flags.add(flag);
            }
        }
        return flags;
    }

    public static int encodeFlags(EnumSet<GameHeaderFlags> flags) {
        int flagInt = 0;
        for (GameHeaderFlags flag : flags) {
            flagInt += flag.getValue();
        }
        return flagInt;
    }

    public static int allFlagsMask() {
        int value = 0;
        for (GameHeaderFlags flag : GameHeaderFlags.values()) {
            value |= flag.value;
        }
        return value;
    }
}
