"""The packed fields of a tournament entity: what kind of tournament it is,
and the flags that go with it. Both bytes use the v1 encodings; see FORMAT.md.
"""

# Low 5 bits of the type byte.
TOURNAMENT_TYPES = [
    "", "Game", "Match", "Tournament", "Open", "Team", "Knockout", "Simul", "Schevening",
]

# Higher bits of the same byte give the time control; no bit set means normal.
TIME_CONTROLS = {32: "Blitz", 64: "Rapid", 128: "Correspondence"}

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


def decode_tournament_flags(value):
    """The flags set on a tournament, as text."""
    parts = [name for bit, name in sorted(TOURNAMENT_FLAGS.items()) if value & bit]
    rest = value & ~(sum(TOURNAMENT_FLAGS) | 1)
    if rest:
        parts.append(f"?{rest:#x}")
    return ", ".join(parts)
