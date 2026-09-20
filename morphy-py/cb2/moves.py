"""MoveDatabase: reads the .2cbg file holding the moves of every game, and
the contents of every guiding text.

Each game's record starts at the offset given in its .2cbh record. The move
data is a sequence of little-endian 16-bit words: a word below 0xfff0 is a
move (looked up in WORDS) or a piece of a setup position (see placement()),
and the words from 0xfffa up are markers. format/v2/spec/2-moves.md is the
source of truth for the layout.

A move word says which piece moves from where to where, and what it captures
or promotes to, so a game can be decoded without replaying it on a board.
"""
import os
import struct
from dataclasses import dataclass, field
from typing import Optional

FILE_HEADER_SIZE = 12
RECORD_MAGIC = bytes.fromhex("8877665544332211")

# Markers in the word stream.
NULL_MOVE = 0xFFFA
START_POSITION_SECTION = 0xFFFB
MOVES_SECTION = 0xFFFC
MORE_ALTERNATIVES = 0xFFFD
END_OF_LINE = 0xFFFF
FIRST_MARKER = 0xFFF0

# The first word of the stream.
VARIANT_NORMAL = 1
VARIANT_CHESS960 = 2

# Castling in Chess960 has four words for each start position.
FIRST_CHESS960_CASTLING = 0xB12D
FIRST_PLACEMENT = FIRST_CHESS960_CASTLING + 4 * 960

# Pieces in the order of the blocks of move words. Also the order of the
# capture slots (after "no capture"), and of the promotion pieces.
BLOCK_PIECES = "KQNBR"
CAPTURED_PIECES = "QNBRP"
PROMOTION_PIECES = "QNBR"  # Q and N confirmed (wch2); B and R by analogy

# Directions (file, rank) in the order each piece's destinations are listed.
KING_STEPS = [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)]
KNIGHT_STEPS = [(-2, -1), (-2, 1), (2, -1), (2, 1), (-1, -2), (-1, 2), (1, -2), (1, 2)]
BISHOP_RAYS = [(-1, -1), (1, -1), (1, 1), (-1, 1)]
ROOK_RAYS = [(-1, 0), (0, -1), (1, 0), (0, 1)]


@dataclass(frozen=True)
class MoveWord:
    """What a move word means. Squares are 0-63 with a1=0, a2=1, ..., h8=63
    (file-major, as in v1). piece is upper case for white, lower for black."""
    piece: str
    from_sq: int = 0
    to_sq: int = 0
    captured: Optional[str] = None  # a piece letter, "ep", or None
    promotion: Optional[str] = None
    castles: Optional[str] = None  # "O-O" or "O-O-O"

    def __str__(self):
        if self.castles:
            return self.castles
        piece = "" if self.piece in "Pp" else self.piece.upper()
        sep = "x" if self.captured else "-"
        promotion = "=" + self.promotion if self.promotion else ""
        ep = " e.p." if self.captured == "ep" else ""
        return f"{piece}{square_name(self.from_sq)}{sep}{square_name(self.to_sq)}{promotion}{ep}"


