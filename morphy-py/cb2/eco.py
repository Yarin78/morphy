"""ECO opening codes as they are stored in a game header. The encoding is
described in format/v2/spec/1-game-headers.md and is the same as in the older format (v1).
"""

# An ECO code is a letter A-E and two digits, so 500 codes in all.
ECO_COUNT = 500
SUB_ECO_BASE = 128

# An encoded value this large is a Chess960 start position instead of an ECO.
CHESS960_LIMIT = 65536 - 960


def decode_eco(value):
    """Decode a stored ECO to text such as "D11", or "D11/02" when a sub-ECO
    is set. Returns "" if no ECO is stored, and "?<value>" if the value is out
    of range (a Chess960 start position is shown as "Chess960 #<n>")."""
    if value >= CHESS960_LIMIT:
        return f"Chess960 #{value - CHESS960_LIMIT}"
    code, sub = value // SUB_ECO_BASE - 1, value % SUB_ECO_BASE
    if code < 0:
        return ""
    if code >= ECO_COUNT:
        return f"?{value}"
    text = f"{chr(ord('A') + code // 100)}{code % 100:02d}"
    return f"{text}/{sub:02d}" if sub else text
