# ChessBase "2" format: the entity indexes

Working notes on the `.2lcd` and `.2lgd` files of the format described in
[FORMAT.md](FORMAT.md), with the same conventions: offsets are hexadecimal and
relative to the start of the structure, `int`, `short` and `long` are 4, 2 and 8
bytes, and **(wch2)**, **(probe)** and **(observed)** say what a fact rests on.
This file is the source of truth for `morphy/indexes.py`.

## Summary

Both files index the entities of the `.2lid` file:

- **`.2lcd` holds the sort orders.** Each kind of entity has a named index, an
  AVL tree whose nodes are the entities in sort order: players by name,
  tournaments by year and title, and so on. This is what v1 keeps inside each
  entity file, as an AVL tree threaded through the entity records; here it has a
  file of its own. Each node carries an 8-byte key, the start of what is
  compared, so most comparisons take a single integer compare.
- **`.2lgd` holds the games of each entity.** For every entity id, and for each
  way a game can refer to an entity (as a player, a tournament, a source, ...),
  the list of games that do. It's what lets ChessBase list all the games of a
  player without reading every game header. v1 keeps only a count and the first
  game in each entity record.

Everything below was checked against every node and list in `wch2`, `probe` and
`reveng1`, with the exceptions noted.

## `.2lcd` — sort orders

Unlike the other files, the header and the catalog are **big-endian**. The tree
nodes are little-endian.

The file is a sequence of 4096-byte pages: 10 pages (40,960 bytes) in every
sample made in ChessBase, and 14 in `wch2`.

| Page | Content |
|------|---------|
| 0 | the file header |
| 1 | the catalog of indexes |
| 2 and up | node pages and directory pages |

### File header

| Offset | Size | Type | Description |
|--------|------|------|-------------|
| 0x00 | 4 | int | `?` 1 |
| 0x04 | 4 | int | number of pages in the file |
| 0x08 | 4 | int | page size (4096) |

The rest of the page is zero.

### Catalog

Page 1 has room for 32 entries of 128 bytes. Every sample has the same eight
indexes in the first eight, with German names, and the rest have an empty name:

| Offset | Size | Type | Description |
|--------|------|------|-------------|
| 0x00 | 2 | short | 1 if the page at 0x02 is a directory page, 0 if it is the only node page |
| 0x02 | 4 | int | page |
| 0x06 | 8 | long | root node, -1 if the index is empty |
| 0x0e | 8 | long | number of nodes |
| 0x16 | 1 | byte | `.2lid` entity type + 1 (players 1, tournaments 2, sources 3, teams 5, game tags 6) |
| 0x17 | 1 | byte | `?` 3 |
| 0x18 | 1 | byte | `?` the index's number: 1-5 as listed below, 6 for the last three |
| 0x19 | 1 | byte | `?` 1 |
| 0x1a | 2 | short | length of the name |
| 0x1c | n | | the name, in Latin-1 |

| Index | Entities | Sorted by |
|-------|----------|-----------|
| Default Spieler | players who have played a game | last name, first name |
| Default Turnier | tournaments | year (ascending), title, place |
| Default Quelle | sources | title |
| Default Kommentator | players who have annotated a game or written a text | last name, first name |
| Default Mannschaften | teams | title |
| Default Partietitle | game tags used by games | language, title |
| Texttitel | game tags used as the title of a guiding text | language, title |
| Analysen | `?` game tags; empty in every sample | |

Players and annotators are the same kind of entity (FORMAT.md), but they are
sorted separately, each index holding only the players used in that role.
Game tags and text titles likewise share an entity type but not an index.

The "sorted by" column is what the order of every index in the samples fits,
comparing text without regard to case. Tournaments sort **oldest first**,
unlike v1, which sorts them by year descending. (wch2, probe)

### Nodes

A node's number is **the id of its entity**, so node 5 of the player index is
player 5. A node page holds 102 nodes of 40 bytes, node `n` at `40 · n` (the
last 16 bytes of the page are unused), and the slots of entities that are not in
the index are zero or hold stale data.

When an index needs more than one page, as the player and annotator indexes of
`wch2` with its 120 players do, the catalog points to a **directory page**
instead: a list of ints, the node pages in order, ending at a 0. Nodes 0-101 are
on the first page listed, 102-203 on the second, and so on: in `wch2` the
players are on pages 2 and 13 (listed on page 12), and the annotators on pages 5
and 11 (listed on page 10). (wch2)

| Offset | Size | Type | Description |
|--------|------|------|-------------|
| 0x00 | 8 | long | left child, -1 if none |
| 0x08 | 8 | long | right child |
| 0x10 | 8 | long | parent, -1 for the root |
| 0x18 | 8 | long | key, see below |
| 0x20 | 2 | short | balance: height of the right subtree minus that of the left, -1 to 1 |
| 0x22 | 2 | short | `?` always 0 |
| 0x24 | 4 | int | `?` usually the node's own id, sometimes -1, rarely another id |

An in-order walk of the tree, from the root in the catalog, gives the entities
in sort order.

The int at 0x24 has no connection to the `.2lgd` lists. In `wch2` a few
annotators hold another id there (Adams, annotator 116, holds 14), which may
be an id from before the conversion renumbered them, but that is a guess.

### Keys

The key is 8 bytes of the entity's sort fields, compared as an unsigned
big-endian number; the node stores it as a little-endian long, so its bytes
appear in the file back to front (`nosskire` is "eriksson"). Nodes with the same
key are ordered by comparing the entities themselves, and so are nodes without
one.

