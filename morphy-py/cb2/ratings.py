"""The rating type that goes with each elo in a game header: what kind of
rating it is, at what time control, and what it is called. See
format/v2/spec/1-game-headers.md.

v1 keeps the same idea in 16 bytes (`RatingType`), but only knows about
international and national ratings; the server ratings are new here.
"""
import struct
from dataclasses import dataclass

from cb2.nations import decode_nation

# Bottom 3 bits of the first field, as in v1, which masks the same way.
RATING_KINDS = {1: "International", 2: "National", 3: "Server"}

# The rest of that field. Bullet is new here; v1 has no such time control.
RATING_TIME_CONTROLS = ["Normal", "Bullet", "Blitz", "Rapid", "Correspondence"]

KIND_MASK = 7
TIME_CONTROL_SHIFT = 3

# The name is a fixed 8 bytes with no terminator when it fills them, so
# ChessBase itself truncates "chess.com" to "chess.co".
NAME_SIZE = 8
RATING_TYPE_SIZE = 6 + NAME_SIZE

# How ChessBase writes each rating list next to an elo, keyed by the name the
# record stores. Anything not listed is shown as stored.
RATING_DISPLAY_NAMES = {
    "FIDE": "ELO",
    "ICCF": "corr",
    "CB": "ChessBase",
    "chess.co": "CC",
    "LiChess": "LiC",
}

# Time controls that are not spelled out: normal because it is the default,
# correspondence because the name ("corr") already says it.
UNWRITTEN_TIME_CONTROLS = {"Normal", "Correspondence"}

# An international rating at normal time control, which is what a game gets
# when nothing is chosen, so nothing about it is worth showing.
PLAIN_KIND_VALUE = 1


@dataclass(frozen=True)
class RatingType:
    kind_value: int  # kind in the bottom 3 bits, time control above them
    subtype: int  # which rating list this is; see the specification
    nation_value: int  # the nation for a national rating, "Internet" for a server one
    name: str  # e.g. "FIDE" or "LiChess"; empty for a national rating

    @property
    def kind(self):
        value = self.kind_value & KIND_MASK
        return RATING_KINDS.get(value, f"?{value}")

    @property
    def time_control(self):
        value = self.kind_value >> TIME_CONTROL_SHIFT
        if value < len(RATING_TIME_CONTROLS):
            return RATING_TIME_CONTROLS[value]
        return f"?{value}"

    @property
    def nation(self):
        return decode_nation(self.nation_value)

    def __str__(self):
        """What the rating is, e.g. "FIDE (Normal)" or "National GBR (Blitz)"."""
        if not self.kind_value:
            return ""
        what = self.name or f"{self.kind} {self.nation}".strip()
        return f"{what} ({self.time_control})"


def format_elo(elo, rating):
    """An elo together with whatever its rating type adds to it, the way
    ChessBase shows it in a game list: "2000-LiC-Rapid (NET)". An ordinary
    international rating at normal time control is just the number, and an elo
    of 0, which means there is no rating, is nothing at all."""
    if not elo:
        return ""
    text = str(elo)
    if rating.name and rating.kind_value != PLAIN_KIND_VALUE:
        text += "-" + RATING_DISPLAY_NAMES.get(rating.name, rating.name)
    if rating.time_control not in UNWRITTEN_TIME_CONTROLS:
        text += "-" + rating.time_control
    return f"{text} ({rating.nation})" if rating.nation_value else text


def decode_rating_type(data):
    """Decode the 14 bytes that follow an elo in a game header."""
    kind, subtype, nation = struct.unpack_from("<3h", data, 0)
    name = data[6:6 + NAME_SIZE].split(b"\0")[0].decode("utf-8", "replace")
    return RatingType(kind_value=kind, subtype=subtype, nation_value=nation, name=name)
