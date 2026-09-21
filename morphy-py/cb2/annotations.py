"""AnnotationDatabase: reads the .2cba file holding the annotations of every game.

Each game's record starts at the offset at 0x10 of its .2cbh record, and has
the same framing as a .2cbg record. The content is a list of positions, each
with the annotations on that move. format/v2/3-annotations.md is the
source of truth for the layout.

A position is -1 for the game as a whole (before the first move), or the index
of a move in the order a PGN would list them (see pgn_order()), not the order
the moves are stored in.
"""
import os
import struct
from dataclasses import dataclass

from cb2.moves import RECORD_MAGIC, checksum, square_name

CONTENT_TAG = b"\x00\x20"
END_OF_ANNOTATIONS = 0x7FFFFFFF
GAME_POSITION = -1

# The language of a text annotation, as a small number (not a nation code).
LANGUAGES = {0: "ENG", 1: "GER", 2: "FRA", 3: "ESP", 4: "ITA", 5: "NED", 6: "POR", 7: "all"}

TYPE_NAMES = {
    0x02: "text after",
    0x03: "symbols",
    0x04: "squares",
    0x05: "arrows",
    0x07: "time spent",
    0x08: "type 08",
    0x09: "training",
    0x16: "white clock",
    0x17: "black clock",
    0x1C: "web link",
    0x21: "computer evaluation",
    0x13: "game quotation",
    0x14: "pawn structure",
    0x15: "piece path",
    0x18: "critical position",
    0x22: "medals",
    0x23: "variation color",
    0x24: "time control",
    0x25: "video stream time",
    0x26: "evaluations",
    0x82: "text before",
}

COLORS = {2: "green", 3: "yellow", 4: "red"}


@dataclass
class Annotation:
    position: int
    type: int
    data: bytes  # the bytes after the type, as stored

    @property
    def name(self):
        return TYPE_NAMES.get(self.type, f"type {self.type:#04x}")

    def describe(self):
        """The annotation's content as text, as far as it is understood."""
        describe = _DESCRIBE.get(self.type)
        return describe(self.data) if describe else self.data.hex(" ")


def _items_length(data, pos):
    """End of a list of items in a training annotation: a short count, then for
    each a short kind, an int length and that many bytes. Kind 0 is a text, 7
    arrows and 8 coloured squares, each in the same form as the annotation of
    that name."""
    count = struct.unpack_from("<H", data, pos)[0]
    pos += 2
    for _ in range(count):
        pos += 6 + struct.unpack_from("<i", data, pos + 2)[0]
    return pos


def _training_length(data, pos):
    # Four lists in a row — the question and three kinds of response — then the
    # solutions. The last three lists are usually empty, six zero bytes in all.
    end = pos + 12
    for _ in range(4):
        end = _items_length(data, end)
    solutions = data[end]
    end += 1
    for _ in range(solutions):
        end = _items_length(data, end + 4)
    return end - pos


def _quotation_length(data, pos):
    end = pos + 10
    for _ in range(6):  # white last and first name, black last and first name, site, event
        end += 1 + data[end]
    end += 35 + 44
    for _ in range(2):  # the rating type of each player
        end += 5
        end += 4 + struct.unpack_from("<i", data, end)[0]
    end += 29
    moves = struct.unpack_from("<i", data, end)[0]
    return end + 4 + 5 * moves + 4 - pos


def _web_link_length(data, pos):
    """Length of a web link: a byte, then two strings, the URL and a caption."""
    end = pos + 1
    for _ in range(2):
        end += 4 + struct.unpack_from("<i", data, end)[0]
    return end - pos


def _int_prefixed(data, pos):
    return 4 + struct.unpack_from("<i", data, pos)[0]


def _text_length(data, pos):
    return 8 + struct.unpack_from("<i", data, pos + 4)[0]


# How many bytes follow the type, given the content and the position after it.
_LENGTHS = {
    0x02: _text_length,
    0x82: _text_length,
    0x03: lambda data, pos: 3,
    0x04: _int_prefixed,
    0x05: _int_prefixed,
    0x07: lambda data, pos: 4,
    0x08: lambda data, pos: 4,
    0x09: _training_length,
    0x16: lambda data, pos: 4,
    0x17: lambda data, pos: 4,
    0x1C: _web_link_length,
    0x21: lambda data, pos: 6,
    0x13: _quotation_length,
    0x14: lambda data, pos: 1,
    0x15: _int_prefixed,
    0x18: lambda data, pos: 1,
    0x22: lambda data, pos: 4,
    0x23: lambda data, pos: 4,
    0x24: lambda data, pos: 38,
    0x25: lambda data, pos: 4,
    0x26: lambda data, pos: 5 + struct.unpack_from("<i", data, pos + 1)[0],
}


