"""GameHeader: one game's record in the .2cbh file. See
format/v2/1-game-headers.md, which is the source of truth for the fields,
and gameheaderdb.py for how they are read.

Fields that are stored encoded in the file are kept in their encoded form,
and there is a property that decodes them.
"""
from dataclasses import dataclass, field

from cb2.columns import default_columns
from cb2.dates import decode_creation_timestamp, decode_date, decode_last_changed_timestamp
from cb2.eco import decode_eco
from cb2.flags import decode_annotation_magnitudes, decode_flags, decode_medals
from cb2.material import decode_endgame_types, decode_material
from cb2.ratings import decode_rating_type, format_elo
from cb2.results import format_result


@default_columns(
    "white", "white_elo", "black", "black_elo", "result", "moves", "tournament", "played_date", "round"
)
@dataclass
class GameHeader:
    id: int
    deleted: bool = False  # the game is marked as deleted
    guiding_text: bool = False  # a text document rather than a game
    moves_offset: int = 0  # where the moves, or the text, are in the .2cbg file
    annotation_offset: int = 0  # where the annotations are in the .2cba file
    media_offset: int = 0  # only guiding texts have one
    white_id: int = -1  # references a Player
    black_id: int = -1  # references a Player
    tournament_id: int = -1
    annotator_id: int = -1  # references a Player; the author of a guiding text
    source_id: int = -1
    white_team_id: int = -1
    black_team_id: int = -1
    game_tag_id: int = -1  # a guiding text keeps its title here
    result_code: int = 0  # a GameResult enum value
    nag: int = 0  # if the result is "line", the line evaluation
    round_number: int = 0  # 0 = none
    subround_number: int = 0  # 0 = none
    board_number: int = 0  # 0 = none
    white_elo_value: int = 0
    white_rating_bytes: bytes = b"\0" * 14  # the rating type that goes with the elo
    black_elo_value: int = 0
    black_rating_bytes: bytes = b"\0" * 14
    encoded_eco: int = 0
    medals_value: int = 0  # bitmask
    flags_value: int = 0  # bitmask, mostly saying what annotations the game has
    annotation_magnitude_value: int = 0  # bitmask giving rough sizes for those annotations
    moves: int = 0  # number of full moves in the game
    final_material_1: int = 0  # material left at the end, for one player ...
    final_material_2: int = 0  # ... and for the other
    creation_timestamp: int = 0
    last_changed_timestamp: int = 0
    endgame_bits: int = 0  # bitmask of the endgame types the game passed through
    version: int = 0  # increases by 1 every time the game is saved
    encoded_played_date: int = 0
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

    @property
    def white_rating(self):
        """What kind of rating white's elo is."""
        return decode_rating_type(self.white_rating_bytes)

    @property
    def black_rating(self):
        return decode_rating_type(self.black_rating_bytes)

    @property
    def white_elo(self):
        """White's elo, with whatever its rating type adds to it."""
        return format_elo(self.white_elo_value, self.white_rating)

    @property
    def black_elo(self):
        return format_elo(self.black_elo_value, self.black_rating)

    @property
    def eco(self):
        """The ECO opening code as text, e.g. "D11"."""
        return decode_eco(self.encoded_eco)

    @property
    def medals(self):
        """The medals given to the game, as text."""
        return decode_medals(self.medals_value)

    @property
    def flags(self):
        """The game's flags, as text."""
        return decode_flags(self.flags_value)

    @property
    def annotation_magnitude(self):
        """How much of each kind of annotation the game has, as text."""
        return decode_annotation_magnitudes(self.annotation_magnitude_value)

    @property
    def final_material(self):
        """The material both players had left at the end, e.g. "R2P : R1P"."""
        return f"{decode_material(self.final_material_1)} : {decode_material(self.final_material_2)}"

    @property
    def endgame_types(self):
        """The endgame types the game passed through, as text."""
        return decode_endgame_types(self.endgame_bits)

    @property
    def created(self):
        """When the game was created (UTC), or None if not set."""
        return decode_creation_timestamp(self.creation_timestamp)

    @property
    def last_changed(self):
        """When the game was last changed (UTC), or None if it never was."""
        return decode_last_changed_timestamp(self.last_changed_timestamp)

    def _resolve(self, getter_name, entity_id):
        """Name of the entity that entity_id refers to. Gives "" when there is
        no entity (-1) and when the entity has no name, which is how a game
        with no game tag is stored: it refers to game tag 0, whose title is
        empty. Gives "?<id>" when the entity can't be read or is deleted, so
        that a real problem doesn't pass unnoticed."""
        if entity_id < 0:
            return ""
        if self._entities is None:
            raise RuntimeError("this game header has no entity database to resolve names with")
        try:
            entity = getattr(self._entities, getter_name)(entity_id)
        except (LookupError, ValueError):
            return f"?{entity_id}"
        return entity.name

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