| Entities | Key |
|----------|-----|
| players, annotators | the first 8 characters of the last name |
| tournaments | the year (2 bytes), then the first 6 characters of the title |
| sources, teams | the first 8 characters of the title |
| game tags, text titles | the language of the first title (1 byte, a nation code, e.g. 42 ENG), then its first 7 characters |

The text is lowercased and padded with zero bytes. An **entity with an empty
name** (the placeholder the games refer to when a field is blank, see
FORMAT.md) gets the key 1, so it comes first.

The key is **0 when the characters that go into it include anything but the
letters `a-z`, digits, space and `-`**: `L'Ami`, `Euwe/Schluricke`,
`Karpov,An/Mazukewitsch` and `Mårdell` all have a key of 0, while
`Benjamin/King`, whose `/` is the 9th character, has the key `benjamin`. The node
is still in the right place in the tree, so a key of 0 just means the entity has
to be compared in full. This rule gives the stored key of every player,
annotator, source, team, game tag and text title in `wch2`, `probe` and
`reveng1`. (wch2, probe)

The one exception is tournaments. All 52 in `wch2` have a key of 0 although
their titles (`World-ch01 ...`) would give one, and so does the tournament of
the `probe` guiding text, `some tournament`, whose record is identical to that
of the tournament `Test` in `reveng1`, which has its key. The other 10
tournaments in the samples have the key the rule gives. Whether a tournament
gets a key seems to depend on how it was created (by converting v1, or from the
text editor) rather than on the tournament.

### Example

The tournament index of `probe`, in order, as `inspect sorted probe 2` shows it:

```
     0  (empty entity)
     3  ???? bar            bar
     2  ???? foo            foo
     1  (no key)            some tournament
     4  2026 baz            baz
     5  2026 qux            qux
     ...
```

The columns are the id, the key and the title. `some tournament` has no key, but
is still where its title puts it among the tournaments without a year.

## `.2lgd` — the games of each entity

All little-endian: a 12-byte header, then 512-byte records.

| Offset | Size | Type | Description |
|--------|------|------|-------------|
| 0x00 | 4 | int | `?` 256 |
| 0x04 | 4 | int | number of list blocks in use, see below |
| 0x08 | 4 | int | `?` 0 |

An empty database is just the header.

### Records

Record `i` has two independent halves:

- the **heads** (0x000-0x0ff): the game lists of every entity with id `i`,
  whatever its type, as with the blocks of the `.2lid` file;
- **list block** `i` (0x100-0x1ff), part of a shared pool of blocks that hold
  the longer lists.

The file has as many records as whichever needs more: `probe` has 26 records
for its 26 players, and `wch2` 165 records for 120 players, because it uses 165
list blocks. (wch2, probe)

The heads are 32 longs, slot `k` at `8 · k`. The lists are numbered by role:

| Role | Games that refer to the entity as |
|------|-------------|
| 1 | white or black player |
| 2 | tournament (guiding texts included) |
| 3 | source (texts included) |
| 4 | annotator (text authors included) |
| 5 | white or black team |
| 6 | game tag |
| 7 | title of a guiding text |

and list `k` of the record is in three slots:

| Slot | Description |
|------|-------------|
| `k` | first, see below; -1 if the list is empty |
| `10 + k` | last |
| `20 + k` | number of games |

Slots 0, 8, 9 and their partners are always empty, slot 30 is always 12 and slot
31 always 0.

A list is written in one of three ways, depending on the flags in the top bits
of *first*:

| First | Form | The list |
|-------|------|----------|
| `0x4000000000000000` + game id | single | that game, and if *last* is not -1, also the game *last* |
| `0x2000000000000000` + game id | range | every game from *first* to *last* (which also has the flag) |
| a block number | blocks | the games in the chain of list blocks from *first*; *last* is the final block of the chain |

Game ids are the 1-based ids of the `.2cbh` file. A list is in ascending order,
and **a game that refers to the entity twice is in it twice**, e.g. a game where
the same player has both colors (`probe` games 5-11). Deleted games are still
listed. For example, player `g41` of `probe` has the list
`0x2000000000000022`-`0x2000000000000024`: the games 34, 35 and 36. (probe)

A list block is:

| Slot | Size | Description |
|------|------|-------------|
| 32 | long | next block of the list, -1 for the last |
| 33 | long | number of games in this block, at most 30 |
| 34-63 | 30 longs | the game ids |

Every block of a chain but the last is full. The blocks in use are 0 to the
count in the file header minus 1.

This decodes to exactly the right games, in the right numbers, for every list
of every entity in all the samples: 1,155 lists in `wch2`, 182 in `probe`, and
all of those in `reveng1` and its earlier states. (wch2, probe)

## Open questions

`.2lcd`:

- What the int at 0x24 of a node is.
- Why some tournaments have no key (see [Keys](#keys)).
- What the bytes at 0x17-0x19 of a catalog entry mean, and what "Analysen" is
  for. It indexes game tags and is empty in every sample.
- When ChessBase moves an index to a directory page. In `wch2` the player and
  annotator indexes were rebuilt that way (their catalog entries have 1 at
  0x00), and the other indexes still have their original pages.
- How it breaks ties beyond the fields listed, and how it compares text with
  accents: the samples fit a plain comparison without regard to case, but
  have few names that would tell the difference.

`.2lgd`:

- What slot 30 (always 12) and the first int of the header (always 256) are.
- Whether roles 8 and 9 are ever used.
- What happens to the lists when a game is deleted for good (compacting the
  database), or when an entity is deleted.
