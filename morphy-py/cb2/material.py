"""The material left at the end of a game, and the endgames the game passed
through. Both are stored in the game header; see format/v2/spec/1-game-headers.md.

The material encoding is the same as in the older format (v1). The endgame
bitmask replaces v1's list of endgame types: one bit per matchup of pieces,
set when the main line held that matchup for at least 5 plies.
"""

# Piece -> (bit shift, mask) within an encoded material value, in the order
# the pieces are listed in the text form.
MATERIAL_PIECES = [("Q", 9, 7), ("R", 0, 7), ("B", 3, 7), ("N", 6, 7), ("P", 12, 15)]

# The endgame matchups, one per bit of the endgame bitmask: what white has
# against what black has, pieces only. The bits run through the ten kinds of
# material in alphabetical order and, for each, through its opponents in the
# same order; only matchups of roughly equal material are in the table. See
# the specification.
ENDGAME_MATERIALS = ["B", "BB", "BN", "N", "NN", "Q", "R", "RB", "RN", "RR"]
ENDGAME_OPPONENTS = {
    "B": ["B", "N", "R"],
    "BB": ["BB", "BN", "NN", "Q", "R"],
    "BN": ["BB", "BN", "NN", "Q", "R"],
    "N": ["B", "N", "R"],
    "NN": ["BB", "BN", "NN", "Q", "R"],
    "Q": ["BB", "BN", "NN", "Q", "R", "RB", "RN", "RR"],
    "R": ["B", "BB", "BN", "N", "NN", "Q", "R"],
    "RB": ["Q", "RB", "RN"],
    "RN": ["Q", "RB", "RN"],
    "RR": ["Q", "RR"],
}
ENDGAME_TYPES = {
    bit: f"{white} : {black}"
    for bit, (white, black) in enumerate(
        (white, black) for white in ENDGAME_MATERIALS for black in ENDGAME_OPPONENTS[white]
    )
}


def decode_material(value):
    """Decode an encoded material value to text such as "R2P" (a rook and two
    pawns), in the same form the older format (v1) uses. Returns "" for a bare
    king."""
    counts = {piece: (value >> shift) & mask for piece, shift, mask in MATERIAL_PIECES}
    text = "".join(piece * counts[piece] for piece in "QRBN")
    return f"{text}{counts['P']}P" if counts["P"] else text


def decode_endgame_types(value):
    """Decode the endgame bitmask to text, e.g. "R : R, Q : R" for a game that
    passed through a rook endgame and then queen against rook. Bits 44-47 have
    no matchup and are shown as "#<bit>"."""
    return ", ".join(
        ENDGAME_TYPES.get(bit, f"#{bit}")
        for bit in range(value.bit_length())
        if value >> bit & 1
    )
