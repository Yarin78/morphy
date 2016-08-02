package se.yarin.cbhlib;

public enum GameHeaderFlags {
    CRITICAL_POSITION,  // has annotation 0x18 (?)
    CORRESPONDENCE_HEADER, // has annotation 0x61 (?)
    FISCHER_RANDOM, // is a fischer random game (?)
    EMBEDDED_AUDIO, // has annotation 0x10 (?)
    EMBEDDED_PICTURE, // has annotation 0x11 (?)
    EMBEDDED_VIDEO, // has annotation 0x20 (?)
    GAME_QUOTATION, // has annotation 0x13 (?)
    PATH_STRUCTURE, // has annotation 0x14 (?)
    PIECE_PATH, // has annotation 0x15 (?)
    ANNO_TYPE_8, // has annotation 0x08 (?)
    TRAINING, // has annotation 0x09 (?)
    SETUP_POSITION, // doesn't start from the initial position
    VARIATIONS, // has variations
    COMMENTARY, // has annotation 0x02 and/or 0x82 (?)
    SYMBOLS, // has annotation 0x03
    GRAPHICAL_SQUARES, // has annotation 0x04
    GRAPHICAL_ARROWS, // has annotation 0x05
    TIME_NOTIFICATIONS, // ?
    WHITE_CLOCK, // has annotation 0x16
    BLACK_CLOCK, // has annotation 0x17
    WEB_LINK, // has annotation 0x1C

    STREAM, // has annotation 0x25?
}
