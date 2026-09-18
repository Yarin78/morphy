"""GameHeaderDatabase: reads the .2cbh file holding one game header per game.

The file starts with a 192-byte header (not yet decoded), followed by
192-byte game records, game #1 first. All integers in a record are
little-endian, signed. FORMAT.md is the source of truth for the layout;
FIELDS below lists the fields decoded so far.
"""
import os
import struct

from morphy.gameheader import GameHeader

HEADER_SIZE = 192
RECORD_SIZE = 192

# The first byte of a record has this bit set if the game is deleted.
DELETED_FLAG = 0x80

# GameHeader field -> (offset in the record, how to read the value): either a
# struct format, or a number of bytes to read as a little-endian unsigned int
# (for fields that aren't a whole number of words).
FIELDS = {
    "moves_offset": (0x08, "<q"),
    "annotation_offset": (0x10, "<q"),
    "white_id": (0x18, "<q"),
    "black_id": (0x20, "<q"),
    "tournament_id": (0x28, "<q"),
    "annotator_id": (0x30, "<q"),
    "source_id": (0x38, "<q"),
    "white_team_id": (0x40, "<q"),
    "black_team_id": (0x48, "<q"),
    "game_tag_id": (0x50, "<q"),
    "result_code": (0x58, "<B"),
    "nag": (0x59, "<B"),
    "round_number": (0x5A, "<h"),
    "subround_number": (0x5C, "<h"),
    "board_number": (0x5E, "<h"),
    "white_elo_value": (0x60, "<h"),
    "white_rating_bytes": (0x62, "<14s"),
    "black_elo_value": (0x70, "<h"),
    "black_rating_bytes": (0x72, "<14s"),
    "encoded_eco": (0x80, "<H"),
    "medals_value": (0x82, "<H"),
    "flags_value": (0x84, "<I"),
    "annotation_magnitude": (0x88, "<H"),
    "moves": (0x8A, "<h"),
    "final_material_1": (0x8C, "<I"),
    "final_material_2": (0x90, "<I"),
    "creation_timestamp": (0x98, "<q"),
    "last_changed_timestamp": (0xA0, "<q"),
    "endgame_bits": (0xA8, 6),
    "version": (0xB8, "<i"),
    "encoded_played_date": (0xBC, "<i"),
}


class GameHeaderDatabase:
    def __init__(self, path, entities=None):
        """entities is the EntityDatabase that the game headers' names are
        resolved with, if wanted."""
        self.path = path
        self.entities = entities
        self._file = open(path, "rb")
        self.header = self._file.read(HEADER_SIZE)
        self.count = (os.path.getsize(path) - HEADER_SIZE) // RECORD_SIZE

    def __len__(self):
        return self.count

    def __iter__(self):
        self._file.seek(HEADER_SIZE)
        for recnum in range(1, self.count + 1):
            yield self._parse(recnum, self._file.read(RECORD_SIZE))

    def get(self, game_id):
        """Random access to a single game header by its 1-indexed id."""
        if not 1 <= game_id <= self.count:
            raise IndexError(f"no game #{game_id} (database has {self.count} games)")
        self._file.seek(HEADER_SIZE + (game_id - 1) * RECORD_SIZE)
        return self._parse(game_id, self._file.read(RECORD_SIZE))

    @staticmethod
    def _read_field(data, offset, fmt):
        if isinstance(fmt, int):
            return int.from_bytes(data[offset:offset + fmt], "little")
        return struct.unpack_from(fmt, data, offset)[0]

    def _parse(self, recnum, data):
        values = {name: self._read_field(data, offset, fmt) for name, (offset, fmt) in FIELDS.items()}
        return GameHeader(id=recnum, deleted=bool(data[0] & DELETED_FLAG), _entities=self.entities, **values)

    def close(self):
        self._file.close()

    def __enter__(self):
        return self

    def __exit__(self, *exc_info):
        self.close()
