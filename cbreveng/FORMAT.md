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
- `probe` — 7 games built to order in ChessBase to pin down specific fields: a
  deleted game, a Chess960 game, a game with round/subround/board set, and games
  with different kinds of rating. Facts from it are marked **(probe)**.

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

An entity referred to by a game but left blank is stored as a real entity with
an empty name, and games point at it rather than at -1. `probe` has an empty
player at #2, which every game with no annotator refers to; the same holds for
game tag 0, and for tournament and source 0 in a database where they were never
filled in. The empty entity is created the first time it is needed, so its id is
not always 0. (probe)

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
The unknown ints are called `d1`–`d6` in the code, and they are **the same six
values in every player of every sample database** — 0, 0, -1, 8, -1, -1 across
all 134 players — so they carry nothing about the individual player. Nor would
there be much to carry: ChessBase does not keep a player's title, rating, birth
date or nation in the database file, but looks them up online, so a player record
really is just the two names. (probe)

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
| 4 | int | record length |
| 4 | int | length of place |
| n | string bytes | place |
| 4 | int | length of title |
| m | string bytes | title |
| 108 | | the fixed-size tail below |

The **place comes before the title**. In `reveng1` the place is empty, so the
record looks like it starts with a `?` int of 0, which is what these notes used
to say. The tail after the two strings is always exactly 108 bytes, and holds the
same fields as v1, in the same order, only widened and little-endian: (wch2)

