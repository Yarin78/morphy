"""EntityDatabase: reads the .2lid file that holds the entities (players,
tournaments, sources, teams, ...) a game database's .2cbh file refers to.
It shares the same base name as the .2cbh file, e.g. "foo.2cbh" pairs
with "foo.2lid".

The file layout (header, blocks, entity records) is described in
FORMAT.md, which is the source of truth for it. In short: a big-endian
header, followed by one block per entity id, each holding a fixed-size
container per entity type in the order of ENTITY_TYPE_ORDER.

The file is not read into memory all at once -- only its header is read
at construction time.
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

# Logically deleted entities form a linked list (see FORMAT.md). A deleted
# record is exactly this long and holds the id of the next deleted entity of
# its type at this offset, as a little-endian long (-1 ends the list).
DELETED_NEXT_OFFSET = 12
DELETED_RECORD_LENGTH = DELETED_NEXT_OFFSET + 8

# A tournament record ends with a fixed-size block of fields after its two
# strings, with the end date near the end of it (see FORMAT.md).
TOURNAMENT_TAIL_SIZE = 108
TOURNAMENT_END_DATE_OFFSET = 60

# The fixed-size blocks that follow the strings of a source and of a team.
SOURCE_TAIL_SIZE = 12
TEAM_TAIL_SIZE = 5

# The unknown ints at the end of a player record (see FORMAT.md).
PLAYER_UNKNOWN_FIELDS = ("d1", "d2", "d3", "d4", "d5", "d6")
PLAYER_UNKNOWN_INTS = len(PLAYER_UNKNOWN_FIELDS)


@dataclass
class EntityTypeHeader:
    container_size: int
    count: int
    first_deleted_id: int  # head of the list of logically deleted entities, -1 if empty


def _read_int(f):
    return struct.unpack(">i", f.read(4))[0]


def _read_long(f):
    return struct.unpack(">q", f.read(8))[0]


def read_header(f):
    """Read the header from a file positioned at its start. Returns
    (header_size, entity_types), with entity_types in header order."""
    header_size = _read_int(f)
    type_count = _read_int(f)
    entity_types = []
    for _ in range(type_count):
        container_size = _read_int(f)
        count = _read_long(f)
        first_deleted_id = _read_long(f)
        entity_types.append(EntityTypeHeader(container_size, count, first_deleted_id))
    return header_size, entity_types


def block_layout(entity_types):
    """Return (block_size, offsets), where offsets maps each entity type
    name to where its data starts within a block."""
    offsets = {}
    offset = 0
    for name, et in zip(ENTITY_TYPE_ORDER, entity_types):
        offsets[name] = offset
        offset += et.container_size
    return sum(et.container_size for et in entity_types), offsets


class DeletedEntityError(LookupError):
    """The requested entity is in its type's list of logically deleted entities."""


def _deleted_next_id(container):
    """Given the container of a deleted entity, return the id of the next
    deleted entity of the same type (-1 if this is the last one)."""
    record_length = struct.unpack_from("<i", container, 0)[0]
    if 4 + record_length != DELETED_RECORD_LENGTH:
        raise ValueError(f"deleted record has length {record_length}, "
                         f"expected {DELETED_RECORD_LENGTH - 4}")
    return struct.unpack_from("<q", container, DELETED_NEXT_OFFSET)[0]


def _record_of(data):
    """The bytes of an entity record, which is its leading length plus that
    many bytes. Raises ValueError if the length doesn't fit the container."""
    record_length = struct.unpack_from("<i", data, 0)[0]
    if not 0 <= record_length <= len(data) - 4:
        raise ValueError(f"record length {record_length} doesn't fit in the container")
    return data[:4 + record_length]


def _read_length_prefixed_string(data, offset):
    """A 4-byte little-endian length followed by that many UTF-8 bytes.
    Returns (text, offset of the byte after the string). Raises ValueError
    if the string doesn't fit within data."""
    if offset + 4 > len(data):
        raise ValueError(f"no room for a string length at offset {offset}")
    length = struct.unpack_from("<i", data, offset)[0]
    start = offset + 4
    if length < 0 or start + length > len(data):
        raise ValueError(f"string of length {length} at offset {offset} doesn't fit in the record")
    return data[start:start + length].decode("utf-8"), start + length


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
    """Decode a player record as described in FORMAT.md. Raises ValueError
    if the record doesn't have exactly that layout."""
    try:
        record = _record_of(data)
        last_name, offset = _read_length_prefixed_string(record, 4)
        first_name, offset = _read_length_prefixed_string(record, offset)
        if offset + PLAYER_UNKNOWN_INTS * 4 != len(record):
            raise ValueError(f"expected {PLAYER_UNKNOWN_INTS} ints after the names, "
                             f"but {len(record) - offset} bytes remain in the record")
        d = struct.unpack_from(f"<{PLAYER_UNKNOWN_INTS}i", record, offset)
    except (ValueError, struct.error) as e:
        raise ValueError(f"malformed player #{entity_id}: {e}") from e
    return Player(id=entity_id, first_name=first_name, last_name=last_name, **dict(zip(PLAYER_UNKNOWN_FIELDS, d)))


