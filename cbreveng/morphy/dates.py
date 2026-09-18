"""Dates and timestamps as they are stored in the database files. A date has
a year, month and day, any of which can be unknown; a timestamp is an exact
point in time. The encodings are described in FORMAT.md.
"""
import datetime as dt
from dataclasses import dataclass

# A game's creation timestamp counts 1/1024 seconds from this moment, and its
# last-changed timestamp counts 100 nanoseconds from this one. Both are the
# encodings the older format (v1) uses.
CREATION_EPOCH = dt.datetime(2008, 12, 1, tzinfo=dt.timezone(dt.timedelta(hours=1)))
LAST_CHANGED_EPOCH = dt.datetime(1582, 10, 15, tzinfo=dt.timezone.utc)


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


def _decode_timestamp(value, epoch, units_per_second):
    """A timestamp is a count of fixed units since an epoch; 0 means unset.
    The result is in UTC, truncated to whole seconds (the raw value keeps the
    full precision). Returns None for a value that isn't a date at all, which
    happens for the creation timestamp of a database ChessBase made itself --
    see FORMAT.md."""
    if not value:
        return None
    try:
        moment = epoch + dt.timedelta(seconds=value / units_per_second)
    except (OverflowError, OSError, ValueError):
        return None
    return moment.astimezone(dt.timezone.utc).replace(microsecond=0)


def decode_creation_timestamp(value):
    """When a game was created; 1/1024 seconds since 2008-12-01 in Berlin."""
    return _decode_timestamp(value, CREATION_EPOCH, 1024)


def decode_last_changed_timestamp(value):
    """When a game was last changed; 100 ns since 1582-10-15 UTC."""
    return _decode_timestamp(value, LAST_CHANGED_EPOCH, 10_000_000)
