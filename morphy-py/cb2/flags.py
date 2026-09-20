"""The flags and medals of a game header. Both are bitmasks, and which bit
means what is taken from the older format (v1); see format/v2/spec/1-game-headers.md.
"""

# Flag bit value -> name. Most of these say that the game has annotations of
# a particular kind.
GAME_FLAGS = {
    0x00000001: "setup position",
    0x00000002: "variations",
    0x00000004: "commentary",
    0x00000008: "symbols",
    0x00000010: "graphical squares",
    0x00000020: "graphical arrows",
    0x00000080: "time spent",
    0x00000100: "anno type 8",
    0x00000200: "training",
    0x00010000: "embedded audio",
    0x00020000: "embedded picture",
    0x00040000: "embedded video",
    0x00080000: "game quotation",
    0x00100000: "pawn structure",
    0x00200000: "piece path",
    0x00400000: "white clock",
    0x00800000: "black clock",
    0x01000000: "critical position",
    0x02000000: "correspondence header",
    0x04000000: "anno type 1a",
    0x08000000: "unorthodox",
    0x10000000: "web link",
}

# Annotation magnitude bits, each qualifying one of the flags above with a
# rough size for that kind of annotation. Bits 0-1 hold the variation
# magnitude and are handled separately. See the specification.
ANNOTATION_MAGNITUDES = {
    0x0004: "over 200 bytes of commentary",
    0x0008: "10 or more symbol annotations",
    0x0010: "10 or more square annotations",
    0x0020: "6 or more arrow annotations",
    0x0080: "10 or more time spent annotations",
    0x0200: "6 or more training annotations",
}

# Medals, in bit order.
MEDALS = [
    "best game", "decided tournament", "model game", "novelty", "pawn structure",
    "strategy", "tactics", "with attack", "defense", "sacrifice", "material",
    "piece play", "endgame", "tactical blunder", "strategical blunder", "user",
]


def _decode_bits(value, names):
    """Names of the set bits, with anything unaccounted for shown as a hex
    remainder so it can't pass unnoticed."""
    parts = [name for bit, name in sorted(names.items()) if value & bit]
    rest = value & ~sum(names)
    if rest:
        parts.append(f"?{rest:#x}")
    return ", ".join(parts)


def decode_flags(value):
    """Decode the game flags to text."""
    return _decode_bits(value, GAME_FLAGS)


def decode_annotation_magnitudes(value):
    """Decode the annotation magnitudes to text. Bits 0-1 are the variation
    magnitude, a level from 1 to 4, which only a database written by the older
    format or converted from it has."""
    parts = []
    if value & 3:
        parts.append(f"variation level {(value & 3) + 1}")
    rest = _decode_bits(value & ~3, ANNOTATION_MAGNITUDES)
    return ", ".join(parts + ([rest] if rest else []))


def decode_medals(value):
    """Decode the medals to text."""
    return _decode_bits(value, {1 << i: name for i, name in enumerate(MEDALS)})
