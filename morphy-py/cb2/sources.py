"""The packed fields of a source entity. The values are the v1 ones; see
format/v2/spec/4-entities.md.
"""

# The quality byte, which says how much a source can be trusted.
SOURCE_QUALITIES = ["", "high", "medium", "low"]


def decode_source_quality(value):
    """The quality of a source, "" if not set, and "?<value>" if the value is
    outside the table."""
    if 0 <= value < len(SOURCE_QUALITIES):
        return SOURCE_QUALITIES[value]
    return f"?{value}"
