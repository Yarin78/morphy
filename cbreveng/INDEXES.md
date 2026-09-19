# ChessBase "2" format: the entity indexes

Working notes on the `.2lcd` and `.2lgd` files of the format described in
[FORMAT.md](FORMAT.md), with the same conventions: offsets are hexadecimal and
relative to the start of the structure, `int`, `short` and `long` are 4, 2 and 8
bytes, and **(wch2)**, **(probe)**, **(mega)** and **(observed)** say what a fact
rests on. This file is the source of truth for `morphy/indexes.py`.

## Summary

Both files index the entities of the `.2lid` file:

- **`.2lcd` holds the sort orders.** Each kind of entity has a named index, an
  AVL tree whose nodes are the entities in sort order: players by name,
  tournaments by year and title, and so on. This is what v1 keeps inside each
  entity file, as an AVL tree threaded through the entity records; here it has a
  file of its own. Each node carries an 8-byte key, the start of what is
  compared, so most comparisons take a single integer compare.
- **`.2lgd` holds the games of each entity.** For every entity id, and for each
  way a record can refer to an entity (as a player, a tournament, a source, ...),
  the list of records that do. It's what lets ChessBase list all the games of a
  player without reading every game header. v1 keeps only a count and the first
  game in each entity record.

Everything below was checked against every node and list in `wch2`, `probe` and
`reveng1`, and every node of the eight indexes of `mega` (668,610 nodes), with
the exceptions noted. There are only ever these eight indexes: `mega` has no
other sort orders.

## `.2lcd` — sort orders

Unlike the other files, the header and the catalog are **big-endian**. The tree
nodes and directory pages are little-endian.