def _describe_text(data):
    language = struct.unpack_from("<H", data, 2)[0]
    # Comments are stored as they came, in UTF-8 or cp1252, with nothing to say which.
    try:
        text = data[8:].decode("utf-8")
    except UnicodeDecodeError:
        text = data[8:].decode("cp1252", errors="replace")
    text = text.strip().replace("\r\n", " / ")
    return f"[{LANGUAGES.get(language, language)}] {text}"


def _describe_squares(data):
    body = data[4:]
    return ", ".join(f"{COLORS.get(body[i], body[i])} {square_name(body[i + 1] - 1)}" for i in range(0, len(body), 2))


def _describe_arrows(data):
    body = data[4:]
    return ", ".join(
        f"{COLORS.get(body[i], body[i])} {square_name(body[i + 1] - 1)}-{square_name(body[i + 2] - 1)}"
        for i in range(0, len(body), 3)
    )


def _describe_evaluations(data):
    count = struct.unpack_from("<H", data, 5)[0]
    entries = []
    for i in range(count):
        value, depth, flag = struct.unpack_from("<hBB", data, 7 + 4 * i)
        if flag == 0xFF:
            entries.append("-")
        elif flag == 1:
            entries.append(f"#{value}/{depth}")
        else:
            entries.append(f"{value / 100:+.2f}/{depth}")
    return " ".join(entries)


def _describe_clock(data):
    """The time left on a player's clock, an int of hundredths of a second."""
    seconds = struct.unpack("<I", data)[0] // 100
    return f"{seconds // 3600}:{seconds // 60 % 60:02}:{seconds % 60:02}"


def _describe_computer_evaluation(data):
    """One engine evaluation: the score, what kind it is, and the depth."""
    score, kind, depth = struct.unpack("<hhh", data)
    shown = f"#{score}" if kind == 1 else f"{score / 100:+.2f}"
    return f"{shown}/{depth}" + (f" (kind {kind})" if kind not in (0, 1) else "")


def _describe_time_control(data):
    series = []
    for i in range(3):
        initial, increment, moves, kind = struct.unpack_from("<iihB", data, 1 + 11 * i)
        series.append(f"{initial / 100:g}s+{increment / 100:g}s for {moves} moves ({kind})")
    return "; ".join(series)


_DESCRIBE = {
    0x02: _describe_text,
    0x82: _describe_text,
    0x03: lambda data: " ".join(f"{b}" for b in data),
    0x04: _describe_squares,
    0x05: _describe_arrows,
    0x07: lambda data: "{3}:{2:02}:{1:02}".format(*data),  # unknown, s, m, h
    0x16: _describe_clock,
    0x17: _describe_clock,
    0x18: lambda data: {1: "opening", 2: "middlegame", 3: "endgame"}.get(data[0], str(data[0])),
    0x21: _describe_computer_evaluation,
    0x22: lambda data: f"{struct.unpack('<I', data)[0]:#x}",
    0x24: _describe_time_control,
    0x26: _describe_evaluations,
}


def pgn_order(root):
    """The nodes of a move tree (see cb2.moves) in the order annotation
    positions count them: each alternative, with all that follows it, right
    after the move it is an alternative to. The root comes first, at -1."""
    out = [root]

    def line(node):
        while node.children:
            first, *alternatives = node.children
            out.append(first)
            for alternative in alternatives:
                out.append(alternative)
                line(alternative)
            node = first

    line(root)
    return out


class AnnotationDatabase:
    def __init__(self, path):
        self.path = path
        self._file = open(path, "rb")

    @classmethod
    def for_game_file(cls, game_file):
        return cls(os.path.splitext(game_file)[0] + ".2cba")

    def read(self, offset):
        """The annotations of the game whose .2cbh record holds this offset."""
        self._file.seek(offset)
        head = self._file.read(24)
        if head[:8] != RECORD_MAGIC:
            raise ValueError(f"no .2cba record at offset {offset}")
        size = struct.unpack_from("<i", head, 8)[0]
        data = self._file.read(2 + size)
        tag, content = data[:2], data[2:]
        if head[16:24] != checksum(content):
            raise ValueError(f"wrong checksum in the record at offset {offset}")
        if tag != CONTENT_TAG:
            raise ValueError(f"unexpected annotation tag at offset {offset}")
        pos, annotations = 0, []
        while True:
            position = struct.unpack_from("<i", content, pos)[0]
            pos += 4
            if position == END_OF_ANNOTATIONS:
                break
            count = struct.unpack_from("<i", content, pos)[0]
            pos += 4
            for _ in range(count):
                kind = struct.unpack_from("<H", content, pos)[0]
                pos += 2
                length = _LENGTHS.get(kind)
                if length is None:
                    raise ValueError(f"unknown annotation type {kind:#04x} at offset {offset}")
                n = length(content, pos)
                annotations.append(Annotation(position, kind, content[pos:pos + n]))
                pos += n
        return annotations

    def close(self):
        self._file.close()

    def __enter__(self):
        return self

    def __exit__(self, *exc_info):
        self.close()
