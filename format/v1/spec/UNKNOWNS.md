# What is not known

Everything the specification leaves open, gathered in one place. Each entry is
marked **unknown** where it occurs as well; this is the list to work from.

Most entries are waiting on analysis: there is data, and what is missing is the
meaning. A few are waiting on a **sample**, since nothing in any database examined
contains one, and are marked as such.

Fields that are simply zero in every database are marked where they occur and are
not repeated here.

## `.cbh` `.cbj` `.flags` — [game headers](1-game-headers.md)

- **[Endgame types](1-game-headers.md#endgame-information)**: what the numbers in
  the endgame information stand for, and how the endgames a game passes through
  are chosen.
- **[Flags](1-game-headers.md#flags)**: the bits that are not listed; whether the
  flags marked *likely* are what they seem, which needs a **sample**; and what
  annotation types `08` and `1a` are.
- **[The `.cbj` record](1-game-headers.md#records)**: the two integers at 0x46 and
  0x4a.
- **[The `.cbj` versions](1-game-headers.md#cbj--the-extended-headers)** 1, 5, 6 and 7:
  which fields they add. Their record sizes are from older documentation, and no
  database examined uses them.

## `.cbg` — [moves](2-moves.md)

- **[The record flags](2-moves.md#game-records)**: what bit 7 means in a game.
  Needs a **sample**.
- **[Chess960 games](2-moves.md#start-position)** with extra bytes that hold
  neither squares nor a start position number.
- **[The file header](2-moves.md#file-header)**: why the number of bytes in holes
  is a little lower than the sum of the holes.
- **How a game that grows is written**, when the records after it are in the way.
- **[Encoding modes](2-moves.md#encoding-modes)** 12 to 19 and 63: what
  they encode. Needs a **sample**.
- **[Guiding texts](2-moves.md#guiding-texts)**: the formatting data that follows
  a plain text in versions 1 and 2, and the byte before the contents.

## `.cba` — [annotations](3-annotations.md)

- **[Types](3-annotations.md#types)** `08` and `1a`: what they hold. The `15` piece
  path: its two bytes. The fourth byte of the `07` time spent.
- **[Web links and videos](3-annotations.md#types)**, types `1c` and `20`: their
  layout. Needs a **sample**.
- **Sound, picture and correspondence annotations**: which types they use, if
  any.
- **[Colours](3-annotations.md#squares-and-arrows)** other than green, yellow and
  red: the values 7, 8 and 9 on arrows.
- **[Evaluations](3-annotations.md#evaluations)**: the entry kinds other than 0,
  1 and `ff`.
- **[Time control](3-annotations.md#time-control)**: the series type 2.
- **[Training](3-annotations.md#training)** and
  **[game quotations](3-annotations.md#game-quotation)**: the layout of the rest.
- **[Text](3-annotations.md#text)**: the byte before the language, and which code
  page a single-byte text is in.

## `.cbp` `.cbt` `.cbtt` `.cbc` `.cbs` `.cbe` `.cbl` — [entities](4-entities.md)

- **[Tournament tie-break rules](4-entities.md#additional-tournament-information)**:
  the ids 15, 17, 19 and 20, and whether 203, 204 and 206 are what they seem.
- **[The `.cbtt` records](4-entities.md#additional-tournament-information)**: the
  34 bytes from 0x10, and the bytes added in versions 4 and 5, some of which hold
  text.
- **[Sort order](4-entities.md#sort-order)** of sources: whether the bytes are
  compared as signed or unsigned. Needs a **sample**: a source title with a
  character above `0x7f` next to one without.

## `.cit` `.cib` `.cbb` `.cbgi` — [search boosters](5-search-boosters.md)

- **[`.cit` and `.cib`](5-search-boosters.md#cit-and-cib--the-games-of-an-entity)**:
  the two integers in the header of `.cit`, and the second field of a block.

## `.cbm` — [multimedia](6-multimedia.md)

- **[Embedded pictures](6-multimedia.md#embedded-pictures)**: how a game or a text
  refers to one, and the sound and video files that correspond.

## [Behaviour](7-behaviour.md)

- **[Free space](7-behaviour.md#free-space-in-the-move-and-annotation-files)**:
  how a game that grows is written.
