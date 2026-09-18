"""EntityDatabase: reads the .2lid file that holds the entities (players,
tournaments, sources, teams, ...) a game database's .2cbh file refers to.
It shares the same base name as the .2cbh file, e.g. "foo.2cbh" pairs
with "foo.2lid".

The file is not read into memory all at once -- only its header is read
at construction time. The header layout is:

    4 bytes   int   total header size
    4 bytes   int   number of entity types in the file
    for each entity type, in order:
        4 bytes   int    container size (bytes per entity record)
        8 bytes   long   number of entities of this type in the file
        8 bytes   long   id of a logically deleted entity (-1 = none)
    (any remaining bytes, up to the declared header size, are unused)

All integers here are big-endian, signed (unlike the little-endian
records in the .2cbh game file).

After the header, the file is a sequence of fixed-size "blocks", one
per entity id, starting right after the header. Within a block, each
entity type's data appears back to back, in this fixed order:

    player, tournament, source, text_title, team, game_tag

sized according to each type's container size from the header. So a
block's total size is the sum of all container sizes, and a given
entity type's data within a block starts at the sum of the container
sizes of the types before it.
"""
import argparse
import os
import struct
from dataclasses import dataclass

from morphy.entities import GameTag, Player, Source, Team, TextTitle, Tournament

# Order entity types appear within a block, matching the header's entity
# type list.
ENTITY_TYPE_ORDER = ["player", "tournament", "source", "text_title", "team", "game_tag"]

# How far into a record to look for a length-prefixed string. Each entity
# type has some number of unknown leading fields before its name/title
# string(s), so the exact offset isn't hardcoded -- we scan for it instead.
STRING_SCAN_RANGE = 64
MIN_STRING_LENGTH = 2


@dataclass
class EntityTypeHeader:
    container_size: int
    count: int
    deleted_id: int  # -1 if there is no logically deleted entity


def _read_length_prefixed_string(data, offset):
    """A 4-byte little-endian length followed by that many UTF-8 bytes."""
    length = struct.unpack_from("<i", data, offset)[0]
    start = offset + 4
    text = data[start:start + length].decode("utf-8")
    return text, start + length


def _find_string_offset(data, start=0):
    """Scan for the first plausible length-prefixed UTF-8 string, since we
    don't know the exact field layout leading up to it yet."""
    end = min(STRING_SCAN_RANGE, len(data) - 4)
    for offset in range(start, end, 4):
        length = struct.unpack_from("<i", data, offset)[0]
        if length < MIN_STRING_LENGTH or offset + 4 + length > len(data):
            continue
        try:
            text = data[offset + 4:offset + 4 + length].decode("utf-8")
        except UnicodeDecodeError:
            continue
        if text.isprintable():
            return offset
    return None


def _deserialize_player(entity_id, data):
    offset = _find_string_offset(data)
    if offset is None:
        return Player(id=entity_id, first_name=None, last_name=None)
    last_name, offset = _read_length_prefixed_string(data, offset)
    first_name, _ = _read_length_prefixed_string(data, offset)
    return Player(id=entity_id, first_name=first_name, last_name=last_name)


def _deserialize_titled(cls, entity_id, data):
    offset = _find_string_offset(data)
    title = _read_length_prefixed_string(data, offset)[0] if offset is not None else None
    return cls(id=entity_id, title=title)


class EntityDatabase:
    def __init__(self, path):
        self.path = path
        self._file = open(path, "rb")
        self.header_size = self._read_int()
        type_count = self._read_int()
        self.entity_types = [self._read_entity_type_header() for _ in range(type_count)]
        self._file.seek(self.header_size)
        self.print_header()

        self._block_size = sum(et.container_size for et in self.entity_types)
        self._block_offset = {}
        offset = 0
        for name, et in zip(ENTITY_TYPE_ORDER, self.entity_types):
            self._block_offset[name] = offset
            offset += et.container_size

    def print_header(self):
        counts = ", ".join(f"{name}: {et.count}" for name, et in zip(ENTITY_TYPE_ORDER, self.entity_types))
        print(f"{self.path}: {counts}")

    @classmethod
    def for_game_file(cls, game_file_path):
        """Open the .2lid file that sits alongside a .2cbh (or similar) file."""
        base, _ext = os.path.splitext(game_file_path)
        return cls(base + ".2lid")

    def _read_int(self):
        return struct.unpack(">i", self._file.read(4))[0]

    def _read_long(self):
        return struct.unpack(">q", self._file.read(8))[0]

    def _read_entity_type_header(self):
        container_size = self._read_int()
        count = self._read_long()
        deleted_id = self._read_long()
        return EntityTypeHeader(container_size, count, deleted_id)

    def _read_entity_raw(self, type_name, entity_id):
        type_index = ENTITY_TYPE_ORDER.index(type_name)
        et = self.entity_types[type_index]
        if not 0 <= entity_id < et.count:
            raise IndexError(f"no {type_name} #{entity_id} (count is {et.count})")
        offset = self.header_size + entity_id * self._block_size + self._block_offset[type_name]
        self._file.seek(offset)
        return self._file.read(et.container_size)

    def get_player(self, entity_id):
        return _deserialize_player(entity_id, self._read_entity_raw("player", entity_id))

    def get_tournament(self, entity_id):
        return _deserialize_titled(Tournament, entity_id, self._read_entity_raw("tournament", entity_id))

    def get_source(self, entity_id):
        return _deserialize_titled(Source, entity_id, self._read_entity_raw("source", entity_id))

    def get_text_title(self, entity_id):
        return _deserialize_titled(TextTitle, entity_id, self._read_entity_raw("text_title", entity_id))

    def get_team(self, entity_id):
        return _deserialize_titled(Team, entity_id, self._read_entity_raw("team", entity_id))

    def get_game_tag(self, entity_id):
        return _deserialize_titled(GameTag, entity_id, self._read_entity_raw("game_tag", entity_id))

    def close(self):
        self._file.close()

    def __enter__(self):
        return self

    def __exit__(self, *exc_info):
        self.close()


def main():
    parser = argparse.ArgumentParser(prog="entitydb", description="Print the header of a .2lid entity database file")
    parser.add_argument("file")
    args = parser.parse_args()
    with EntityDatabase(args.file):
        pass


if __name__ == "__main__":
    main()