| Offset in tail | Size | Type | Description |
|------|------|------|-------------|
| 0 | 4 | int | start date, see [Dates](#dates) |
| 4 | 1 | byte | type and time control, see below |
| 5 | 1 | byte | bit 0 = team tournament |
| 6 | 1 | byte | nation, see below |
| 7 | 1 | byte | `?` always 0 |
| 8 | 1 | byte | category |
| 9 | 1 | byte | flags: bit 0 and 1 = complete, bit 2 = board points, bit 3 = three points win |
| 10 | 1 | byte | number of rounds |
| 11 | 1 | byte | `?` always 0 |
| 12 | 4 | float | latitude of the place, 0 if not known |
| 16 | 4 | float | longitude |
| 20 | 40 | | `?` mostly zero, see below |
| 60 | 4 | int | end date |
| 64 | 44 | | `?` always 0 |

These rows account for all 108 bytes.

Bytes 20-59 of the tail are zero except for a `07` at 34, 45 and 56 — three of
something 11 bytes apart. The same three bytes, with the same spacing, sit in the
34 bytes that v1 skips over in its `.cbtt` file, so whatever it is, it was
carried across unchanged. v1 keeps the tiebreak rules right after them, ten bytes
that are all 1 with a count of 0 in every tournament of `wch1`, and nothing like
that run appears in the v2 records.

The low 5 bits of the type byte give the kind of tournament: 1 game, 2 match,
3 tournament (round robin), 4 open (swiss), 5 team, 6 knockout, 7 simul,
8 schevening. Bit 5 means blitz, bit 6 rapid and bit 7 correspondence; none set
means a normal time control.

The nation byte uses the v1 numbering, which `morphy/nations.py` lists as IOC
codes. In `wch2` it agrees with v1 for 38 of the 52 tournaments, and the rest are
a difference in the data rather than the encoding: see below.

Example, tournament #1 of `wch2`, 162 bytes with the tail starting at 54:

```
9e 00 00 00                              record length 158
06 00 00 00  48 61 76 61 6e 61           place "Havana"
24 00 00 00  57 6f 72 6c 64 2d 63 68 ... title "World-ch02 Steinitz-Chigorin +10-6=1"
34 c2 0e 00                              start date 1889-01-20
02                                       type: match
00 22 00                                 not a team tournament, nation CUB, ?
00 03 11 00                              category 0, complete, 17 rounds, ?
8e 29 b9 41  c3 db a4 c2                 23.1453, -82.4292
...                                      zero, apart from the three 07 bytes
58 c2 0e 00                              end date 1889-02-24 (at tail+60)
```

Tournament #0 of `wch2` is written differently from the other 51: it has no
coordinates and no end date, though v1 has an end date for it, it is missing the
three `07` bytes, and it alone has a 1 at tail+57. Nothing explains that yet.

##### What the conversion changed

Comparing all 52 tournaments of `wch2` with `wch1` confirms the layout: the start
date, type byte, team byte, category, flags and rounds agree for every single
one, and so do all 52 titles. The fields that disagree do so because ChessBase
rewrote the data, not because the encoding differs:

- Three places were renamed to ChessBase's own spelling, and v2 stores them as
  UTF-8 rather than v1's single-byte charset: `Duesseldorf/Munich` became
  `Düsseldorf Munich`, `Seville` became `Sevilla`, and `Vienna & Berlin` became
  `Vienne Berlin`.
- The nation and the coordinates were then re-derived from the place, sometimes
  wrongly. `Vienne Berlin` became France, and the London tournaments were placed
  at 43.0005, -81.2298, which is London in Ontario, so `London/Leningrad` became
  Canada. Every Soviet Union tournament became Russia, which looks deliberate.

v1's own coordinates are stored as **big-endian** doubles in `.cbtt`, not the
little-endian ones `ExtendedGameHeaderStorage` reads, which is why the Java tool
shows nonsense for them. v2 stores them as little-endian 32-bit floats, and the
two agree to four decimal places for the 48 tournaments that were not moved.

#### Source

| Size | Type | Description |
|------|------|-------------|
| 4 | int | record length |
| 4 | int | length of title |
| n | string bytes | title |
| 4 | int | length of publisher |
| m | string bytes | publisher |
| 4 | int | publication date, see [Dates](#dates) |
| 4 | int | date |
| 2 | short | version |
| 2 | short | quality: 0 unset, 1 high, 2 medium, 3 low |

The tail after the two strings is always 12 bytes. This is v1's layout with the
two fixed-size strings replaced by length-prefixed ones, and the version and
quality widened from a byte each. All six fields of all 24 sources in `wch2`
agree with v1 exactly. (wch2)

Example, source #0 of `wch2` (41 bytes):

```
25 00 00 00                              record length 37
08 00 00 00  4d 61 69 6e 42 61 73 65     title "MainBase"
09 00 00 00  43 68 65 73 73 42 61 73 65  publisher "ChessBase"
e1 9e 0f 00                              publication 1999-07-01
e1 9e 0f 00                              date 1999-07-01
02 00  01 00                             version 2, quality high
```

#### Team

| Size | Type | Description |
|------|------|-------------|
| 4 | int | record length |
| 4 | int | length of title |
| n | string bytes | title |
| 5 | | `?` all zero in the one team there is |

`reveng1` has the only team in any sample database, so only the title is
confirmed. v1 keeps a team number, a season flag, a year and a nation after the
title, which is 10 bytes; v2 has 5, so they cannot all be there in the same form.

#### Game tag

| Size | Type | Description |
|------|------|-------------|
| 4 | int | record length |
| 4 | int | number of titles |
| … | | that many titles, each as below |

Each title is:

| Size | Type | Description |
|------|------|-------------|
| 4 | int | language, numbered as the nations are, e.g. 42 English, 53 German |
| 4 | int | length of the title |
| n | string bytes | the title in that language |

v1 keeps eight fixed 200-byte titles, one per language, in a 1600-byte record;
v2 stores only the languages that are actually filled in. A game tag with no
title at all is just a record length of 4 and a count of 0, and that is what a
game with no game tag refers to: **game tag 0 is an empty placeholder**, where v1
would use -1. Every game tag in `wch2` and `reveng1` accounts for exactly its
record length this way. (wch2)

Example, game tag #4 of `wch2` (55 bytes), which has an English and a German
title:

```
33 00 00 00                                record length 51
02 00 00 00                                two titles
2a 00 00 00  11 00 00 00  "Tournament Report"
35 00 00 00  0e 00 00 00  "Turnierbericht"
```

Guiding texts use game tags for their own titles, which is why `wch2` has nine
game tags where v1 has two: seven of them are the titles of its 13 guiding texts,
referred to from offset 0x28 of a text record. (wch2)

#### Text title

No entities of this type exist in any sample database, so nothing is known.
`morphy/entitydb.py` still falls back to scanning for the first string in the
record for this one type.

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
| 0x00 | 1 | byte | type: bit 0 always set, bit 1 = guiding text, bit 7 = deleted (probe) |
| 0x01 | 1 | byte | `?` always 0 |
| 0x02 | 1 | byte | `?` always 1 (wch2) |
| 0x03 | 1 | byte | `?` always the same as the type byte at 0x00 (wch2) |
| 0x04 | 4 | | `?` always 0 |
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
| 0x5a | 2 | short | round (probe) |
| 0x5c | 2 | short | subround (probe) |
| 0x5e | 2 | short | board (probe) |
| 0x60 | 2 | short | white elo |
| 0x62 | 14 | | white rating type (probe), see [Rating type](#rating-type) |
| 0x70 | 2 | short | black elo |
| 0x72 | 14 | | black rating type |
| 0x80 | 2 | ushort | ECO opening code, or a Chess960 start position (wch2, probe), see [ECO](#eco) |
| 0x82 | 2 | ushort | medals, a bitmask (wch2), see [Flags and medals](#flags-and-medals) |
| 0x84 | 4 | uint | flags, a bitmask (wch2), see [Flags and medals](#flags-and-medals) |
| 0x88 | 2 | ushort | annotation magnitude flags (wch2) |
| 0x8a | 2 | short | number of full moves in the game |
| 0x8c | 4 | uint | final material of one player (wch2), see [Final material](#final-material) |
| 0x90 | 4 | uint | final material of the other player (wch2) |
| 0x94 | 4 | | `?` always 0 |
| 0x98 | 8 | long | creation timestamp (wch2, probe), see [Timestamps](#timestamps) |
| 0xa0 | 8 | long | last-changed timestamp (wch2), see [Timestamps](#timestamps) |
| 0xa8 | 6 | bitmask | endgame types the game passed through (wch2), see [Endgame types](#endgame-types) |
| 0xae | 10 | | `?` always 0 |
| 0xb8 | 4 | int | game version, increases by 1 on every save |
| 0xbc | 4 | int | encoded played date, see [Dates](#dates) |

The rows above account for all 192 bytes. The fields marked `?` are zero in every
game of every sample database, apart from 0x02 and 0x03 as noted. Two fields do
not use all the room they have: each final material is a 4-byte slot whose top
two bytes are always zero, and the endgame bitmask is 6 bytes followed by 10 more
that are always zero, so it could be anything up to 16.

The entity ids are 0-based ids into the `.2lid` file, in the block/slot sense
above. Verified with `inspect games` against the sample databases.

An annotator is an ordinary player, in the same type and the same id space: a
`probe` game annotated by "Probe, Delta" stores annotator id 4, which is exactly
the player Delta. (probe)

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
65536 - 960 is a Chess960 start position instead, `value - (65536 - 960)`, and
the `unorthodox` flag is set at the same time. A `probe` game entered as
Chess960 position 123 stores 64699, which is 65536 - 960 + 123, with flags
`0x08000005`. (wch2, probe)

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

Each elo is followed by 14 bytes saying what kind of rating it is, so white uses
0x60-0x6f and black 0x70-0x7f. v1 keeps the same idea in a 16-byte `RatingType`,
but only knows about international and national ratings.

| Offset after the elo | Size | Type | Description |
|------|------|------|-------------|
| 0 | 2 | short | kind in the bottom 3 bits, time control in the bits above |
| 2 | 2 | short | which rating list this is, see below |
| 4 | 2 | short | nation |
| 6 | 8 | string bytes | what the rating is called |

The kind is **1 international, 2 national, 3 server**, masked with 7 exactly as
v1 masks its own type byte. Shifting right by 3 gives the time control:
**0 normal, 1 bullet, 2 blitz, 3 rapid, 4 correspondence**. Bullet is new; v1 has
normal, blitz, rapid and correspondence only.

In ChessBase each kind has its own second choice: international is Elo or ICCF,
national picks a nation, and a server rating picks ChessBase, chess.com or
LiChess. Choosing ICCF and choosing Elo with the correspondence time control give
the same record, which is also how v1 behaves — it names an international rating
"ICCF" when the time control is correspondence and "FIDE" otherwise.

Every combination entered into `probe`: (probe)

| First short | Kind | Time control | List | Nation | Name |
|------|------|------|------|------|------|
| 1 | international | normal | 1 | 0 | `FIDE` |
| 25 | international | rapid | 3 | 0 | `FIDE` |
| 33 | international | correspondence | 4 | 0 | `ICCF` |
| 2 | national | normal | 100 | 53 GER / 134 SWE / 247 GBR | *(empty)* |
| 3 | server | normal | 10 | 0 | `chess.co` |
| 11 | server | bullet | 6 | 196 NET | `CB` |
| 19 | server | blitz | 16 | 196 NET | `LiChess` |
| 27 | server | rapid | 17 | 196 NET | `LiChess` |

The name is a fixed 8 bytes with no terminator when it fills them, so ChessBase
truncates `chess.com` to `chess.co` in the file — the tool is not cutting it
short. A national rating has no name at all; the nation says which one it is.

`inspect games` shows the elo and its rating type together, the way ChessBase
does: `2000-ChessBase-Bullet (NET)`, `2000-LiC-Rapid (NET)`, `2000-ELO-Rapid`,
`2000-corr`, `2000-CC`, `2000 (GER)`. The rating list is written under the name
ChessBase uses rather than the one stored in the record (`FIDE` as `ELO`, `ICCF`
as `corr`, `CB` as `ChessBase`, `chess.co` as `CC`, `LiChess` as `LiC`), the time
control is left out when it is normal or correspondence, an ordinary
international rating at normal time control is just the number, and an elo of 0,
which means the player has no rating, is left blank.

The second short is an id for the particular rating list, not for the kind: all
three national ratings use 100 whatever the nation, so it cannot be the nation,
and LiChess blitz and rapid are 16 and 17 while FIDE rapid and ICCF are 3 and 4 —
adjacent ids for adjacent time controls. It reads as a flat registry of the
rating lists ChessBase knows about, one entry per provider and time control, with
a single generic entry for national ratings. Which id is which beyond the eight
above is not known, and FIDE appears to have no bullet list.

A server rating stores the nation as `Internet` (196), which is a real entry in
the nation list — except for chess.com, which stores 0. Whether that is
deliberate is not clear from one sample.

### Dates
### Dates

A date is an int where bits 0-4 are the day, bits 5-8 the month and bits 9-20 the
year; higher bits are ignored, and a part that is 0 is unknown. This is the v1
encoding, and it fits the samples: the played dates 1037616 and 940544 are
2026-09-16 (the date the database was created) and 1837 with month and day
unknown. The same value, 1037616, is stored in source records and as `GameDate`
in the `.ini` file, so they use this encoding too.

### Timestamps

A game header holds two timestamps, both counting from a fixed moment:

- **Creation** (0x98): fractions of a second since 2008-12-01 in Europe/Berlin.
- **Last changed** (0xa0): 100 nanoseconds since 1582-10-15 UTC, the epoch UUID
  timestamps use. 0 means the game has never been changed since it was created.

The creation timestamp is written at **two different scales**. ChessBase writes
1/2²² of a second; v1 writes 1/2¹⁰ (1/1024) of a second, and converting a
database does not rescale the value, so `wch2` still holds v1-scale numbers. The
two are 4096 apart and nowhere near each other — a v1-scale value only reaches
2⁴⁰ in the 2040s, and a native one falls below it only within days of the epoch —
so the code picks the scale from the size of the value. (probe)

Read this way every game of `probe` was created before it was last changed, and
the seven creation times run in order from 20:50:20 to 20:59:12 on the day the
database was built, which is what entering seven games one after another looks
like.

The creation time is stamped once when the record is born and never touched
again. **Copying a game carries it across**, and editing the copy afterwards does
not reset it: `reveng1`'s two games are a game and a copy of it pasted into the
same database, and although they now have different players, different lengths,
different last-changed times and 7 against 17 saves, their creation times are
identical to the nanosecond. So the field doubles as a marker for where a game
originally came from, which is presumably the point of storing it at that
resolution.

It is a creation time rather than a unique id, even so: `wch2` matches v1's
`creationTimestamp` on all 1025 distinct values, and v1 derives a date from it.
A value that a copy inherits cannot identify a game on its own.

## Open questions

- Deleted entities: the record layout for the other entity types, what the
  `22 33 44 55 66 77 88 99` bytes are, and whether a deleted slot is reused when a
  new entity is added (and if so, which one, and how the list changes).
- In `1tour/reveng1.2lid`, player #2 is a valid (not deleted) record with both
  names empty (36 bytes).
- Which rating list each id in the second short of a rating type refers to,
  beyond the eight seen (see above), and why chess.com stores no nation while the
  other two servers store `Internet`.
- The layout of a guiding text record (see above), and what 0x02 and 0x03 are.
- What the endgame bits at 0xa8 mean, beyond the seven guessed above, and whether
  the field is more than 6 bytes.
- Whether 0x8c or 0x90 is white's final material.
- The rating type after each elo (see above); only `reveng1` has it.
- Subround (0x5c) and board (0x5e) are 0 everywhere except in `reveng1`, where
  they were set by hand.
- The unknown fields in every entity record, and the layout of the text title.
- The 5 bytes after a team's title, and the layout of a text title. Neither has
  enough data in any sample database: there is one team and no text titles.
- What the three `07` bytes in a tournament tail are, and why tournament #0 of
  `wch2` is written differently from the rest (see above).
- The 192-byte `.2cbh` file header: 0x0a holds the record size (192) and 0x10 the
  next game id (games + 1). 0x08 holds 38 and 0x0c holds 1280 in every sample.
