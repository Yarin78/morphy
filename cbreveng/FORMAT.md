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
- Most of what follows was checked against `wch2`, a 1038-game database that
  ChessBase converted from the v1 database in `wch1`. Because the game ids match
  one to one, a field is confirmed by decoding the v1 file and requiring the
  values to agree for every game. Facts established that way are marked
  **(wch2)**; they rest on a real database but only one.
- Fields marked `?` have unknown meaning; the value given is what the sample
  files contain. **(observed)** marks facts that come from inspecting the sample
  files rather than from the earlier notes, so they rest on very few samples.

## Sample databases

- `reveng1` — a handful of games made by hand in ChessBase, with `empty`, `1tour`
  and `2tour` as earlier states of it. Small enough to read byte by byte, and the
  only place some fields are set at all.
- `wch2` — 1038 world championship games, converted by ChessBase from the v1
  database in `wch1`. The game ids match one to one between the two, so decoding
  `wch1` with the Java code gives an expected value for every game.

## Files

A database is a set of files with the same base name, e.g. `reveng1.*`:

| Extension | Content | Header | Notes |
|-----------|---------|--------|-------|
| `.2cbh` | game headers | 192 bytes | 192-byte records, see [below](#2cbh--game-headers) |
| `.2lid` | entities (players, tournaments, ...) | variable | see [below](#2lid--entities) |
| `.2cba` | annotations | 12 bytes | records start with `88 77 66 55 44 33 22 11` (wch2) |
| `.2cbg` | moves | 12 bytes | records start with `88 77 66 55 44 33 22 11` (wch2) |
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
| 4 | int | length of place |
| n | string bytes | place |
| 4 | int | length of title |
| m | string bytes | title |
| … | | `?` — zero, except that a byte with value `07` was seen 34, 45 and 56 bytes after the end of the title (observed) |

The **place comes before the title**, which only shows up in a database that has
one. In `reveng1` the place is empty, so the record looks like it starts with a
`?` int of 0. Tournament #0 of `wch2` (wch2):

```
9c 00 00 00                              record length 156
03 00 00 00  55 53 41                    place "USA"
25 00 00 00  57 6f 72 6c 64 2d 63 68 ... title "World-ch01 Steinitz-Zukertort +10-5=5+"
```

Example, tournament "Test" with no place (124 bytes, at file offset 0x4b8):

```
78 00 00 00  00 00 00 00  04 00 00 00  54 65 73 74  00 00 00 00 ...
```

`morphy/entitydb.py` does not know this yet: it scans for the first string in the
record, so it reports the place as the title whenever there is one.

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
| 0x00 | 1 | byte | type: bit 0 always set, bit 1 = guiding text, bit 7 = deleted |
| 0x02 | 1 | byte | `?` always 1 (wch2) |
| 0x03 | 1 | byte | `?` always the same as the type byte at 0x00 (wch2) |
| 0x08 | 8 | long | offset of the moves in the `.2cbg` file (wch2) |
| 0x10 | 8 | long | offset of the annotations in the `.2cba` file (wch2) |
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
| 0x60 | 2 | short | white elo, followed by the white rating type, see [Rating type](#rating-type) |
| 0x70 | 2 | short | black elo, followed by the black rating type |
| 0x80 | 2 | ushort | ECO opening code (wch2), see [ECO](#eco) |
| 0x82 | 2 | ushort | medals, a bitmask (wch2), see [Flags and medals](#flags-and-medals) |
| 0x84 | 4 | uint | flags, a bitmask (wch2), see [Flags and medals](#flags-and-medals) |
| 0x88 | 2 | ushort | annotation magnitude flags (wch2) |
| 0x8a | 2 | short | number of full moves in the game |
| 0x8c | 4 | uint | final material of one player (wch2), see [Final material](#final-material) |
| 0x90 | 4 | uint | final material of the other player (wch2) |
| 0x98 | 8 | long | creation timestamp (wch2), see [Timestamps](#timestamps) |
| 0xa0 | 8 | long | last-changed timestamp (wch2), see [Timestamps](#timestamps) |
| 0xa8 | 6 | bitmask | endgame types the game passed through (wch2), see [Endgame types](#endgame-types) |
| 0xb8 | 4 | int | game version, increases by 1 on every save |
| 0xbc | 4 | int | encoded played date, see [Dates](#dates) |

Everything not in the table is zero in every game of `wch2`: 0x01, 0x04-0x07,
0x62-0x6f, 0x72-0x7f, 0x8e-0x8f, 0x92-0x97, 0xae-0xb7 and 0xba-0xbb. The endgame
bitmask at 0xa8 is therefore at least 6 bytes and at most 16.

The entity ids are 0-based ids into the `.2lid` file, in the block/slot sense
above. Verified with `inspect games` against the sample databases.

Converting a v1 database renumbers some entities but not others: in `wch2` the
tournament and source ids are unchanged from v1, while players, annotators and
game tags are renumbered. A game with no game tag refers to game tag 0, which is
an entity with an empty title, rather than to -1 as in v1.

### Guiding texts

A record whose type byte has bit 1 set is a guiding text, not a game, and then
the whole record has a different layout that is not decoded. The little that is
known (wch2): 0x08 is still the offset into `.2cbg`, 0x30 holds the creation
timestamp that a game keeps at 0x98, and everything from 0x50 on is zero. In
`wch2`, 13 of the 1038 games are guiding texts.

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

### ECO

The ECO code is stored the same way as in v1: `value / 128 - 1` is the code,
numbered from 0, so 0-99 is A00-A99, 100-199 is B00-B99 and so on up to E99, and
`value % 128` is the sub-ECO. A value of 0 means no ECO. A value of at least
65536 - 960 is a Chess960 start position instead, `value - (65536 - 960)`. (wch2)

### Flags and medals

Both are bitmasks and both use the v1 values. The flags (0x84) mostly say which
kinds of annotation the game has: 0x01 setup position, 0x02 variations,
0x04 commentary, 0x08 symbols, 0x10 graphical squares, 0x20 graphical arrows,
0x80 time spent, 0x100 anno type 8, 0x200 training, 0x10000 embedded audio,
0x20000 embedded picture, 0x40000 embedded video, 0x80000 game quotation,
0x100000 pawn structure, 0x200000 piece path, 0x400000 white clock,
0x800000 black clock, 0x1000000 critical position, 0x2000000 correspondence
header, 0x4000000 anno type 1a, 0x8000000 unorthodox, 0x10000000 web link.

The annotation magnitude flags (0x88) qualify some of those, giving a rough size
for each kind of annotation; the v1 meanings are in `GameHeaderIndex`.

The medals (0x82) are one bit each, from bit 0: best game, decided tournament,
model game, novelty, pawn structure, strategy, tactics, with attack, defense,
sacrifice, material, piece play, endgame, tactical blunder, strategical blunder,
user. (wch2)

### Final material

The material a player has left at the end of the game, packed into one value:
bits 0-2 rooks, bits 3-5 bishops, bits 6-8 knights, bits 9-11 queens and bits
12-15 pawns. The same encoding as v1. Which of 0x8c and 0x90 is white is not
known; v1 calls them player 1 and player 2. (wch2)

### Endgame types

A bitmask of the endgame types the game passed through. v1 stores endgame
information differently, in 20 bytes of the `.cbj` file, and it is empty for all
of `wch1` while `wch2` has it for 206 games, so ChessBase computed it during the
conversion and there is nothing to check it against.

What the bits mean is guesswork, from the final material of the games that set
each bit. Bits that fit a clear rule in at least four games out of five:
bit 0 bishop endgame, bit 1 knight vs bishop, bit 13 minor piece endgame,
bit 14 knight endgame, bit 24 queen endgame, bit 29 bishop vs rook, and bit 35
rook endgame, which is by far the most common (76 games, all of them ending with
only rooks and pawns). The rule can't be expected to hold exactly, since the bits
record what the game passed through and the final material is only where it
ended up.

### Rating type

In `reveng1`, each elo is followed by what looks like a rating type: at 0x62 and
0x64 a short of 1, and at 0x68 the text `FIDE`. That makes a 16-byte block per
player, 0x60-0x6f for white and 0x70-0x7f for black, matching v1's 16-byte
`RatingType`. In `wch2` the whole block after the elo is zero even though v1 has
`FIDE` for 747 of those games, so the conversion dropped it and the layout is
only a guess.

### Dates

A date is an int where bits 0-4 are the day, bits 5-8 the month and bits 9-20 the
year; higher bits are ignored, and a part that is 0 is unknown. This is the v1
encoding, and it fits the samples: the played dates 1037616 and 940544 are
2026-09-16 (the date the database was created) and 1837 with month and day
unknown. The same value, 1037616, is stored in source records and as `GameDate`
in the `.ini` file, so they use this encoding too.

### Timestamps

A game header holds two timestamps, both in the v1 encodings, and in `wch2` both
are byte for byte what v1 has:

- **Creation** (0x98): 1/1024 seconds since 2008-12-01 in Europe/Berlin.
- **Last changed** (0xa0): 100 nanoseconds since 1582-10-15 UTC, the epoch
  UUID timestamps use. 0 means the game has never been changed since it was
  created.

Game 1 of `wch2` gives 2017-04-14 12:12:08 UTC and 2019-10-03 09:21:02 UTC, which
is what the Java command line tool prints for the v1 database in local time.

The creation timestamp is only understood for a converted database. In `reveng1`,
which ChessBase made itself, 0x98 holds the same value
`2b e0 0a 59 f5 5d 08 00` in every game and in all four snapshots of it, taken on
different days, and no epoch and unit turn that into a date near 2026. So either
the field means something else when ChessBase writes it fresh, and the converter
just copied v1's value in, or it is a creation timestamp in an encoding that has
not been worked out. Last changed (0xa0) has no such problem: it decodes
correctly in `reveng1` too, giving the days those games were actually saved.
## `.2cbg` and `.2cba` — moves and annotations

Not analysed beyond their framing. Both start with a 12-byte file header holding
the file size as a long and then the header size (12) as an int. A record begins
with the 8-byte marker `88 77 66 55 44 33 22 11`, then an int length, then an int
that varies by record (0x62, 0x64, 0x68 … in `.2cbg`, 0xc2, 0xc3, 0xc4 … in
`.2cba`). A game's records are found through the offsets at 0x08 and 0x10 of its
game header. (wch2)

Every game has an annotation record, even with nothing to annotate: 414 games of
`wch1` have no annotations, and in `wch2` each of them points at a record of
length 4 whose body is `00 00 00 00 7f ff ff ff`. There is no "offset 0 means
none" convention as in v1.

## Open questions

- Deleted entities: the record layout for the other entity types, what the
  `22 33 44 55 66 77 88 99` bytes are, and whether a deleted slot is reused when a
  new entity is added (and if so, which one, and how the list changes).
- In `1tour/reveng1.2lid`, player #2 is a valid (not deleted) record with both
  names empty (36 bytes).
- What 0x98 holds in a database ChessBase made itself, where it is a constant
  rather than a creation timestamp (see above).
- The deletion bit: the code tests bit 7 of the first byte, as v1 does, but no
  sample database has a deleted game to check it against.
- The layout of a guiding text record (see above), and what 0x02 and 0x03 are.
- What the endgame bits at 0xa8 mean, beyond the seven guessed above, and whether
  the field is more than 6 bytes.
- Whether 0x8c or 0x90 is white's final material.
- The rating type after each elo (see above); only `reveng1` has it.
- Subround (0x5c) and board (0x5e) are 0 everywhere except in `reveng1`, where
  they were set by hand.
- The unknown fields in every entity record, and the layout of the text title.
- Titled entities are still decoded by scanning for the first string in the
  record, which is wrong for a tournament (see above) and unchecked for source,
  team and game tag. Each needs its layout worked out from `wch2`.
- The 192-byte `.2cbh` file header: 0x0a holds the record size (192) and 0x10 the
  next game id (games + 1). 0x08 holds 38 and 0x0c holds 1280 in every sample.
