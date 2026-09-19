"""Entity classes referenced by game headers in a .2cbh file.

Only a few fields are known for each entity so far; more will be added
as the formats get decoded further.
"""
from dataclasses import dataclass

from morphy.columns import default_columns
from morphy.dates import decode_date
from morphy.nations import decode_language, decode_nation
from morphy.sources import decode_source_quality
from morphy.tournaments import (
    decode_tiebreaks, decode_time_control, decode_tournament_flags, decode_tournament_type,
)

# The language a title is written in, when a title has to be picked and there
# is more than one to choose from.
PREFERRED_LANGUAGE = 42  # English


@default_columns("first_name", "last_name")
@dataclass
class Player:
    id: int
    first_name: str
    last_name: str
    d1: int  # always 0; perhaps the length of a string that is always empty
    d2: int  # always 0; likewise
    # The player's id in ChessBase's own player database, which is what it
    # looks titles, ratings and birth dates up by. -1 = never looked up,
    # 0 = looked up but not found.
    chessbase_id: int
    fide_id_size: int  # always 8; presumably the size of the FIDE id after it
    fide_id: int  # -1 = never looked up, 0 = none

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
    place_nation_value: int  # the nation the place is in today, 0 if not known
    tiebreak_values: tuple  # tiebreak rule ids, in the order they apply
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
        """The nation at the time the tournament was played, as an IOC code.
        Historical states are used, e.g. URS for the Soviet Union."""
        return decode_nation(self.nation_value)

    @property
    def flags(self):
        """The tournament's flags, as text."""
        return decode_tournament_flags(self.flags_value)

    @property
    def tiebreaks(self):
        """The tiebreak rules, in the order they apply, as text."""
        return decode_tiebreaks(self.tiebreak_values)

    @property
    def place_nation(self):
        """The nation the place is in today, worked out from its coordinates,
        as an IOC code. Unlike nation, it doesn't depend on when the tournament
        was played: a tournament in Breslau has nation GER, but place_nation
        POL, since the city is now Wrocław."""
        return decode_nation(self.place_nation_value)

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


@default_columns("title", "team_number", "season", "year", "nation")
@dataclass
class Team(TitledEntity):
    team_number: int  # 0 = not set
    season_byte: int  # bit 0 = the year is a season spanning two years
    year: int  # 0 = not set
    nation_value: int

    @property
    def season(self):
        return bool(self.season_byte & 1)

    @property
    def nation(self):
        """The team's nation, as an IOC code."""
        return decode_nation(self.nation_value)


@default_columns("title", "titles")
@dataclass
class GameTag:
    """A game tag or the title of a guiding text -- both are this one entity
    type, and which a given entity is depends only on whether a game or a text
    refers to it. The title is given in one or more languages."""
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
