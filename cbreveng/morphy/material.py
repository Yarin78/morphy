"""The material left at the end of a game, and the endgame types the game
passed through. Both are stored in the game header; see FORMAT.md.

The material encoding is the same as in the older format (v1). The endgame
bitmask is not in v1 at all, and what its bits mean is only partly known.
"""

# Piece -> (bit shift, mask) within an encoded material value, in the order
# the pieces are listed in the text form.
MATERIAL_PIECES = [("Q", 9, 7), ("R", 0, 7), ("B", 3, 7), ("N", 6, 7), ("P", 12, 15)]

# Endgame bit -> a guess at what it means, from the final material of the
# games that set it in the one database where these bits are populated. Only
# bits that fit a clear rule in at least 4 games out of 5 are named. The
# guesses can't be checked exactly, because the bits record what the game
# passed through while the final material is only where it ended up.
ENDGAME_TYPES = {
    0: "bishop endgame",
    1: "knight vs bishop",
    13: "minor piece endgame",
    14: "knight endgame",
    24: "queen endgame",
    29: "bishop vs rook",
    35: "rook endgame",
}


def decode_material(value):
    """Decode an encoded material value to text such as "R2P" (a rook and two
    pawns), in the same form the older format (v1) uses. Returns "" for a bare
    king."""
    counts = {piece: (value >> shift) & mask for piece, shift, mask in MATERIAL_PIECES}
    text = "".join(piece * counts[piece] for piece in "QRBN")
    return f"{text}{counts['P']}P" if counts["P"] else text


def decode_endgame_types(value):
    """Decode the endgame bitmask to text, naming the bits that are known and
    showing the rest as "#<bit>"."""
    return ", ".join(
        ENDGAME_TYPES.get(bit, f"#{bit}")
        for bit in range(value.bit_length())
        if value >> bit & 1
    )
