# `.2cba` — annotations

Holds the annotations of every game and analysis: comments, symbols, arrows,
evaluations and the rest. A 12-byte file header is followed by one record per
game, in game id order and back to back. The record for a game is found through
the offset at 0x10 of its `.2cbh` record.

**Every game has a record**, even when it has no annotations. Guiding texts have
none.

## File header

The same layout as the [`.2cbg` header](2-moves.md#file-header): the file size as
a `long`, then the header size 12 as a `short`. The byte at 0x0a is always 0
here.

## Records

Records use the same framing as `.2cbg`, including the
[checksum](2-moves.md#checksum), computed over the content after its `00 20` tag.

The content is:

| Size | Type | Description |
|---|---|---|
| 2 | | `00 20` |
| … | | position blocks, in ascending order of position |
| 4 | int | `7fffffff`, the end marker |

A position block groups every annotation attached to one position:

| Size | Type | Description |
|---|---|---|
| 4 | int | the [position](#positions) |
| 4 | int | the number of annotations |
| … | | that many annotations, each a `short` type followed by its data |

A game with no annotations is just `00 20 ff ff ff 7f`, whose checksum is
therefore always `00 00 00 00 7f ff ff ff`.

Annotations carry **no length field**, so a reader must understand every type it
meets in order to find the end of one and the start of the next.

## Positions

Position −1 refers to the game as a whole, before the first move. Otherwise a
position is the index of a move in the tree, counting from 0 **in the order a PGN
would list them**: each alternative, together with everything that follows it,
comes immediately after the move it is an alternative to, before the main line
continues.

Note that this is not the order the moves are stored in; see
[the move tree](2-moves.md#the-move-tree).

In `1.e4 c5 (1...c6 2.d4) 2.Nf3` the positions are e4 0, c5 1, c6 2, d4 3,
Nf3 4.

Annotations attached to the same position are stored in a meaningful order and a
reader should preserve it.

## Types

| Type | Name | Data |
|---|---|---|
| `02` | text after move | [below](#text) |
| `82` | text before move | as `02` |
| `03` | symbols | 3 bytes: move, evaluation, prefix |
| `04` | coloured squares | `int` length, then that many bytes |
| `05` | arrows | `int` length, then that many bytes |
| `07` | time spent | 4 bytes |
| `09` | training | [below](#training) |
| `13` | game quotation | [below](#game-quotation) |
| `14` | pawn structure | 1 byte |
| `15` | piece path | `int` length, then that many bytes |
| `16` | white clock | 4 bytes, **unknown** |
| `17` | black clock | 4 bytes, **unknown** |
| `18` | critical position | 1 byte |
| `1c` | web link | `01`, then two strings: the URL and a caption. **Unknown** in detail |
| `20` | video | **unknown** |
| `21` | computer evaluation | 6 bytes, **unknown** |
| `22` | medals | 4 bytes |
| `23` | variation colour | 4 bytes |
| `24` | time control | `01`, three 12-byte series, `int` 0; 38 bytes in all |
| `25` | video stream time | 4 bytes |
| `26` | evaluations | [below](#evaluations) |

Types `08` and `1a`, and sound, picture and correspondence annotations, are
referred to by the [flags](1-game-headers.md#flags) but have not been observed.

### Text

| Size | Type | Description |
|---|---|---|
| 2 | short | always 0 |
| 2 | short | language, see below |
| 4 | int | length in bytes |
| *n* | | the text |

The language is a small number, **not** a nation code:

| Value | Language |
|---|---|
| 0 | English |
| 1 | German |
| 2 | French |
| 3 | Spanish |
| 4 | Italian (probable; not confirmed) |
| 5 | Dutch |
| 6 | Portuguese |
| 7 | any language |
| 12 | Polish |
| 18 | Greek |

**The text encoding is not declared and is not consistent.** Some comments are
UTF-8 and others are cp1252, within the same database and the same language, and
no field distinguishes them. A reader should attempt UTF-8 and fall back to
cp1252. Figurines appear as private-use characters from U+E024 to U+E029. The
marker `[#]` in a comment asks for a diagram.

### Evaluations

Engine evaluations for the moves of the main line, stored at position −1.

| Size | Type | Description |
|---|---|---|
| 1 | | `01` |
| 4 | int | number of bytes that follow |
| 2 | short | number of entries |
| 4 · *n* | | the entries |

Each entry:

| Size | Type | Description |
|---|---|---|
| 2 | short | evaluation in centipawns, or moves to mate |
| 1 | byte | search depth |
| 1 | byte | 0 ordinary, `ff` none (value and depth 0), 1 apparently mate; 2 and `20` also occur and are **unknown** |

The number of entries does not always match the length of the main line: they are
not rewritten when moves are added afterwards.

### Training

A training question. The layout below accounts for the observed records but is
**not fully reliable**: some records cannot be parsed by it.

| Size | Description |
|---|---|
| 6 | `01 01 01 00 00 00` |
| 4 | `int`, time allowed in seconds |
| 2 | `short`, points |
| … | a list of texts, see below |
| 6 | unknown, zero |
| 1 | number of solutions |
| … | per solution: origin and destination square, 1 byte each; 2 unknown bytes; then a list of texts |

A list of texts is a `short` count, then per text a `short` (0), an `int` length
and the bytes.

### Game quotation

A complete game embedded in a comment, with its own header and optionally its
moves.

| Size | Description |
|---|---|
| 1 | `01` |
| 2 | `short`, 1 for the header only, 2 with moves |
| 2 | `short`, unknown |
| 4 | `int` 1 |
| 1 | 0 |
| … | six strings: white last name, white first name, black last name, black first name, site, event. Each is a length byte counting a terminating zero, the text, and the zero |
| 35 | date (`int`), event type, nation, category, rounds, white and black elo, ECO, result — **unknown** in detail |
| 44 | unknown, mostly zero |
| … | two rating types, each `01 00 01 00 00` and a string naming the list |
| 29 | unknown |
| 4 | `int`, number of moves |
| 5 · *n* | the moves: origin and destination square, 1 byte each, then 3 bytes **unknown**, presumably promotion |
| 4 | `int` 0 |