def _deserialize_titled(cls, entity_id, data):
    offset = _find_string_offset(data)
    title = _read_length_prefixed_string(data, offset)[0] if offset is not None else None
    return cls(id=entity_id, title=title)


def _deserialize_source(entity_id, data):
    """Decode a source record as described in FORMAT.md."""
    try:
        record = _record_of(data)
        title, offset = _read_length_prefixed_string(record, 4)
        publisher, offset = _read_length_prefixed_string(record, offset)
        if offset + SOURCE_TAIL_SIZE != len(record):
            raise ValueError(f"expected {SOURCE_TAIL_SIZE} bytes after the strings, "
                             f"but {len(record) - offset} remain in the record")
        publication, date, version, quality = struct.unpack_from("<iihh", record, offset)
    except (ValueError, struct.error) as e:
        raise ValueError(f"malformed source #{entity_id}: {e}") from e
    return Source(id=entity_id, title=title, publisher=publisher, encoded_publication=publication,
                  encoded_date=date, version=version, quality_value=quality)


def _deserialize_team(entity_id, data):
    """Decode a team record as described in FORMAT.md."""
    try:
        record = _record_of(data)
        title, offset = _read_length_prefixed_string(record, 4)
        if offset + TEAM_TAIL_SIZE != len(record):
            raise ValueError(f"expected {TEAM_TAIL_SIZE} bytes after the title, "
                             f"but {len(record) - offset} remain in the record")
    except (ValueError, struct.error) as e:
        raise ValueError(f"malformed team #{entity_id}: {e}") from e
    return Team(id=entity_id, title=title, trailing=record[offset:])


def _deserialize_game_tag(entity_id, data):
    """Decode a game tag record as described in FORMAT.md: a count, then that
    many titles, each with the language it is written in."""
    try:
        record = _record_of(data)
        count = struct.unpack_from("<i", record, 4)[0]
        if count < 0:
            raise ValueError(f"negative title count {count}")
        titles, offset = [], 8
        for _ in range(count):
            language = struct.unpack_from("<i", record, offset)[0]
            title, offset = _read_length_prefixed_string(record, offset + 4)
            titles.append((language, title))
        if offset != len(record):
            raise ValueError(f"{len(record) - offset} bytes left over after {count} title(s)")
    except (ValueError, struct.error) as e:
        raise ValueError(f"malformed game tag #{entity_id}: {e}") from e
    return GameTag(id=entity_id, localized_titles=tuple(titles))


def _deserialize_tournament(entity_id, data):
    """Decode a tournament record as described in FORMAT.md. Raises ValueError
    if the record doesn't have exactly that layout."""
    try:
        record = _record_of(data)
        place, offset = _read_length_prefixed_string(record, 4)
        title, offset = _read_length_prefixed_string(record, offset)
        if offset + TOURNAMENT_TAIL_SIZE != len(record):
            raise ValueError(f"expected {TOURNAMENT_TAIL_SIZE} bytes after the strings, "
                             f"but {len(record) - offset} remain in the record")
        date, type_byte, team_byte, nation, _, category, flags, rounds, _ = struct.unpack_from(
            "<i8B", record, offset)
        latitude, longitude = struct.unpack_from("<2f", record, offset + 12)
        end_date = struct.unpack_from("<i", record, offset + TOURNAMENT_END_DATE_OFFSET)[0]
    except (ValueError, struct.error) as e:
        raise ValueError(f"malformed tournament #{entity_id}: {e}") from e
    return Tournament(
        id=entity_id, title=title, place=place, encoded_date=date, type_byte=type_byte,
        team_byte=team_byte, nation_value=nation, category=category, flags_value=flags,
        rounds=rounds, latitude=latitude, longitude=longitude, encoded_end_date=end_date)


