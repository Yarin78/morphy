"""The packed fields of a tournament entity: what kind of tournament it is,
and the flags that go with it. Both bytes use the v1 encodings; see
format/v2/spec/4-entities.md.
"""

# Low 5 bits of the type byte.
TOURNAMENT_TYPES = [
    "", "Game", "Match", "Tournament", "Open", "Team", "Knockout", "Simul", "Schevening",
]

# Higher bits of the same byte give the time control; no bit set means normal.
TIME_CONTROLS = {32: "Blitz", 64: "Rapid", 128: "Correspondence"}

# Tiebreak rule ids. These are not v1's ids -- v2 numbers the rules afresh --
# and every rule ChessBase offers has been seen; see the specification. Swiss and round
# robin each have their own Sonneborn-Berger.
TIEBREAK_RULES = {
    0: "Not set",
    1: "Rating of Buchholz",
    2: "Feine Buchholz",
    3: "Median Buchholz",
    4: "Fortschritt",
    5: "Sonneborn-Berger (swiss)",
    11: "# wins",
    12: "# black wins",
    13: "# black games",
    14: "Point group",
    16: "Median2 Buchholz",
    17: "Buchholz Cut 1",
    18: "Buchholz Cut 2",
    19: "Sonneborn-Berger (round robin)",
    21: "Koya",
}

# Bits of the flags byte. Bit 0 is the older form of bit 1, and from 2005 on
# the two always agree, so only bit 1 is reported as "complete".
TOURNAMENT_FLAGS = {2: "complete", 4: "board points", 8: "three points win"}


def decode_tournament_type(value):
    """The kind of tournament, e.g. "Match"."""
    index = value & 31
    if index < len(TOURNAMENT_TYPES):
        return TOURNAMENT_TYPES[index]
    return f"?{index}"


def decode_time_control(value):
    """The time control, e.g. "Blitz". Normal time control gives ""."""
    return next((name for bit, name in TIME_CONTROLS.items() if value & bit), "")


def decode_tiebreaks(values):
    """The tiebreak rules, in order, as text."""
    return ", ".join(TIEBREAK_RULES.get(value, f"?{value}") for value in values)


def decode_tournament_flags(value):
    """The flags set on a tournament, as text."""
    parts = [name for bit, name in sorted(TOURNAMENT_FLAGS.items()) if value & bit]
    rest = value & ~(sum(TOURNAMENT_FLAGS) | 1)
    if rest:
        parts.append(f"?{rest:#x}")
    return ", ".join(parts)
