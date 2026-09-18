"""GameHeaderDatabase: reads the .2cbh file holding one game header per game.

The file starts with a 192-byte header (not yet decoded), followed by
192-byte game records, game #1 first. All integers in a record are
little-endian, signed. Fields decoded so far:

    0x18  long  white player id
    0x20  long  black player id
    0x28  long  tournament id
    0x30  long  annotator id (references a player)
    0x38  long  source id
    0x40  long  white team id (-1 = none)
    0x48  long  black team id
    0x50  long  game tag id
"""
import os
import struct
from dataclasses import dataclass

HEADER_SIZE = 192
RECORD_SIZE = 192

FIELD_OFFSETS = {
    "white_id": 0x18,
    "black_id": 0x20,
    "tournament_id": 0x28,
    "annotator_id": 0x30,
    "source_id": 0x38,
    "white_team_id": 0x40,
    "black_team_id": 0x48,
    "game_tag_id": 0x50,
}


@dataclass
class GameHeader:
    id: int
    white_id: int  # references a Player
    black_id: int  # references a Player
    tournament_id: int
    annotator_id: int  # references a Player
    source_id: int
    white_team_id: int
    black_team_id: int
    game_tag_id: int


class GameHeaderDatabase:
    def __init__(self, path):
        self.path = path
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
    def _parse(recnum, data):
        values = {name: struct.unpack_from("<q", data, offset)[0] for name, offset in FIELD_OFFSETS.items()}
        return GameHeader(id=recnum, **values)

    def close(self):
        self._file.close()

    def __enter__(self):
        return self

    def __exit__(self, *exc_info):
        self.close()