The file is a sequence of 4096-byte pages: 10 pages (40,960 bytes) in every
sample made in ChessBase, 14 in `wch2` and 7,578 in `mega`.

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
| 0x00 | 2 | short | depth: the number of levels of directory pages above the node pages, see [Nodes](#nodes) |
| 0x02 | 4 | int | the node page if the depth is 0, otherwise the top directory page |
| 0x06 | 8 | long | root node, -1 if the index is empty |
| 0x0e | 8 | long | number of nodes |
| 0x16 | 1 | byte | `.2lid` entity type + 1 (players 1, tournaments 2, sources 3, teams 5, game tags 6) |
| 0x17 | 1 | byte | `?` always 3 |
| 0x18 | 1 | byte | `?` the index's number: 1-5 as listed below, 6 for the last three |
| 0x19 | 1 | byte | `?` always 1 |
| 0x1a | 2 | short | length of the name |
| 0x1c | n | | the name, in Latin-1 |

| Index | Entities | Sorted by |
|-------|----------|-----------|
| Default Spieler | players who have played a game | last name, first name |
| Default Turnier | tournaments | year (oldest first), title, place, month, day |
| Default Quelle | sources | title |
| Default Kommentator | players who have annotated a game, or written a text or an analysis | last name, first name |
| Default Mannschaften | teams | title, team number, nation, year, season |
| Default Partietitle | game tags used by games | language, title |
| Texttitel | game tags used as the title of a guiding text | language, title |
| Analysen | game tags used as the title of an analysis | language, title |

The names are not fixed strings: `mega` has `Default partietitle`, with a
lowercase `p`.

Players and annotators are the same kind of entity (FORMAT.md), but they are
sorted separately, each index holding only the players used in that role. The
game tag entity type likewise has three indexes, for the three things a game
tag can be the title of. An **analysis** is a third kind of record in the
`.2cbh` file, next to games and guiding texts; `mega` has 406 of them (see
[Analyses](#analyses)).

How text is compared is described [below](#how-text-is-compared). Tournaments
sort **oldest first**, unlike v1, which sorts them by year descending.

### Nodes

A node's number is **the id of its entity**, so node 5 of the player index is
player 5. A node page holds 102 nodes of 40 bytes, node `n` at `40 · (n mod 102)`
(the last 16 bytes of the page are unused), and the slots of entities that are
not in the index are zero or hold stale data.

Node pages are numbered from 0 (nodes 0-101), 1 (nodes 102-203), and so on.
When there is more than one, the catalog points to a **directory page**: a list
of up to 1024 page numbers (ints), the node pages in order, ending at a 0. When
there are more than 1024 node pages, the directory pages have a directory page
of their own, and so on; the depth in the catalog is the number of these
levels. It depends only on the highest entity id, not on how many entities
the index holds:

| Ids | Depth | Example |
|-----|-------|---------|
| up to 102 | 0 | every index of `probe`; the tournaments of `wch2` |
| up to 104,448 (102 · 1024) | 1 | the players of `wch2` (120 ids, on pages 2 and 13, listed on page 12); the 71,455 teams of `mega` |
| up to 106,954,752 | 2 | the players (482,530 ids) and tournaments (110,137) of `mega`, and its annotators, which are players |

So the annotator index of `mega` has two levels for only 2,574 nodes, because
its node ids are player ids. (wch2, mega)

| Offset | Size | Type | Description |
|--------|------|------|-------------|
| 0x00 | 8 | long | left child, -1 if none |
| 0x08 | 8 | long | right child |
| 0x10 | 8 | long | parent, -1 for the root |
| 0x18 | 8 | long | key, see below |
| 0x20 | 2 | short | balance: height of the right subtree minus that of the left, -1 to 1 |
| 0x22 | 2 | short | `?` always 0 |
| 0x24 | 4 | int | the node's slot in its page (`id mod 102`), or -1 |

An in-order walk of the tree, from the root in the catalog, gives the entities
in sort order.

The int at 0x24 is the slot in 636,263 of the 668,610 nodes of `mega`, and -1 in
the others, which are **all leaves**. In the small databases the slot is the
same number as the id, which made it look like a copy of the id. Which leaves
have -1 isn't clear. The newest node of a small tree often has it (in `2tour`
the tournament `Rockaden Open`, a leaf, has -1, while `Test`, which gained it as
a child, has its slot), so it may mean the node has never had a child, but that
is a guess. (mega)

### Keys

The key is 8 bytes of the entity's sort fields, compared as an unsigned
big-endian number; the node stores it as a little-endian long, so its bytes
appear in the file back to front (`nosskire` is "eriksson"). Nodes with the same
key are ordered by comparing the entities themselves, and so are nodes without
one.

| Entities | Key | Characters allowed |
|----------|-----|--------------------|
| players, annotators | the first 8 characters of the last name | letters `a-z`, digits, space, `-` |
| tournaments | the year (2 bytes), then the first 6 characters of the title | letters `a-z` and digits only |
| sources, teams | the first 8 characters of the title | any ASCII |
| game tags (all three indexes) | the language of the first title (1 byte, a nation code, e.g. 42 ENG), then its first 7 characters | any ASCII, checked over the first 8 characters |

The text is lowercased and padded with zero bytes. **The key is 0 when the
characters it is made from include one that is not allowed**; the node is still
in its right place in the tree, so a key of 0 just means the entity has to be
compared in full. The allowed characters are exactly those whose plain ASCII
order agrees with [how the text is compared](#how-text-is-compared), which is
presumably why they differ: the comparison ignores apostrophes for players and
also hyphens for tournaments, and it puts symbols in a different order from
ASCII.

Examples: `L'Ami`, `Euwe/Schluricke` and `Mårdell` have no key, while
`Benjamin/King`, whose `/` is the 9th character, has `benjamin`. Every `wch2`
tournament starts with `World-` and so has no key, and neither does the
`probe` tournament `some tournament` (a space in the first 6 characters); `mega`
has 49,856 such tournaments. `aarhus/s` and `100% lic` are team keys. The game
tag `Kecskemét 1927` has no key although its key would only use `kecskem`,
because the `é` is the 8th character.

An entity whose sort fields are all empty (the placeholder the games refer to
when a field is blank, see FORMAT.md) gets the key 1, so it comes first. A
tournament with only a year gets the year and six zero bytes.

These rules give the stored key of every node in `mega` (668,610), `wch2`,
`probe` and `reveng1`. (mega, wch2, probe)

### How text is compared

The order of the indexes fits a comparison much like the one Windows uses for
text (`CompareString`), rather than v1's comparison of raw bytes:

- Case is ignored, and so are accents at first: `Cífer` sorts as `cifer` and
  `Bełchatow` as `belchatow`. When two titles are otherwise equal, the one
  without accents comes first (`Bialoleka Sparta` before `Białołeka Sparta`).
- Symbols come before digits, and digits before letters. The symbols are in
  Windows' order, which is ASCII's except that `+ < = >` come after all the
  other punctuation (`BYE1/2` before `BYE1+`). Chess figurine characters
  (U+E024-E029, used in some titles) come after the letters.
- Apostrophes are ignored for players and annotators (`D'Abraccio` sorts as
  `dabraccio`, right after `Dabraccio`), and apostrophes and hyphens for
  tournaments (`BEL-NED m` sorts as `belnedm`). For other entities they count as
  symbols (`C'CHARTRES` before `Caballos`). Spaces always count.
- Fields are compared one after the other, in the order listed in the
  [catalog](#catalog). Two entities that are equal in all of them are in id
  order.

This fits the order of every index of `mega` apart from 7 pairs of neighbours
out of 668,610 entries, all of them about apostrophes or hyphens (e.g.
`Dhaka Knights` before `Dhaka Knight's CC`, and `Leningrad1 tt` before
`Leningrad-ch 1979`), which may be entities renamed after they were put in the
tree. (mega)

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
| 0x00 | 4 | int | `?` always 256 |
| 0x04 | 4 | int | number of list blocks in use, see below |
| 0x08 | 4 | int | `?` always 0 |

An empty database is just the header.

### Records

Record `i` has two independent halves:

- the **heads** (0x000-0x0ff): the game lists of every entity with id `i`,
  whatever its type, as with the blocks of the `.2lid` file;
- **list block** `i` (0x100-0x1ff), part of a shared pool of blocks that hold
  the longer lists.

The file has as many records as whichever needs more: `probe` has 26 records
for its 26 players, while `wch2` has 165 for 120 players and `mega` 4,355,078
for 482,530 players, because that is how many list blocks they use. (wch2, probe,
mega)

The heads are 32 longs, slot `k` at `8 · k`. The lists are numbered by role:

| Role | Records that refer to the entity as |
|------|-------------|
| 1 | white or black player |
| 2 | tournament (guiding texts included) |
| 3 | source (texts and analyses included) |
| 4 | annotator (authors of texts and analyses included) |
| 5 | white or black team |
| 6 | game tag |
| 7 | title of a guiding text |
| 8 | title of an analysis |

and list `k` of the record is in three slots:

| Slot | Description |
|------|-------------|
| `k` | first, see below; -1 if the list is empty |
| `10 + k` | last |
| `20 + k` | number of records |

Slots 0 and 9 and their partners are always empty, slot 30 is always 12 and slot
31 always 0. Role 8 is used 402 times in `mega`, once for each entry of its
Analysen index. (mega)

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
of every entity in `wch2` (1,155 lists), `probe` (182), and `reveng1` and its
earlier states. (wch2, probe)

## Analyses

`mega` has a third kind of record in the `.2cbh` file besides games and guiding
texts. It is told apart by **the byte at 0x02 of the record, which is 2**, where
it is 1 for games and texts (FORMAT.md has it as "always 1"). Of the 11,990,472
records of `mega`, 406 are analyses. They have moves and annotations like a
game, but a header of their own, and these fields are confirmed by the game
lists they appear in: (mega)

| Offset | Size | Type | Description |
|--------|------|------|-------------|
| 0x08 | 8 | long | offset of the moves in the `.2cbg` file |
| 0x10 | 8 | long | offset of the annotations in the `.2cba` file |
| 0x18 | 8 | long | title: a game tag, in the Analysen index and listed under role 8 |
| 0x20 | 8 | long | source, listed under role 3 |
| 0x28 | 8 | long | annotator (the author), listed under role 4 |
| 0x30 | 8 | | `?` large values, perhaps a timestamp |
| 0x38 | 8 | | `?` 776 in the analyses looked at |
| 0x40 | 8 | | `?` small numbers |
| 0x48 | 8 | | `?` large values, perhaps a timestamp |
| 0x50 | 8 | | `?` small numbers |

The rest of the record is zero. Their titles are opening lines such as
`1.d4 d5 2.c4 c6 3.♘c3`, written with the figurine characters.

## Open questions

`.2lcd`:

- Which leaves have -1 at 0x24 rather than their slot.
- What the bytes at 0x17 (3) and 0x19 (1) of a catalog entry mean.
- The exact comparison for apostrophes and hyphens: 7 pairs in `mega` don't
  fit the rules above.

`.2lgd`:

- What slot 30 (always 12) and the first int of the header (always 256) are.
- Whether role 9 is ever used.
- What happens to the lists when a game is deleted for good (compacting the
  database), or when an entity is deleted.

Analyses: the fields at 0x30-0x50, and whether their moves and annotations are
stored like a game's.
