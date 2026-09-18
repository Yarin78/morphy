# ChessBase "2" database format

Working notes on the file format, and the source of truth for the Python code in
`morphy/`. If the code and this file disagree, fix one of them.

## Conventions

- Offsets are hexadecimal (`0x18`) and relative to the start of the structure
  being described, unless stated otherwise.
- `int` = 4 bytes, `short` = 2 bytes, `long` = 8 bytes, all signed.
- Byte order is **little-endian everywhere, except the `.2lid` file header**,
  which is big-endian.
- A `string` is an `int` byte length followed by that many UTF-8 bytes, with no
  terminator. The length counts bytes, not characters (`Mårdell` is 8).
- The older format (v1), implemented in Java in `morphy-cbh` (`se.yarin.morphy`),
  is only a guide to what fields mean and how values are encoded. Offsets, sizes
  and byte order differ in this format (e.g. ids are 24-bit big-endian in v1's
  `.cbh`), so anything borrowed from v1 needs to be checked against the samples.
- Fields marked `?` have unknown meaning; the value given is what the sample
  files contain. **(observed)** marks facts that come from inspecting the sample
  files rather than from the earlier notes, so they rest on very few samples.

## Files

A database is a set of files with the same base name, e.g. `reveng1.*`:

| Extension | Content | Header | Notes |
|-----------|---------|--------|-------|
| `.2cbh` | game headers | 192 bytes | 192-byte records, see [below](#2cbh--game-headers) |
| `.2lid` | entities (players, tournaments, ...) | variable | see [below](#2lid--entities) |
| `.2cba` | not analysed | 12 bytes | 236-byte records |
| `.2cbg` | not analysed | 12 bytes | 140-byte records |
| `.2lgd` | not analysed | 12 bytes | blocks are a multiple of 1024 bytes |
| `.2lcd` | not analysed | none | 40960 bytes in every sample, even an empty database (observed) |
| `.ini` | settings | | plain text INI |

## `.2lid` — entities

Holds every entity the game headers refer to. There are six entity types, always
in this order (the index is the type's position in the file header):

| Index | Type | Container size |
|-------|------|----------------|
| 0 | player (annotators are players too) | 1024 |
| 1 | tournament | 1120 |
| 2 | source | 220 |
| 3 | text_title | 1024 |
| 4 | team | 314 |
| 5 | game_tag | 532 |

The container sizes are read from the header, not fixed by the format; the
values above are what every sample file contains.

### File header

All integers big-endian.

| Offset | Size | Type | Description |
|--------|------|------|-------------|
| 0x00 | 4 | int | header size in bytes (0xb8 = 184) |
| 0x04 | 4 | int | number of entity types (6) |
| 0x08 + 20·i | 4 | int | container size of type `i` (bytes reserved per entity in a block) |
| 0x0c + 20·i | 8 | long | number of entities of type `i` |
| 0x14 + 20·i | 8 | long | id of the first logically deleted entity of type `i` (-1 = none), see [Deleted entities](#deleted-entities) |
| 0x80 | 56 | 7 × (int, int) | `?` — see below |

The 56 bytes at the end of the header are the pairs `(-1, 1)`, `(0, 1)`, `(1, 1)`,
`(2, 1)`, `(3, 1)`, `(4, 1)`, `(5, 1)`: a leading `(-1, 1)` and then one pair per
entity type. They are identical in every sample file, including an empty
database, so they are not counts. (observed)

### Blocks

Entities are stored in blocks that follow the header directly:

- Block `i` starts at `header size + i · block size`, where the block size is the
  sum of all the container sizes (4234 in the samples).
- Block `i` holds entity id `i` of every entity type. Ids are 0-based.
- Within a block, the types appear back to back in the order above, each taking
  its container size. In the samples, the type-specific parts start at 0
  (player), 1024 (tournament), 2144 (source), 2364 (text_title), 3388 (team) and
  3702 (game_tag).
- A type with fewer entities than there are blocks simply has unused slots in the
  later blocks (see below).
- The file ends right after the last record written, so the last block is cut
  short. E.g. with 4 players and 1 of everything else, the file is 3 full blocks
  plus the 41 bytes of player #3. (observed)

An empty database is just the header.

### Entity records

Every entity record, whatever its type, starts with:

| Offset | Size | Type | Description |
|--------|------|------|-------------|
| 0x00 | 4 | int | number of bytes that follow this field |

So a record is `4 + length` bytes, and the rest of its container is zero. A slot
with length 0 is unused: it has no entity. The field tables below list a record's
fields in order, starting with that length.

#### Player

| Size | Type | Description |
|------|------|-------------|
| 4 | int | record length (e.g. 45) |
| 4 | int | length of last name |
| n | string bytes | last name |
| 4 | int | length of first name (0 if none, e.g. for an annotator) |
| m | string bytes | first name |
| 4 | int | `d1` `?` (0) |
| 4 | int | `d2` `?` (0) |
| 4 | int | `d3` `?` (-1) |
| 4 | int | `d4` `?` (8) |
| 4 | int | `d5` `?` (-1) |
| 4 | int | `d6` `?` (-1) |

The record ends after `d6`; a player record with any other length is malformed.
The unknown ints are called `d1`–`d6` in the code.

Example, player "Jimmy Mårdell" (49 bytes, at file offset 0xb8):

```
2d 00 00 00                            record length 45
08 00 00 00  4d c3 a5 72 64 65 6c 6c   last name "Mårdell"
05 00 00 00  4a 69 6d 6d 79            first name "Jimmy"
00 00 00 00  00 00 00 00  ff ff ff ff  08 00 00 00  ff ff ff ff  ff ff ff ff
```

An annotator named "jimmy" is the same record with last name "jimmy" and an empty
first name (length 0), 41 bytes in total. (observed)

#### Tournament

| Size | Type | Description |
|------|------|-------------|
| 4 | int | record length (e.g. 120) |
| 4 | int | `?` (0) |
| 4 | int | length of title |
| n | string bytes | title |
| … | | `?` — zero, except that a byte with value `07` was seen 34, 45 and 56 bytes after the end of the title (observed) |

Example, tournament "Test" (124 bytes, at file offset 0x4b8):

```
78 00 00 00  00 00 00 00  04 00 00 00  54 65 73 74  00 00 00 00 ...
```

#### Source (observed)

| Size | Type | Description |
|------|------|-------------|
| 4 | int | record length |
| 4 | int | length of title (0 if none) |
| n | string bytes | title |
| 4 | int | `?` (0) |
| 4 | int | `?` (1037616) |
| 4 | int | `?` (1037616) |
| 4 | int | `?` (0) |

1037616 is also the `GameDate` and `RecentDate` in `reveng1.ini`, so it is
probably a date in the same encoding as the played date in `.2cbh`.

#### Team (observed)

| Size | Type | Description |
|------|------|-------------|
| 4 | int | record length |
| 4 | int | length of title |
| n | string bytes | title |
| 5 | | `?` (zero) |

#### Game tag (observed)

| Size | Type | Description |
|------|------|-------------|
| 4 | int | record length |
| 4 | int | `?` (1 with a title, 0 without) |
| 4 | int | `?` (42) |
| 4 | int | length of title |
| n | string bytes | title |

A game tag without a title is just the length (4) and one `int` of 0.

#### Text title

No entities of this type exist in the samples, so nothing is known.

### Deleted entities

A deleted entity is not removed: it keeps its slot and its id, and it is still
included in the entity count in the header (`2tour` has 6 players, one of which
is deleted).

Deleted entities of a type form a singly linked list. The header holds the id of
the first one (-1 if there are none), and each deleted record holds the id of the
next one, with -1 ending the list. This means that whether an entity is deleted
cannot be decided from a reference to it (e.g. a player id in a game header); the
list has to be followed.

Layout of a deleted record, seen for a player (observed):

| Size | Type | Description |
|------|------|-------------|
| 4 | int | record length (16) |
| 8 | | `?` (`22 33 44 55 66 77 88 99`, the same in every deleted record seen) |
| 8 | long | id of the next deleted entity of the same type (-1 = end of list) |

Newly deleted entities are put first in the list, so the list is ordered from the
most recently deleted entity to the oldest. Example, with two deleted players
(observed, in `reveng1.2lid` after #2 had been deleted and then #1):

```
header, player entry:  first deleted id = 1
player #1:  10 00 00 00  22 33 44 55 66 77 88 99  02 00 00 00 00 00 00 00   next = 2
player #2:  10 00 00 00  22 33 44 55 66 77 88 99  ff ff ff ff ff ff ff ff   next = -1, end
```

Not known: the layout for the other entity types, what the `22 33 …` bytes mean,
and whether a record can be recognised as deleted without following the list.

## `.2cbh` — game headers

A 192-byte header (not decoded) followed by one 192-byte record per game. Game
ids are 1-based: game 1 is the first record after the header. The number of games
is `(file size − 192) / 192`.

Known fields of a game record:

| Offset | Size | Type | Description |
|--------|------|------|-------------|
| 0x00 | | | highest bit first byte marks deletion |
| 0x18 | 8 | long | white player id |
| 0x20 | 8 | long | black player id |
| 0x28 | 8 | long | tournament id |
| 0x30 | 8 | long | annotator id (a player) |
| 0x38 | 8 | long | source id |
| 0x40 | 8 | long | white team id (-1 = none) |
| 0x48 | 8 | long | black team id |
| 0x50 | 8 | long | game tag id |
| 0x58 | 1 | byte | result (`GameResult` enum) |
| 0x59 | 1 | byte | NAG (if the result is "line", this is the `LineEvaluation`) |
| 0x5a | 2 | short | round |
| 0x5c | 2 | short | subround |
| 0x5e | 2 | short | board |
| 0x60 | 2 | short | white elo |
| 0x70 | 2 | short | black elo |
| 0x8a | 2 | short | number of full moves in the game |
| 0xa0 | 8 | long | presumably a timestamp of when the game was saved; encoding unknown, see [Timestamp](#timestamp) |
| 0xb8 | 4 | int | version number (increases by 1 on every save) |
| 0xbc | 4 | int | encoded played date, see [Dates](#dates) |

The entity ids are 0-based ids into the `.2lid` file, in the block/slot sense
above. Verified with `inspect games` against the sample databases.

The samples have 2200 at 0x60 and 2100 at 0x70, so those are the white and black
elo. The result is 3 in all samples (the v1 value for an unfinished game, "line"),
and the NAG is only non-zero (15) in a game where the result is 3 as well.

### Result, NAG and round

The result byte (0x58) uses the v1 `GameResult` values: 0 = 0-1, 1 = draw,
2 = 1-0, 3 = not finished ("line"), 4 = white wins on forfeit, 5 = draw on
forfeit, 6 = black wins on forfeit, 7 = both lost. Only 3 occurs in the samples.
When the result is 3, the NAG byte (0x59) is the line evaluation, using the
standard NAG numbers, e.g. 15 for a slight advantage for black (`=/+`). That is
how `inspect` shows the result: for a line, the evaluation is shown instead, as
in the Java command line tool.

The round (0x5a), subround (0x5c) and board (0x5e) are shown together as
`round.subround.board`, leaving out trailing parts that are 0.

### Dates

A date is an int where bits 0-4 are the day, bits 5-8 the month and bits 9-20 the
year; higher bits are ignored, and a part that is 0 is unknown. This is the v1
encoding, and it fits the samples: the played dates 1037616 and 940544 are
2026-09-16 (the date the database was created) and 1837 with month and day
unknown. The same value, 1037616, is stored in source records and as `GameDate`
in the `.ini` file, so they use this encoding too.

### Timestamp

The long at 0xa0 changes every time the game is saved, and the samples show it
increasing for successive saves of a game. Values are around 1.4·10¹⁷ and are
always multiples of 10. Interpreting it as a count of seconds, milliseconds,
microseconds, 10 ns, 100 ns or nanoseconds since 0001, 1601, 1970, 1899-12-30,
1904 or 2000 gives no date near 2026, when the samples were made, so the unit and
epoch are not known. The code keeps the value as is.

## Open questions

- Deleted entities: the record layout for the other entity types, what the
  `22 33 44 55 66 77 88 99` bytes are, and whether a deleted slot is reused when a
  new entity is added (and if so, which one, and how the list changes).
- In `1tour/reveng1.2lid`, player #2 is a valid (not deleted) record with both
  names empty (36 bytes).
- Which byte(s) of a game record hold the deletion bit (the code tests bit 7 of
  the first byte, as v1 does, but no sample has a deleted game).
- The encoding of the timestamp at 0xa0 (see above).
- Other non-zero bytes in the samples' game records: 0x08 and 0x10 hold 0x0c in
  game 1 and 0x98 and 0xf8 in game 2, which look like offsets into `.2cbg`
  (12 + 140·n) and `.2cba` (12 + 236·n); 0x62 and 0x64 hold 1 and 0x68 holds
  `FIDE` (the same at 0x72, 0x74 and 0x78, so probably rating type information
  for white and black); and there is more from 0x80 to 0x9e. Byte 0 is 0x01, as in
  the v1 type byte where bit 0 is always set.
- The unknown fields in every entity record, and the layout of the text title.