def square_name(sq):
    return "abcdefgh"[sq // 8] + str(sq % 8 + 1)


def _destinations(piece, sq):
    x, y = divmod(sq, 8)
    if piece in "KN":
        steps = KING_STEPS if piece == "K" else KNIGHT_STEPS
        return [(x + dx) * 8 + y + dy for dx, dy in steps if 0 <= x + dx < 8 and 0 <= y + dy < 8]
    rays = {"B": BISHOP_RAYS, "R": ROOK_RAYS, "Q": BISHOP_RAYS + ROOK_RAYS}[piece]
    out = []
    for dx, dy in rays:
        k = 1
        while 0 <= x + k * dx < 8 and 0 <= y + k * dy < 8:
            out.append((x + k * dx) * 8 + y + k * dy)
            k += 1
    return out


def _build_words():
    """The list of all move words, indexed by word value. Word 0 is unused."""
    words = [None]
    for white in (True, False):
        for piece in BLOCK_PIECES:
            symbol = piece if white else piece.lower()
            for sq in range(64):
                for dest in _destinations(piece, sq):
                    words.append(MoveWord(symbol, sq, dest))
                    for captured in CAPTURED_PIECES:
                        words.append(MoveWord(symbol, sq, dest, captured if white else captured.upper()))
    for white in (True, False):
        symbol, step = ("P", 1) if white else ("p", -1)
        start_rank, ep_rank, promotion_rank = (1, 4, 6) if white else (6, 3, 1)
        opponent = str.lower if white else str.upper
        for x in range(8):
            for y in range(1, 7):
                sq = x * 8 + y
                if y == promotion_rank:
                    words.extend(MoveWord(symbol, sq, sq + step, None, p) for p in PROMOTION_PIECES)
                else:
                    if y == start_rank:
                        words.append(MoveWord(symbol, sq, sq + 2 * step))
                    words.append(MoveWord(symbol, sq, sq + step))
                for dx in (-1, 1):
                    if not 0 <= x + dx < 8:
                        continue
                    dest = (x + dx) * 8 + y + step
                    if y == promotion_rank:
                        words.extend(
                            MoveWord(symbol, sq, dest, opponent(c), p) for c in "QNBR" for p in PROMOTION_PIECES
                        )
                    else:
                        words.extend(MoveWord(symbol, sq, dest, opponent(c)) for c in CAPTURED_PIECES)
                        if y == ep_rank:
                            words.append(MoveWord(symbol, sq, dest, "ep"))
    # Castling in normal chess, then in each Chess960 start position in turn.
    for _ in range(1 + 960):
        for symbol in "Kk":
            words.append(MoveWord(symbol, castles="O-O-O"))
            words.append(MoveWord(symbol, castles="O-O"))
    return words


def placement(word):
    """The piece and square (a1=0, a2=1, ...) a word of a setup position
    stands for: every piece on every square, in the same order of pieces as
    the move words, except that pawns only get the 48 squares on ranks 2-7."""
    index = word - FIRST_PLACEMENT
    if 0 <= index < 640:
        white, index = index < 320, index % 320
        piece, sq = BLOCK_PIECES[index // 64], index % 64
    elif 640 <= index < 736:
        white, index = index < 688, (index - 640) % 48
        piece, sq = "P", index // 6 * 8 + index % 6 + 1
    else:
        raise ValueError(f"unknown setup position word {word:#06x}")
    return (piece if white else piece.lower()), sq


WORDS = _build_words()


@dataclass
class SetupPosition:
    move_number: int
    black_to_move: bool
    en_passant_file: int  # 1-8 for a-h, 0 if none
    castling: int  # bits: 1 white O-O-O, 2 white O-O, 4 black O-O-O, 8 black O-O
    pieces: list  # (piece, square), in square order

    @property
    def start_ply(self):
        return (self.move_number - 1) * 2 + self.black_to_move

    def __str__(self):
        side = "black" if self.black_to_move else "white"
        rights = [name for bit, name in ((2, "K"), (1, "Q"), (8, "k"), (4, "q")) if self.castling & bit]
        ep = "abcdefgh"[self.en_passant_file - 1] if self.en_passant_file else "-"
        placed = " ".join(piece + square_name(sq) for piece, sq in self.pieces)
        return f"move {self.move_number}, {side} to move, castling {''.join(rights) or '-'}, e.p. {ep}: {placed}"


@dataclass
class Node:
    """A move and the moves that can follow it; children[0] is the main one."""
    move: Optional[object] = None  # a MoveWord, "--" for a null move, None at the root
    children: list = field(default_factory=list)


@dataclass
class GameMoves:
    offset: int
    variant: int  # VARIANT_NORMAL or VARIANT_CHESS960
    checksum: bytes
    words: list  # the content as words, the sections from their first marker to the last ffff
    sections: dict  # section marker -> list of words
    root: Node
    move_bytes: int
    spare_bytes: int
    record_length: int

    @property
    def chess960_position(self):
        section = self.sections.get(START_POSITION_SECTION)
        return section[0] if section and self.variant == VARIANT_CHESS960 else None

    @property
    def setup_position(self):
        """The position a game from a setup position starts from, or None."""
        section = self.sections.get(START_POSITION_SECTION)
        if not section or self.variant == VARIANT_CHESS960:
            return None
        move_number, side_and_ep, castling, *pieces = section
        return SetupPosition(
            move_number, bool(side_and_ep & 0xFF), side_and_ep >> 8, castling, [placement(w) for w in pieces]
        )

    def notation(self):
        """The game as text: the main line with variations in parentheses."""
        out = []

        def line(node, ply, show_number):
            while node.children:
                first, *alternatives = node.children
                out.append(_numbered(first.move, ply, show_number))
                for alt in alternatives:
                    out.append("(" + _numbered(alt.move, ply, True))
                    line(alt, ply + 1, False)
                    out[-1] += ")"
                show_number = bool(alternatives)
                node, ply = first, ply + 1

        setup = self.setup_position
        line(self.root, setup.start_ply if setup else 0, True)
        return " ".join(out)


def _numbered(move, ply, show_number):
    number = ply // 2 + 1
    if ply % 2 == 0:
        return f"{number}.{move}"
    return f"{number}...{move}" if show_number else str(move)


def decode_word(word):
    if word == NULL_MOVE:
        return "--"
    if word < len(WORDS) and WORDS[word] is not None:
        return WORDS[word]
    raise ValueError(f"unknown move word {word:#06x}")


def parse_tree(words):
    """Builds the move tree from the words after the moves section marker, up
    to and including the END_OF_LINE that ends the last line. Returns the root
    and the number of words used."""
    root = Node()
    current, parent_of_last = root, None
    pending = []  # nodes that still have another alternative to come
    for i, word in enumerate(words):
        if word == MORE_ALTERNATIVES:
            pending.append(parent_of_last)
        elif word == END_OF_LINE:
            if not pending:
                return root, i + 1
            current = pending.pop()
        else:
            node = Node(decode_word(word))
            current.children.append(node)
            parent_of_last, current = current, node
    raise ValueError("move data ended without a final END_OF_LINE")


# The tag at 0x18 of a record says what it holds. A game's is its variant.
TEXT_TAG = bytes.fromhex("0010")
# Every guiding text's content then opens with these 2 bytes.
TEXT_VERSION = bytes.fromhex("0500")


@dataclass
class GuidingText:
    offset: int
    checksum: bytes
    contents: list  # (language, HTML as bytes); the language is a nation code, 0 for none


def checksum(data):
    """The 8 bytes a .2cbg or .2cba record stores at 0x10, a checksum of the A
    bytes of content, which is everything after the tag. Whole 8-byte blocks
    only are split
    into 8 runs of equal length, and byte i of the value (from the least
    significant) is the sum of run i, modulo 256; content shorter than 8 bytes
    is taken as it is. The value is written big-endian."""
    runs = len(data) // 8
    if runs == 0:
        sums = (data + bytes(8))[:8]
    else:
        sums = bytes(sum(data[i * runs:(i + 1) * runs]) & 0xFF for i in range(8))
    return int.from_bytes(sums, "little").to_bytes(8, "big")


class MoveDatabase:
    def __init__(self, path):
        self.path = path
        self._file = open(path, "rb")
        header = self._file.read(FILE_HEADER_SIZE)
        self.file_size = struct.unpack_from("<q", header)[0]

    @classmethod
    def for_game_file(cls, game_file):
        return cls(os.path.splitext(game_file)[0] + ".2cbg")

    def _read_record(self, offset):
        """The framing of the record at offset: the checksum, the 2-byte tag,
        the content (A bytes), A, B and the record length."""
        self._file.seek(offset)
        head = self._file.read(24)
        if head[:8] != RECORD_MAGIC:
            raise ValueError(f"no .2cbg record at offset {offset}")
        size, spare_bytes = struct.unpack_from("<ii", head, 8)
        data = self._file.read(2 + size + spare_bytes + 8)
        record_length = struct.unpack_from("<q", data, 2 + size + spare_bytes)[0]
        tag, content = data[:2], data[2:2 + size]
        stored = head[16:24]
        if stored != checksum(content):
            raise ValueError(f"wrong checksum in the record at offset {offset}")
        return stored, tag, content, size, spare_bytes, record_length

    def read_text(self, offset):
        """The contents of the guiding text whose .2cbh record holds this offset."""
        text_checksum, tag, content, *_ = self._read_record(offset)
        if tag != TEXT_TAG or content[:2] != TEXT_VERSION:
            raise ValueError(f"no guiding text at offset {offset}")
        remaining, count = struct.unpack_from("<ii", content, 2)
        if remaining != len(content) - 6:
            raise ValueError(f"guiding text at offset {offset} has the wrong length")
        pos, contents = 10, []
        for _ in range(count):
            language, length = struct.unpack_from("<ii", content, pos)
            contents.append((language, content[pos + 8:pos + 8 + length]))
            pos += 8 + length
        return GuidingText(offset, text_checksum, contents)

    def read(self, offset):
        """The moves of the game whose .2cbh record holds this offset."""
        game_checksum, tag, content, move_bytes, spare_bytes, record_length = \
            self._read_record(offset)
        if tag == TEXT_TAG:
            raise ValueError(f"a guiding text, not a game, at offset {offset}")
        words = list(struct.unpack(f"<{len(content) // 2}H", content))

        # A game's tag is its variant; the content is sections, each a marker and
        # the words up to the next marker. The moves section is last and runs to
        # the end.
        variant = struct.unpack("<H", tag)[0]
        pos, sections, root = 0, {}, None
        if variant not in (VARIANT_NORMAL, VARIANT_CHESS960):
            raise ValueError(f"unknown variant {variant:#06x} at offset {offset}")
        while root is None:
            marker = words[pos]
            pos += 1
            if marker == MOVES_SECTION:
                root, used = parse_tree(words[pos:])
                sections[marker] = words[pos:pos + used]
                pos += used
            elif marker == START_POSITION_SECTION:
                end = pos
                while words[end] < FIRST_MARKER:
                    end += 1
                sections[marker] = words[pos:end]
                pos = end
            else:
                raise ValueError(f"unknown section {marker:#06x} at offset {offset}")
        return GameMoves(
            offset, variant, game_checksum, words[:pos], sections, root, move_bytes, spare_bytes, record_length
        )

    def close(self):
        self._file.close()

    def __enter__(self):
        return self

    def __exit__(self, *exc_info):
        self.close()
