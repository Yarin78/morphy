# What is not known

Everything the specification leaves open, gathered in one place. Each entry is
marked **unknown** where it occurs as well; this is the list to work from.

Two kinds of entry appear below. Most are waiting on analysis: there is data,
and what is missing is the meaning. A few are waiting on a **sample** — nothing
in any database examined contains one — and are marked as such, since no amount
of work on the databases at hand will settle them.

Fields that are simply zero in every database are marked where they occur and
are not repeated here.

## `.2cbh` — [game headers](1-game-headers.md)

- **[Endgame types](1-game-headers.md#endgame-types)**: which of the matchups a
  game passes through are given a bit. Holding one for 5 plies is necessary but
  not sufficient, and the matchup the game ends in is the likeliest to be
  recorded.
- **[Classification scores](1-game-headers.md#classification-scores)**: the 48
  bits in detail, and how the beauty, Top Game and theoretical importance levels
  ChessBase displays follow from them.
- **[Guiding texts](1-game-headers.md#guiding-texts)**: the high bits of the
  media offset. Some texts set bit 49 above an otherwise ordinary offset.
- **[Analyses](1-game-headers.md#analyses)**: the five fields from 0x30 to 0x50.
  Two hold large values that could be timestamps, two hold small numbers, and
  one is always 776.
- **[Rating type](1-game-headers.md#rating-type)**: why a chess.com rating
  stores nation 0 where the other servers store 196.
- **[File header](1-game-headers.md#file-header)**: the 38 at 0x08, and the
  first 8 bytes.

## `.2cbg` — [moves](2-moves.md)

- **[File header](2-moves.md#file-header)**: the byte at 0x0a, which is 1 in a
  database with no games and 0 otherwise.
- **[Free space](6-behaviour.md#free-space-in-the-move-and-annotation-files)**:
  what happens when a record outgrows all the spare in the records after it, or
  when the last record in the file grows.

## `.2cba` — [annotations](3-annotations.md)

- **[Type `08`](3-annotations.md#types)**: 4 bytes, one sample, value 6.
- **[Type `1c`](3-annotations.md#types)**, web link: a byte `01` and two strings,
  the URL and a caption, but the pieces have not been checked one by one.
- **[Time spent](3-annotations.md#types)**: the first of its 4 bytes.
- **[Evaluations](3-annotations.md#evaluations)**: the flag values 2 and `20`.
- **[Computer evaluation](3-annotations.md#computer-evaluation)**: the kinds 3
  and 32, and why some games hold the absolute value of the score in place of
  the depth.
- **[Training](3-annotations.md#training)**: the two bytes after each solution's
  squares, and the layout of a record whose third byte is 2 rather than 1.
- **[Game quotation](3-annotations.md#game-quotation)**: most of its fixed
  blocks — the 35 bytes of game data in detail, the 44 mostly-zero bytes, the 29
  bytes before the move count, and the 3 bytes after each move's squares.
- **Sample**: type `1a`, and the sound, picture, video and correspondence
  annotations. The [flags](1-game-headers.md#flags) name them, but no game sets
  those flags.

## `.2lid` — [entities](4-entities.md)

- **[Entity type 3](4-entities.md#entity-types)**: it has a container in every
  database and has never held an entity, so what it is for is unknown. Also a
  **sample** question: nothing creates one.
- **[File header](4-entities.md#file-header)**: the 56 bytes at 0x80, which do
  not vary and are not counts.
- **[Player](4-entities.md#player)**: the two zero `int`s before the ChessBase
  id, and the 8 before the FIDE id.
- **[Tournament](4-entities.md#tournament)**: the 37 bytes at offset 21,
  including the three 7s 11 bytes apart and the flag at 57.
- **[Deleted entities](4-entities.md#deleted-entities)**: the
  `22 33 44 55 66 77 88 99` every deleted record holds.

## `.2lcd` and `.2lgd` — [indexes](5-indexes.md)

- **[Catalog](5-indexes.md#catalog)**: the byte at 0x17, always 3; the byte at
  0x18, which is the index's number within its entity type but whose purpose is
  unclear; the byte at 0x19, always 1; and the `int` at 0x00 of the file header,
  always 1.
- **[Nodes](5-indexes.md#nodes)**: which leaves hold −1 in place of their slot
  number.
- **[How text is compared](5-indexes.md#how-text-is-compared)**: the exact
  treatment of apostrophes and hyphens. A few neighbouring pairs in large
  databases do not fit the rules given.
- **[`.2lgd`](5-indexes.md#2lgd--the-games-of-each-entity)**: the 256 at 0x00 of
  the file header and the 0 at 0x08, and slot 30 of a record, always 12.
