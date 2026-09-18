"""Entity classes referenced by game headers in a .2cbh file.

Only a few fields are known for each entity so far; more will be added
as the formats get decoded further.
"""
from dataclasses import dataclass

from morphy.columns import default_columns


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


@dataclass
class Tournament(TitledEntity):
    pass


@dataclass
class Source(TitledEntity):
    pass


@dataclass
class TextTitle(TitledEntity):
    pass


@dataclass
class Team(TitledEntity):
    pass


@dataclass
class GameTag(TitledEntity):
    pass
