"""Dates as they are stored in the database files, where any of the year,
month and day can be unknown. The encoding is described in FORMAT.md.
"""
from dataclasses import dataclass


@dataclass(frozen=True)
class PartialDate:
    """A date where a part that is 0 is unknown."""
    year: int
    month: int
    day: int

    def __str__(self):
        """The date in PGN format, e.g. "2026.09.16" or "1837.??.??"."""
        year = f"{self.year:04d}" if self.year else "????"
        month = f"{self.month:02d}" if self.month else "??"
        day = f"{self.day:02d}" if self.day else "??"
        return f"{year}.{month}.{day}"


def decode_date(value):
    """Decode a date stored as an int: bits 0-4 are the day, bits 5-8 the
    month and bits 9-20 the year. Higher bits are ignored."""
    value %= 1 << 21
    return PartialDate(year=value // 512, month=(value // 32) % 16, day=value % 32)
