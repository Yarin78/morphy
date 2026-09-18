"""Entity classes referenced by game headers in a .2cbh file.

Only a few fields are known for each entity so far; more will be added
as the formats get decoded further.
"""
from dataclasses import dataclass


@dataclass
class Player:
    id: int
    first_name: str
    last_name: str


@dataclass
class TitledEntity:
    """Base for entities that, for now, are known only by a title."""
    id: int
    title: str


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
