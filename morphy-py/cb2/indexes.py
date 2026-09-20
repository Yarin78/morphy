"""Readers for the two index files that go with the .2lid entity file.

- SortIndexes reads the .2lcd file: the sort order of each kind of entity, as
  an AVL tree per index, where a node's id is the entity id.
- GameLists reads the .2lgd file: for every entity, the games that refer to
  it in each role (as a player, a tournament, ...).

format/v2/spec/5-indexes.md is the source of truth for both layouts.
"""
import mmap
import os
import struct
from dataclasses import dataclass

# .2lcd: big-endian header and catalog, little-endian nodes.
PAGE_SIZE = 4096
CATALOG_PAGE = 1
CATALOG_ENTRY_SIZE = 128
NODE_SIZE = 40
NODES_PER_PAGE = PAGE_SIZE // NODE_SIZE
PAGES_PER_DIRECTORY = PAGE_SIZE // 4

# .2lid entity types whose keys hold more than text.
TOURNAMENT_TYPE = 1
GAME_TAG_TYPE = 5

# .2lgd: a 12-byte header, then 512-byte records.
LISTS_HEADER_SIZE = 12
LISTS_RECORD_SIZE = 512
SINGLE_FLAG = 0x4000000000000000
RANGE_FLAG = 0x2000000000000000
VALUE_MASK = 0x0FFFFFFFFFFFFFFF

# The roles a game list is kept for; the number is the list's slot in a record.
ROLES = {
    1: "player",
    2: "tournament",
    3: "source",
    4: "annotator",
    5: "team",
    6: "game tag",
    7: "text title",
    8: "analysis title",
}


@dataclass
class IndexNode:
    id: int  # the entity id
    left: int
    right: int
    parent: int
    key: bytes  # 8 bytes, in comparison (big-endian) order; all zero if not set
    balance: int
    slot: int  # the node's slot in its page (id mod 102), or -1 (only ever on a leaf)

    def key_text(self, entity_type):
        """The key shown as text: a tournament's starts with its year, and a
        game tag's with the language (as a nation code) of its first title."""
        if self.key == bytes(7) + b"\x01":
            return "(empty entity)"
        if not any(self.key):
            return "(no key)"
        if entity_type == TOURNAMENT_TYPE:
            year = int.from_bytes(self.key[:2], "big")
            return f"{year or '????'} {self.key[2:].rstrip(bytes(1)).decode('latin-1')}"
        if entity_type == GAME_TAG_TYPE:
            return f"{self.key[0]}: {self.key[1:].rstrip(bytes(1)).decode('latin-1')}"
        return self.key.rstrip(bytes(1)).decode("latin-1")


@dataclass
class SortIndex:
    name: str
    entity_type: int  # the .2lid entity type, 0-based (players 0, tournaments 1, ...)
    number: int  # `?` 1-6, the same for game tags, text titles and analysis titles
    root: int
    count: int
    page: int  # the only node page, or the top directory page
    depth: int  # levels of directory pages above the node pages


class SortIndexes:
    def __init__(self, path):
        self.path = path
        with open(path, "rb") as f:
            self.data = f.read()
        _, self.page_count, page_size = struct.unpack_from(">iii", self.data, 0)
        if page_size != PAGE_SIZE:
            raise ValueError(f"unexpected page size {page_size} in {path}")
        self.indexes = self._read_catalog()

    @classmethod
    def for_game_file(cls, game_file):
        return cls(os.path.splitext(game_file)[0] + ".2lcd")

    def _page(self, number):
        return self.data[number * PAGE_SIZE:(number + 1) * PAGE_SIZE]

    def _read_catalog(self):
        catalog = self._page(CATALOG_PAGE)
        indexes = []
        for offset in range(0, PAGE_SIZE, CATALOG_ENTRY_SIZE):
            entry = catalog[offset:offset + CATALOG_ENTRY_SIZE]
            depth, page, root, count = struct.unpack_from(">hiqq", entry, 0)
            name_length = struct.unpack_from(">H", entry, 26)[0]
            if not name_length:
                continue
            name = entry[28:28 + name_length].decode("latin-1")
            indexes.append(SortIndex(name, entry[22] - 1, entry[24], root, count, page, depth))
        return indexes

    def node_page(self, index, node_id):
        """The page holding a node: the node pages are numbered from 0, 102
        nodes each, and found through `depth` levels of directory pages of
        1024 page numbers each. A directory entry of 0 means no id in that
        range is in the index."""
        position, page = node_id // NODES_PER_PAGE, index.page
        for level in range(index.depth - 1, -1, -1):
            span = PAGES_PER_DIRECTORY ** level
            page = struct.unpack_from("<i", self.data, page * PAGE_SIZE + 4 * (position // span))[0]
            if page == 0:
                raise KeyError(f"node {node_id} is not in the index {index.name!r}")
            position %= span
        return page

    def node(self, index, node_id):
        offset = self.node_page(index, node_id) * PAGE_SIZE + (node_id % NODES_PER_PAGE) * NODE_SIZE
        left, right, parent = struct.unpack_from("<qqq", self.data, offset)
        key = self.data[offset + 24:offset + 32][::-1]
        balance, _, slot = struct.unpack_from("<hhi", self.data, offset + 32)
        return IndexNode(node_id, left, right, parent, key, balance, slot)

    def sorted_nodes(self, index):
        """The nodes of an index in sort order (an in-order walk of the tree)."""
        out, stack, node_id = [], [], index.root
        while stack or node_id != -1:
            while node_id != -1:
                node = self.node(index, node_id)
                stack.append(node)
                node_id = node.left
            node = stack.pop()
            out.append(node)
            node_id = node.right
        return out

    def get(self, name):
        for index in self.indexes:
            if index.name == name:
                return index
        raise KeyError(name)


@dataclass
class GameList:
    role: str
    count: int
    games: list  # game ids; a game referring to the entity twice is listed twice
    form: str  # "single" (one or two ids inline), "range" or "blocks"


class GameLists:
    def __init__(self, path):
        self.path = path
        with open(path, "rb") as f:
            self.data = mmap.mmap(f.fileno(), 0, access=mmap.ACCESS_READ)
        _, self.blocks_used, _ = struct.unpack_from("<iii", self.data, 0)
        self.record_count = (len(self.data) - LISTS_HEADER_SIZE) // LISTS_RECORD_SIZE

    @classmethod
    def for_game_file(cls, game_file):
        return cls(os.path.splitext(game_file)[0] + ".2lgd")

    def _slots(self, record):
        offset = LISTS_HEADER_SIZE + record * LISTS_RECORD_SIZE
        return struct.unpack_from("<64q", self.data, offset)

    def lists(self, entity_id):
        """The game lists of the entities with this id, one per role that has games."""
        if entity_id >= self.record_count:
            return []
        slots = self._slots(entity_id)
        out = []
        for slot, role in ROLES.items():
            first, last, count = slots[slot], slots[10 + slot], slots[20 + slot]
            if first == -1:
                continue
            if first & SINGLE_FLAG:
                games = [first & VALUE_MASK] + ([last] if last != -1 else [])
                form = "single"
            elif first & RANGE_FLAG:
                games = list(range(first & VALUE_MASK, (last & VALUE_MASK) + 1))
                form = "range"
            else:
                games, block = [], first
                while block != -1:
                    block_slots = self._slots(block)[32:]
                    games.extend(block_slots[2:2 + block_slots[1]])
                    block = block_slots[0]
                form = "blocks"
            out.append(GameList(role, count, games, form))
        return out
