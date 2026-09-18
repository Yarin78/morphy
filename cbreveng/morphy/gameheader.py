"""GameHeader: one game's record in the .2cbh file. See FORMAT.md, which is
the source of truth for the fields, and gameheaderdb.py for how they are read.

Fields that are stored encoded in the file are kept in their encoded form,
and there is a property that decodes them.
"""
from dataclasses import dataclass, field

from morphy.columns import default_columns
from morphy.dates import decode_date
from morphy.results import format_result


@default_columns(
    "white", "white_elo", "black", "black_elo", "result", "moves", "tournament", "played_date", "round"
)
@dataclass
class GameHeader:
    id: int
    deleted: bool  # the game is marked as deleted
    white_id: int  # references a Player
    black_id: int  # references a Player
    tournament_id: int
    annotator_id: int  # references a Player
    source_id: int
    white_team_id: int
    black_team_id: int
    game_tag_id: int
    result_code: int  # a GameResult enum value
    nag: int  # if the result is "line", the line evaluation
    round_number: int  # 0 = none
    subround_number: int  # 0 = none
    board_number: int  # 0 = none
    white_elo: int
    black_elo: int
    moves: int  # number of full moves in the game
    timestamp: int  # presumably when the game was saved; encoding unknown
    version: int  # increases by 1 every time the game is saved
    encoded_played_date: int
    # The EntityDatabase used to resolve the ids above into names, if any
    # (needed for the properties that resolve names).
    _entities: object = field(default=None, repr=False, compare=False)

    @property
    def result(self):
        """The result as text, or for an unfinished game (a "line") its evaluation."""
        return format_result(self.result_code, self.nag)

    @property
    def round(self):
        """The round, subround and board as "round.subround.board". Trailing
        parts that are 0 are left out, so this is "" if there is no round."""
        parts = [self.round_number, self.subround_number, self.board_number]
        while parts and parts[-1] == 0:
            parts.pop()
        return ".".join(str(part) for part in parts)

    @property
    def played_date(self):
        """The date the game was played, as a PartialDate."""
        return decode_date(self.encoded_played_date)

    def _resolve(self, getter_name, entity_id):
        """Name of the entity that entity_id refers to: "" if there is none
        (-1), and "?<id>" if it can't be found, is deleted, or has no name."""
        if entity_id < 0:
            return ""
        if self._entities is None:
            raise RuntimeError("this game header has no entity database to resolve names with")
        try:
            entity = getattr(self._entities, getter_name)(entity_id)
        except (LookupError, ValueError):
            return f"?{entity_id}"
        return entity.name or f"?{entity_id}"

    @property
    def white(self):
        return self._resolve("get_player", self.white_id)

    @property
    def black(self):
        return self._resolve("get_player", self.black_id)

    @property
    def tournament(self):
        return self._resolve("get_tournament", self.tournament_id)

    @property
    def annotator(self):
        return self._resolve("get_player", self.annotator_id)

    @property
    def source(self):
        return self._resolve("get_source", self.source_id)

    @property
    def white_team(self):
        return self._resolve("get_team", self.white_team_id)

    @property
    def black_team(self):
        return self._resolve("get_team", self.black_team_id)

    @property
    def game_tag(self):
        return self._resolve("get_game_tag", self.game_tag_id)