class EntityDatabase:
    def __init__(self, path):
        self.path = path
        self._file = open(path, "rb")
        self.header_size, self.entity_types = read_header(self._file)
        self._file.seek(self.header_size)

        self._block_size, self._block_offset = block_layout(self.entity_types)

        # Read the lists of deleted entities up front, so that looking up an
        # entity can tell right away whether its id is valid.
        try:
            self._deleted_ids = {
                name: tuple(self._walk_deleted(name))
                for name, _ in zip(ENTITY_TYPE_ORDER, self.entity_types)
            }
        except ValueError:
            self._file.close()
            raise
        self._deleted_sets = {name: frozenset(ids) for name, ids in self._deleted_ids.items()}

    def print_header(self):
        counts = ", ".join(f"{name}: {et.count}" for name, et in zip(ENTITY_TYPE_ORDER, self.entity_types))
        print(f"{self.path}: {counts}")

    @classmethod
    def for_game_file(cls, game_file_path):
        """Open the .2lid file that sits alongside a .2cbh (or similar) file."""
        base, _ext = os.path.splitext(game_file_path)
        return cls(base + ".2lid")

    def _read_container(self, type_name, entity_id):
        """The raw container of an entity, whether or not it is deleted."""
        type_index = ENTITY_TYPE_ORDER.index(type_name)
        et = self.entity_types[type_index]
        if not 0 <= entity_id < et.count:
            raise IndexError(f"no {type_name} #{entity_id} (count is {et.count})")
        offset = self.header_size + entity_id * self._block_size + self._block_offset[type_name]
        self._file.seek(offset)
        return self._file.read(et.container_size)

    def _read_entity_raw(self, type_name, entity_id):
        """The raw container of an entity. Raises IndexError if the id is out
        of range and DeletedEntityError if the entity is deleted."""
        if entity_id in self._deleted_sets[type_name]:
            raise DeletedEntityError(f"{type_name} #{entity_id} is deleted")
        return self._read_container(type_name, entity_id)

    def deleted_ids(self, type_name):
        """Ids of the logically deleted entities of a type, in list order:
        the header points to the first one and each deleted record points to
        the next."""
        return self._deleted_ids[type_name]

    def _walk_deleted(self, type_name):
        """Follow the list of deleted entities of a type, starting at the id
        in the header. Raises ValueError if the list is broken."""
        et = self.entity_types[ENTITY_TYPE_ORDER.index(type_name)]
        ids = []
        seen = set()
        entity_id = et.first_deleted_id
        while entity_id != -1:
            if entity_id in seen:
                raise ValueError(f"the deleted {type_name} list loops back to #{entity_id}")
            seen.add(entity_id)
            ids.append(entity_id)
            try:
                entity_id = _deleted_next_id(self._read_container(type_name, entity_id))
            except (IndexError, ValueError) as e:
                raise ValueError(f"broken deleted {type_name} list at #{ids[-1]}: {e}") from e
        return ids

    def _live_ids(self, type_name):
        """Ids of the entities of a type that aren't deleted, in id order."""
        deleted = self._deleted_sets[type_name]
        count = self.entity_types[ENTITY_TYPE_ORDER.index(type_name)].count
        return (entity_id for entity_id in range(count) if entity_id not in deleted)

    def players(self):
        """All players that aren't deleted, in id order."""
        return (self.get_player(entity_id) for entity_id in self._live_ids("player"))

    def tournaments(self):
        """All tournaments that aren't deleted, in id order."""
        return (self.get_tournament(entity_id) for entity_id in self._live_ids("tournament"))

    def sources(self):
        """All sources that aren't deleted, in id order."""
        return (self.get_source(entity_id) for entity_id in self._live_ids("source"))

    def teams(self):
        """All teams that aren't deleted, in id order."""
        return (self.get_team(entity_id) for entity_id in self._live_ids("team"))

    def game_tags(self):
        """All game tags that aren't deleted, in id order."""
        return (self.get_game_tag(entity_id) for entity_id in self._live_ids("game_tag"))

    def get_player(self, entity_id):
        return _deserialize_player(entity_id, self._read_entity_raw("player", entity_id))

    def get_tournament(self, entity_id):
        return _deserialize_tournament(entity_id, self._read_entity_raw("tournament", entity_id))

    def get_source(self, entity_id):
        return _deserialize_source(entity_id, self._read_entity_raw("source", entity_id))

    def get_text_title(self, entity_id):
        return _deserialize_titled(TextTitle, entity_id, self._read_entity_raw("text_title", entity_id))

    def get_team(self, entity_id):
        return _deserialize_team(entity_id, self._read_entity_raw("team", entity_id))

    def get_game_tag(self, entity_id):
        return _deserialize_game_tag(entity_id, self._read_entity_raw("game_tag", entity_id))

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
