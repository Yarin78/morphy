"""Entity classes referenced by game headers in a .2cbh file.

Only a few fields are known for each entity so far; more will be added
as the formats get decoded further.
"""
from dataclasses import dataclass

from morphy.columns import default_columns
from morphy.dates import decode_date
from morphy.nations import decode_language, decode_nation
from morphy.sources import decode_source_quality
from morphy.tournaments import decode_time_control, decode_tournament_flags, decode_tournament_type

# The language a title is written in, when a title has to be picked and there
# is more than one to choose from.
PREFERRED_LANGUAGE = 42  # English


@default_columns("first_name", "last_name")
@dataclass
class Player:
    id: int
    first_name: str
    last_name: str
    # Unknown fields, in the order they appear in the record (see FORMAT.md).
    d1: int
    d2: int
    d3: int
    d4: int
    d5: int
    d6: int

    @property
    def name(self):
        """First and last name together, or "" if both are empty."""
        return " ".join(part for part in (self.first_name, self.last_name) if part)


@dataclass
class TitledEntity:
    """Base for entities that, for now, are known only by a title."""
    id: int
    title: str

    @property
    def name(self):
        return self.title or ""


@default_columns("title", "place", "date", "nation", "type", "rounds")
@dataclass
class Tournament(TitledEntity):
    place: str
    encoded_date: int
    type_byte: int  # the kind of tournament, and the time control
    team_byte: int  # bit 0 = a team tournament
    nation_value: int
    category: int
    flags_value: int
    rounds: int
    latitude: float  # of the place; 0 if not known
    longitude: float
    encoded_end_date: int

    @property
    def date(self):
        """The date the tournament started, as a PartialDate."""
        return decode_date(self.encoded_date)

    @property
    def end_date(self):
        """The date the tournament ended, as a PartialDate."""
        return decode_date(self.encoded_end_date)

    @property
    def type(self):
        """The kind of tournament, e.g. "Match"."""
        return decode_tournament_type(self.type_byte)

    @property
    def time_control(self):
        """The time control, or "" for a normal one."""
        return decode_time_control(self.type_byte)

    @property
    def team_tournament(self):
        return bool(self.team_byte & 1)

    @property
    def nation(self):
        """The nation of the place, as an IOC code."""
        return decode_nation(self.nation_value)

    @property
    def flags(self):
        """The tournament's flags, as text."""
        return decode_tournament_flags(self.flags_value)

    @property
    def coordinates(self):
        """The place's latitude and longitude, or "" if not known."""
        if not self.latitude and not self.longitude:
            return ""
        return f"{self.latitude:.4f}, {self.longitude:.4f}"


@default_columns("title", "publisher", "publication", "date", "version", "quality")
@dataclass
class Source(TitledEntity):
    publisher: str
    encoded_publication: int
    encoded_date: int
    version: int
    quality_value: int

    @property
    def publication(self):
        """The date the source was published, as a PartialDate."""
        return decode_date(self.encoded_publication)

    @property
    def date(self):
        """The date of the source itself, as a PartialDate."""
        return decode_date(self.encoded_date)

    @property
    def quality(self):
        """How much the source can be trusted, as text."""
        return decode_source_quality(self.quality_value)


@dataclass
class TextTitle(TitledEntity):
    pass


@default_columns("title")
@dataclass
class Team(TitledEntity):
    # The 5 bytes after the title; every team seen so far has them all zero,
    # so what they hold isn't known (see FORMAT.md).
    trailing: bytes


@default_columns("title", "titles")
@dataclass
class GameTag:
    """A game tag, whose title is given in one or more languages. Guiding
    texts use these for their titles too."""
    id: int
    # (language, title) pairs, in the order they appear in the record.
    localized_titles: tuple

    @property
    def title(self):
        """The English title if there is one, otherwise the first."""
        by_language = dict(self.localized_titles)
        if PREFERRED_LANGUAGE in by_language:
            return by_language[PREFERRED_LANGUAGE]
        return self.localized_titles[0][1] if self.localized_titles else ""

    @property
    def titles(self):
        """Every title with the language it is written in."""
        return ", ".join(f"{decode_language(lang)}: {text}" for lang, text in self.localized_titles)

    @property
    def name(self):
        return self.title
