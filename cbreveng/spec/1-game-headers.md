# `.2cbh` — game headers

The spine of the database. A 192-byte file header is followed by one 192-byte
record per game, in game id order and with no gaps. Game ids are 1-based, so the
record for game *n* starts at `192 · n`. The number of records is
`(file size − 192) / 192`.

Records are never removed. A deleted game keeps its record and its id.

## File header

| Offset | Size | Type | Description |
|---|---|---|---|
| 0x00 | 8 | | unknown, always 0 |
| 0x08 | 2 | short | unknown, always 38 |
| 0x0a | 2 | short | record size, 192 |
| 0x0c | 1 | | unknown, always 0 |
| 0x0d | 1 | byte | format version, 5 |
| 0x0e | 2 | | unknown, always 0 |
| 0x10 | 4 | int | id of the next game to be added |
| 0x14 | 172 | | unknown, always 0 |

The next game id equals the record count plus 1. Deleting a game does not
change it.

## Record kinds

The byte at 0x02 gives the kind of record, and the type byte at 0x00 qualifies
it. A record is one of three things, each with its own layout:

| 0x02 | 0x00 bit 1 | Kind |
|---|---|---|
| 1 | clear | [game](#game-records) |
| 1 | set | [guiding text](#guiding-texts) |
| 2 | — | [analysis](#analyses) |

The first eight bytes are common to all three:

| Offset | Size | Type | Description |
|---|---|---|---|
| 0x00 | 1 | byte | type: bit 0 always set, bit 1 = guiding text, bit 7 = deleted |
| 0x01 | 1 | byte | unknown, always 0 |
| 0x02 | 1 | byte | record kind, see above |
| 0x03 | 1 | byte | the type byte without the deleted flag |
| 0x04 | 4 | | unknown, always 0 |

## Game records

| Offset | Size | Type | Description |
|---|---|---|---|
| 0x00 | 8 | | common header, above |
| 0x08 | 8 | long | offset of the moves in `.2cbg` |
| 0x10 | 8 | long | offset of the annotations in `.2cba` |
| 0x18 | 8 | long | white player, an entity id |
| 0x20 | 8 | long | black player |
| 0x28 | 8 | long | tournament |
| 0x30 | 8 | long | annotator, which is a player entity |
| 0x38 | 8 | long | source |
| 0x40 | 8 | long | white team, −1 if none |
| 0x48 | 8 | long | black team, −1 if none |
| 0x50 | 8 | long | game tag |
| 0x58 | 1 | byte | [result](#result-and-line-evaluation) |
| 0x59 | 1 | byte | line evaluation, when the result is *line* |
| 0x5a | 2 | short | round, 0 if not set |
| 0x5c | 2 | short | subround, 0 if not set |
| 0x5e | 2 | short | board, 0 if not set |
| 0x60 | 2 | short | white elo, 0 if none |
| 0x62 | 14 | | white [rating type](#rating-type) |
| 0x70 | 2 | short | black elo |
| 0x72 | 14 | | black rating type |
| 0x80 | 2 | ushort | [ECO](#eco) or Chess960 start position |
| 0x82 | 2 | ushort | [medals](#medals) |
| 0x84 | 4 | uint | [flags](#flags) |
| 0x88 | 2 | ushort | [annotation magnitudes](#annotation-magnitudes) |
| 0x8a | 2 | short | number of full moves in the main line |
| 0x8c | 4 | uint | [final material](#final-material), the greater of the two |
| 0x90 | 4 | uint | final material, the lesser |
| 0x94 | 4 | | unknown, always 0 |
| 0x98 | 8 | long | creation timestamp |
| 0xa0 | 8 | long | last-changed timestamp |
| 0xa8 | 6 | | [endgame types](#endgame-types) |
| 0xae | 2 | | unknown, always 0 |
| 0xb0 | 6 | | [classification scores](#classification-scores) |
| 0xb6 | 2 | | unknown, always 0 |
| 0xb8 | 4 | int | version, increased by one each time the record is saved |
| 0xbc | 4 | int | played date |

A game with no annotations still has an annotation record, so 0x10 always points
at one.

Entity references are never −1 except for the two teams. A field left blank by
the user points at an entity whose text is empty; see
[6-behaviour.md](6-behaviour.md#placeholder-entities).

### Result and line evaluation

| Value | Result |
|---|---|
| 0 | 0-1 |
| 1 | ½-½ |
| 2 | 1-0 |
| 3 | line (unfinished) |
| 4 | 1-0 on forfeit |
| 5 | ½-½ on forfeit |
| 6 | 0-1 on forfeit |
| 7 | 0-0, both lost |

When the result is 3 the byte at 0x59 holds a numeric annotation glyph giving the
evaluation of the position, using the standard NAG numbering (for example 15 is a
slight advantage for black). It is 0 otherwise.

### ECO

`value / 128 − 1` is the ECO code numbered from 0, so 0-99 are A00-A99, 100-199
B00-B99, and so on to E99. `value % 128` is the sub-ECO. A value of 0 means no
ECO.

A value of 64576 (`65536 − 960`) or more is not an ECO but a **Chess960 start
position**, `value − 64576`, numbered 0-959. The `unorthodox` flag is set at the
same time. Position 518 is the standard start position and is never stored this
way: such a game is an ordinary game with an ordinary ECO.

### Flags

A bitmask. Most bits say that the game carries annotations of a given kind.

| Bit | Meaning | Bit | Meaning |
|---|---|---|---|
| 0x00000001 | starts from a set-up position | 0x00080000 | game quotation |
| 0x00000002 | variations | 0x00100000 | pawn structure |
| 0x00000004 | commentary | 0x00200000 | piece path |
| 0x00000008 | symbols | 0x00400000 | white clock |
| 0x00000010 | coloured squares | 0x00800000 | black clock |
| 0x00000020 | arrows | 0x01000000 | critical position |
| 0x00000080 | time spent | 0x02000000 | correspondence header |
| 0x00000100 | annotation type 8 | 0x04000000 | annotation type 1a |
| 0x00000200 | training | 0x08000000 | unorthodox (Chess960) |
| 0x00010000 | embedded audio | 0x10000000 | web link |
| 0x00020000 | embedded picture | | |
| 0x00040000 | embedded video | | |

### Medals

A bitmask, one bit each from bit 0: best game, decided tournament, model game,
novelty, pawn structure, strategy, tactics, with attack, defence, sacrifice,
material, piece play, endgame, tactical blunder, strategical blunder, user.

### Annotation magnitudes

A bitmask qualifying some of the annotation flags with a rough size for that kind
of annotation. The individual bits are **unknown**.

### Final material

The material a player has left at the end of the game, packed into one value:

| Bits | Piece |
|---|---|
| 0-2 | rooks |
| 3-5 | bishops |
| 6-8 | knights |
| 9-11 | queens |
| 12-15 | pawns |

Bits 16-31 are always zero.

The two values are **not white and black**. The greater is at 0x8c and the lesser
at 0x90. Since pawns occupy the highest bits, the side with more pawns is
normally first. Storing them ordered lets a material search match either colour.

### Endgame types

A 48-bit mask of the kinds of endgame the game passed through. Bit numbering
starts at bit 0 of 0xa8. Most bits are **unknown**; the following fit the final
material of the games that set them:

| Bit | Endgame |
|---|---|
| 0 | bishop |
| 1 | knight against bishop |
| 13 | minor piece |
| 14 | knight |
| 24 | queen |
| 29 | bishop against rook |
| 35 | rook |

Bit 35 is by far the most common. The bits record what the game passed through
rather than where it ended, so they do not correspond exactly to the final
material.

### Classification scores

Six bytes holding the scores ChessBase computes when a database is classified:
beauty, "Top Game" and, apparently, theoretical importance. They are zero until
the database has been classified, and classifying changes nothing else in the
record but the version and last-changed timestamp.

The 48 bits are **unknown** in detail. They appear to be several small numbers
side by side. Bits 7, 8, 17, 33-35 and 43-47 are never set. Groups that vary
together, with the ranges observed:

| Bits | Range | Notes |
|---|---|---|
| 0-6 | 0-90 | contributes to beauty |
| 9-16 | 0-141 | contributes to beauty |
| 18-21 | 0 or 15 | all four bits always agree |
| 22-26 | multiples of 5 up to 30, and 31 | |
| 27-32 | 0-40 | contributes to beauty |
| 36-38 | 0-7 | grows with the length of the game |
| 39-42 | 1-15 | contributes to beauty; high for short decisive games |

Beauty is not stored as a value of its own and no bit window reproduces the level
ChessBase displays. A weighted sum of the four groups marked above, dominated by
bits 39-42, reproduces the ordering approximately but not exactly.

### Rating type

The 14 bytes after each elo describe the rating it belongs to.

| Offset | Size | Type | Description |
|---|---|---|---|
| 0 | 2 | short | kind and time control, see below |
| 2 | 2 | short | rating list |
| 4 | 2 | short | nation |
| 6 | 8 | | name, a fixed-size string |

The first short holds the kind in its bottom three bits and the time control
above them:

| Kind (`value & 7`) | | Time control (`value >> 3`) | |
|---|---|---|---|
| 1 | international | 0 | normal |
| 2 | national | 1 | bullet |
| 3 | server | 2 | blitz |
| | | 3 | rapid |
| | | 4 | correspondence |

The **rating list** identifies the particular list, one entry per provider and
time control. A national rating always uses 100, whatever the nation, so it does
not encode the nation. The values known:

| Id | List | Id | List |
|---|---|---|---|
| 1 | FIDE standard | 10 | chess.com |
| 2 | FIDE blitz | 16 | LiChess blitz |
| 3 | FIDE rapid | 17 | LiChess rapid |
| 4 | ICCF | 100 | any national rating |
| 6 | ChessBase server, bullet | | |

The **nation** is the nation of a national rating. A server rating uses 196
(`NET`, the internet), except chess.com, which stores 0 — whether deliberately is
**unknown**. An international rating stores 0.

The **name** is at most 8 bytes and has no terminator when it fills the field, so
`chess.com` is stored truncated as `chess.co`. A national rating has no name; the
nation identifies it. An international rating is named `ICCF` at the
correspondence time control and `FIDE` otherwise, so choosing ICCF and choosing
correspondence produce identical records.

An elo of 0 means the player has no rating.

## Guiding texts

A guiding text is a piece of writing filed among the games. Its record shares
only the first eight bytes with a game; everything from 0x08 on differs.

| Offset | Size | Type | Description |
|---|---|---|---|
| 0x00 | 8 | | common header, with bit 1 of the type byte set |
| 0x08 | 8 | long | offset of the text in `.2cbg` |
| 0x10 | 8 | long | tournament |
| 0x18 | 8 | long | source |
| 0x20 | 8 | long | annotator, who is the author of the text |
| 0x28 | 8 | long | game tag, which holds the **title** of the text |
| 0x30 | 8 | long | creation timestamp |
| 0x38 | 8 | long | media offset, see below |
| 0x40 | 8 | long | version |
| 0x48 | 120 | | unknown, always 0 |

A guiding text has **no annotation record**, no last-changed timestamp, and no
title of its own: the title is a game tag entity, which carries one title per
language. See [4-entities.md](4-entities.md#game-tags-and-text-titles).

The **media offset** is **unknown** in its high bits. Some texts set bit 49 above
an otherwise ordinary offset; others have no high bits; a text written in
ChessBase has 0; and `0xffffffff` occurs where there is no media.

No field holds a text's round.

## Analyses

A third kind of record, told apart by the byte 2 at 0x02. An analysis has moves
and annotations like a game, but a header of its own.

| Offset | Size | Type | Description |
|---|---|---|---|
| 0x00 | 8 | | common header |
| 0x08 | 8 | long | offset of the moves in `.2cbg` |
| 0x10 | 8 | long | offset of the annotations in `.2cba` |
| 0x18 | 8 | long | game tag, which holds the **title** of the analysis |
| 0x20 | 8 | long | source |
| 0x28 | 8 | long | annotator, the author |
| 0x30 | 8 | | unknown, large values, possibly a timestamp |
| 0x38 | 8 | | unknown, 776 in every analysis examined |
| 0x40 | 8 | | unknown, small numbers |
| 0x48 | 8 | | unknown, large values, possibly a timestamp |
| 0x50 | 8 | | unknown, small numbers |
| 0x58 | 104 | | unknown, always 0 |

Titles are opening lines such as `1.d4 d5 2.c4 c6 3.♘c3`, written with figurine
characters.
