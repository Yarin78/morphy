# ChessBase "2" format: moves and annotations

Working notes on the `.2cbg` (moves) and `.2cba` (annotations) files of the
format described in [FORMAT.md](FORMAT.md). The conventions are the same as
there: offsets are hexadecimal and relative to the start of the structure,
integers are little-endian (except the [checksum](#the-checksum-at-0x10) of each
record), and **(wch2)**, **(probe)** and **(observed)** say
what a fact rests on. This file is the source of truth for `morphy/moves.py` and
`morphy/annotations.py`.

## Summary

**The moves are not encoded as in v1.** Everything about the encoding is new:

- v1 writes most moves as one byte that says which piece moves and in what
  direction, relative to a numbering of the pieces ("the second knight") that
  changes as pieces are captured, and obfuscates each byte with a translation
  table and the number of moves so far. Decoding a move needs the position.
- v2 writes every move as one plain **16-bit word** that says which piece moves,
  from which square to which, what it captures and what it promotes to. There is
  no obfuscation, and a word means the same thing in any position, so a game can
  be decoded without a board.
- Variations are also stored in a different order (see [The move
  tree](#the-move-tree)).

What does carry over from v1 is the square numbering (`a1` = 0, `a2` = 1, …,
`h8` = 63, file by file) and the idea of a null move.

The decoding below was checked against every game of `wch2`: decoding the
`.2cbg` file gives exactly the move tree the Java code reads from v1's `.cbg` in
`wch1`, variations included, for all 1025 games. They contain 16,784 variations,
34 null moves, 239 promotions and 128 en passant captures. (wch2)

The v1 side of the check comes from the `DumpMoves` tool in `morphy-tools`. It
writes each game's move tree to a file, one game per line, in UCI notation with
variations in parentheses:

```
mvn -pl morphy-tools exec:java -Dexec.mainClass=se.yarin.morphy.tools.DumpMoves \
    -Dexec.args="cbreveng/wch1/World-ch.cbh wch1.moves"
```

On the v2 side, `morphy/moves.py` gives the tree for the same game id. The two
agree once castling is written as the king's move and a null move as `0000`.

## `.2cbg` — moves

### File header

12 bytes:

| Offset | Size | Type | Description |
|--------|------|------|-------------|
| 0x00 | 8 | long | file size in bytes |
| 0x08 | 2 | short | header size (12) |
| 0x0a | 2 | | `?` `00 05`, or `01 00` in an empty database (observed) |

The `.2cba` header has the same size and the same header size field, but
`00 00` at 0x0a.

### Records

One record per game or guiding text follows the header, in game id order and
back to back:
each record starts where the previous one ends, with no gaps. A game's `.2cbh`
record has the offset of its `.2cbg` record at 0x08. The last record ends at the
end of the file. (wch2)

| Offset | Size | Type | Description |
|--------|------|------|-------------|
| 0x00 | 8 | | magic `88 77 66 55 44 33 22 11` |
| 0x08 | 4 | int | `A`, the size of the content minus 2 |
| 0x0c | 4 | int | `B`, the size of the spare area |
| 0x10 | 8 | | a checksum of the content, see [below](#the-checksum-at-0x10) |
| 0x18 | A + 2 | | the content: a game's [word stream](#the-word-stream), or a [guiding text](#guiding-texts) |
| 0x1a + A | B | | spare, all zero |
| 0x1a + A + B | 8 | long | record length, which is `A + B + 34` |

`A` is the size of the content after its first 2 bytes (the variant of a game,
`00 10` of a text, `00 20` of annotations), which is exactly the part the
[checksum](#the-checksum-at-0x10) covers. For a game, it can look as if `A`
leaves out the final `ff ff` instead, since that is also 2 bytes. (wch2, probe)

#### The spare area

The spare area is zero in every record. When ChessBase writes a record in one
go, the spare area is 96 bytes for a game, or 192 for a text, plus the 1 to 8
bytes that make the record **end at a file offset that is a multiple of 8**.
So `B` is 98, 100, 102 or 104 for a game (an odd `B` never
comes up, since the stream is a whole number of words) and 193 to 200 for a
text. (wch2, probe)

It is room for the game to grow in place, and when that is not enough, the
games after it give up theirs. In `probe`, two games that had no moves were
edited in three rounds, with the records given as offset, A, B, length:
(probe)

| Game | At first | Round 1 | Round 2 | Round 3 |
|------|------|------|------|------|
| 1 | 12, 4, 102, 140 | 9 plies added: 12, 22, 84, 140 | 5 removed: 12, 12, 94, 140 | 12, 12, 94, 140 |
| 2 | 152, 4, 98, 136 | 89 plies added: 152, 182, 0, 216 | 4 more: 152, 190, 0, 224 | 10 more: 152, 210, 0, 244 |
| 3 (not edited) | 288, 4, 98, 136 | 368, 4, 18, 56 | 376, 4, 10, 48 | 396, 4, 0, 38 |
| 4 (not edited) | 424, 4, 98, 136 | 424, 4, 98, 136 | 424, 4, 98, 136 | 434, 4, 88, 126 |
| 5 (not edited) | 560, 4, 98, 136 | 560, 4, 98, 136 | 560, 4, 98, 136 | 560, 4, 98, 136 |

- A game that **fits its record** is rewritten in place, and the record keeps its
  length: game 1 took 18 bytes of its spare area, and gave 10 back when it got
  shorter. Nothing else moved.
- A game that **outgrows its record** takes only what it needs from the games
  after it. Game 2 needed 80 bytes more, and then another 8. Each time
  ChessBase moved game 3 up by that much, took it out of game 3's spare area,
  and changed game 3's offset in the `.2cbh` file. Game 2 was left with no
  spare at all, not the usual 96.
- When the next game does not have enough spare, the **squeeze cascades**. In
  round 3, game 2 needed 20 more bytes. Game 3 had only 10 spare, so it gave up
  all of them and moved up by 20, and game 4 gave up the other 10 and moved up
  by 10. Game 5 and everything after it stayed where they were, and the file
  kept its size.

The spare areas taken this way are exact, so records that have been squeezed no
longer end on an 8-byte boundary: after round 3, game 2 ends at 396 and game 3
at 434. The alignment only holds when a record is written from scratch.

Nothing was moved to the end of the file, and the file has no holes. Unlike v1,
where a shortened game leaves unused bytes between games that the file header
counts, here they are simply spare. Game 2 of `reveng1`, with 82 spare bytes,
was edited in the same way while it was being entered.

Presumably a game at the end of the file, or one that needs more room than all
the games after it have to spare, makes the file longer. That has not been seen.

### The word stream

The stream is a sequence of 16-bit little-endian words. Words from `0xfffa`
upward are markers. The words below them are all numbered in one scheme: first
every [move](#move-words), then every [piece of a setup
position](#setup-positions), up to `c30c`. No other words have been seen.

| Word | Meaning |
|------|---------|
| `fffa` | null move |
| `fffb` | section: the start position |
| `fffc` | section: the moves |
| `fffd` | the move just given has another alternative, which comes later |
| `fffe` | never seen |
| `ffff` | end of a line |

The stream starts with the **variant**: 1 for normal chess, 2 for Chess960.
Sections follow, each a marker and the words up to the next marker:

- `fffb`, the start position, only when the game does not start from the
  normal position:
  - In a Chess960 game it is one word, the start position number (0-959). The
    `probe` games entered as positions 123 and 566 have `0002 fffb 007b fffc …`
    and `0002 fffb 0236 fffc …`, and their `.2cbh` records give the same
    positions in the ECO field. (probe)
  - In a normal game it is a [setup position](#setup-positions).
- `fffc`, the moves: the [move tree](#the-move-tree), which runs to the end of
  the stream.

Every game in `wch2` starts from the normal position, so its stream starts
`0001 fffc`. A game with no moves is `0001 fffc ffff`.

Examples from `probe`, with the words written as numbers (the bytes are the
other way round):

```
0001 fffc ffff                       no moves
0001 fffc ace1 ffff                  1.d4
0002 fffb 007b fffc ad40 afcf ffff   Chess960 #123: 1.e3 d5
```

These notes used to read the first word as a count of sections, which fitted
every sample until the setup game: it has two sections, but its first word is 1.

### Move words

A move word stands for one move: the piece that moves, its from and to squares,
the piece it captures, and the piece it promotes to. The words are simply every
such move numbered in a fixed order. Word 0 is not used, and the moves run from
1 to `c02c`, 49,196 words in all:

| Words | Moves |
|-------|-------|
| `0001`–`09d8` | white king |
| `09d9`–`2bf8` | white queen |
| `2bf9`–`33d8` | white knight |
| `33d9`–`40f8` | white bishop |
| `40f9`–`55f8` | white rook |
| `55f9`–`5fd0` | black king |
| `5fd1`–`81f0` | black queen |
| `81f1`–`89d0` | black knight |
| `89d1`–`96f0` | black bishop |
| `96f1`–`abf0` | black rook |
| `abf1`–`ae8c` | white pawn |
| `ae8d`–`b128` | black pawn |
| `b129`–`b12c` | castling: white O-O-O, white O-O, black O-O-O, black O-O |
| `b12d`–`c02c` | castling in Chess960, the same four for each start position |

#### Pieces other than pawns

In each piece's block, the moves are listed from each square in turn, `a1`,
`a2`, … `h8`. From each square, the destinations the piece could reach on an
empty board are listed in a fixed order of directions, going along each
direction one square at a time:

| Piece | Directions, as (file, rank) steps |
|-------|------------|
| king | (−1,−1) (−1,0) (−1,+1) (0,−1) (0,+1) (+1,−1) (+1,0) (+1,+1) |
| knight | (−2,−1) (−2,+1) (+2,−1) (+2,+1) (−1,−2) (−1,+2) (+1,−2) (+1,+2) |
| bishop | (−1,−1) (+1,−1) (+1,+1) (−1,+1) |
| rook | (−1,0) (0,−1) (+1,0) (0,+1) |
| queen | the four bishop directions, then the four rook directions |

The king's order is the same as the numbering of its destination squares. The
knight's is not.

Each destination then takes **6 consecutive words**: the move without a capture,
then capturing a queen, knight, bishop, rook or pawn, in that order. So each
block is 6 times the number of moves the piece has on an empty board (420 for
the king, 1456 queen, 336 knight, 560 bishop and 896 rook).

For example, the white king on `a1` can go to `a2`, `b1` and `b2`, so `a1-a2` is
word 1 and `a1-b1` is word 7. The white king block starts at 1, so the queen
block starts at 1 + 6 · 420 = `09d9`. The table takes no account of whether a
move can ever happen: it has words for capturing a pawn on the first or last
rank, too.

#### Pawns

Pawns are listed by file (`a` to `h`), and in each file by square from rank 2 to
rank 7, for both colours. For each square:

1. The moves forward: from the starting rank the double step and then the
   single step; from the rank before promotion the four promotions, to queen,
   knight, bishop and rook; otherwise just the single step.
2. The capture toward the lower file, then toward the higher file (only one on
   the `a` and `h` files). Each is 5 words, one for each piece that can be
   captured (queen, knight, bishop, rook, pawn), and 6 on the rank where en
   passant is possible, the sixth being the en passant capture. On the rank
   before promotion, a capture is 16 words: the four capturable pieces (queen,
   knight, bishop, rook) × the four promotions.

This gives 668 words for each colour. Promotions to queen and knight are
confirmed by `wch2`, and to bishop and rook by the `probe` setup game, which has
one of each. (wch2, probe)

The black pawns are listed in the same square order as the white ones, so the
black block starts with the promotions from `a2`. En passant is on rank 5 for
white and rank 4 for black.

#### Castling

Castling is written as neither a king move nor a rook move, but as four words of
its own: `b129` white O-O-O, `b12a` white O-O, `b12b` black O-O-O, `b12c` black
O-O. (wch2)

A Chess960 game uses a different four words for each start position, the four
for position `n` starting at `b12d + 4n`, in the same order. The `probe` game
from position 566 castles with `ba05` (white O-O-O, `b12d + 4·566`) and `ba08`
(black O-O, 3 more). They were the only castling moves available at that point,
so what they mean is not in doubt. (probe)

Chess960 position 518 is the normal start position, and ChessBase does not treat
a game from it as Chess960 at all. The `probe` game entered as Chess960 position
518, castling on both sides, is stored as an ordinary game: variant 1, no start
position section, castling with `b12a` and `b12c`. Its `.2cbh` record has ECO
C42 and no unorthodox flag. So the words for position 518 are never used.
(probe)

#### Setup positions

A game that starts from a set-up position has a start position section with
three words and then one word for each piece on the board:

| Word | Description |
|------|-------------|
| 0 | the move number |
| 1 | low byte: side to move, 0 white, 1 black; high byte: en passant file, 1-8 for a-h, 0 if none |
| 2 | castling rights: 1 white O-O-O, 2 white O-O, 4 black O-O-O, 8 black O-O |
| 3… | the pieces, see below |

These are v1's values for the side to move, the en passant file and the
castling rights, only laid out differently. Two `probe` games confirm them: one
is white to move at move 1 with no castling and no en passant, the other black
to move at move 263 with e.p. on the f file, white able to castle short and
black long: (probe)

```
0001                      normal chess
fffb                      start position
0001 0000 0000            move 1, white to move, no e.p., no castling
c2dd c183 c047 c2fb       pa2 kc7 Kd3 pf2
c2d0 c301 c2d6            Pf7 pg2 Pg7
fffc                      the moves
0409 b03b ae38 9259 5329  Kd3-e4 f2-f1=B g7-g8=R Bf1-c4 Rg8-g7
ffff

0001 fffb
0107 0601 0006            move 263, black to move, e.p. on f, white O-O and black O-O-O
c12d c2e2 c274 …          Ra1 pa7 ra8 …
fffc b016 b12a b05d ffff  263...exf3 e.p. 264.O-O f2
```

A piece word is `c02d` plus the number of the piece and square, right after the
last move word. They are numbered in the same order of pieces as the move
words: white K, Q, N, B, R with 64 squares each, then the same for black, then
white pawns and black pawns with 48 squares each, as they can only be on ranks 2
to 7:

| Words | Pieces |
|-------|--------|
| `c02d`–`c16c` | white K, Q, N, B, R: `c02d` + 64 · piece + square |
| `c16d`–`c2ac` | black k, q, n, b, r |
| `c2ad`–`c2dc` | white pawns: `c2ad` + 6 · file + rank − 2 |
| `c2dd`–`c30c` | black pawns |

The pieces are listed in square order. Both positions fit their games: every
move is legal from them, and the final material in the first game's `.2cbh`
record matches (black a bishop and two pawns, white a rook and a pawn). (probe)

### The move tree

The main line comes first, all the way to the end. The alternatives come after
it. The rule:

- A move is written as its word, and becomes the current position.
- `fffd` after a move means the move has **another alternative**, a sibling that
  comes later. The position it was played from is remembered, on a stack.
- `ffff` ends the current line. If the stack is empty, the stream is over.
  Otherwise the last position on the stack is taken off, and the next move is
  the next alternative from that position.

So alternatives are written **last in, first out**: the deepest, most recent
branch point is taken up first. v1 does it the other way round, with each
variation written in full right where it branches off.

The line from the v1 notes

    1. e4 c5 (1... c6 2. d4) (1... Nf6 2. e5) 2. Nf3 d6 (2... Nc6 3. Bb5) 3. d4

is written

    e4 c5 fffd Nf3 d6 fffd d4 ffff Nc6 Bb5 ffff c6 fffd d4 ffff Nf6 e5 ffff

`c5` gets a `fffd` because `c6` and `Nf6` are still to come. `d6` gets one
because `Nc6` is still to come. The main line ends at `3. d4`. Then the
most recent branch point, the one before `d6`, is taken up first: `Nc6 Bb5`.
After that comes `c6`, which gets a `fffd` of its own because `Nf6` still
follows, and finally `Nf6 e5`.

There is always exactly one `fffd` for each `ffff` other than the last. (wch2)

A null move (`fffa`) is written in the tree like any other move.

### Example

The start of game 1 of `wch2`, 1.d4 d5 2.c4 c6 3.e3 Bf5 4.Nc3 (4.cxd5 …) e6:

```
88 77 66 55 44 33 22 11    magic
c0 02 00 00                A = 704
62 00 00 00                B = 98
a1 74 64 d5 eb aa 48 26    checksum
01 00                      one section
fc ff                      moves
e1 ac   cf af              d2-d4, d7-d5
83 ac   72 af              c2-c4, c7-c6
40 ad   6f 8e              e2-e3, Bc8-f5
a1 2c   fd ff              Nb1-c3, which has an alternative (4.cxd5)
2e b0   5f 32   5f 83 …    e7-e6, Ng1-f3, Nb8-d7, …
```

The alternative `c4xd5` is the very last line in the stream, since it is the
first branch point in the game. The record is 836 bytes and ends with the long
`44 03 00 00 00 00 00 00`.

### The checksum at 0x10

The 8 bytes at 0x10 are a 64-bit checksum of the `A` bytes of content after the
first 2 bytes (after the variant word of a game), and **the one big-endian field
in these files**. With `m = A / 8`, rounded down:

1. Only the first `8m` bytes count. The last `A mod 8` bytes are left out.
2. They are split into 8 runs of `m` bytes, and each run is summed modulo 256.
3. Byte `i` of the checksum, counting from the least significant, is the sum of
   run `i`, bytes `i·m` to `i·m + m − 1`.

If `A` is less than 8, the checksum is the content itself read as a
little-endian number, as if padded with zeros to 8 bytes; that is the same rule
with runs of one byte. Either way the value is written big-endian, so its bytes
appear in the file in the reverse order of the runs.

This holds for every record in every sample, 2,154 in all: every game and
guiding text in `.2cbg`, and every game in `.2cba`. The `probe` games 17-36,
short games made by adding one move at a time, show each step: (wch2, probe)

| Game | Content after the variant | m | Stored |
|------|------|---|------|
| 1.e4 e5 2.Nf3 Nc6 | `fffc ad3f b02d 325f 836b ffff` (12 bytes) | 1 | `32 5f b0 2d ad 3f ff fc`, the first 8 bytes |
| … 3.Bc4 | 14 bytes | 1 | the same: the new move is in the left-out bytes |
| … 3…Bc5 | `fffc ad3f … 3c61 93eb ffff` (16 bytes) | 2 | `fe 7e 9d ee 91 dd ec fb`: `fb` = `fc + ff`, `ec` = `3f + ad`, … |
| … 3…Be7 | as above, with `93df` | 2 | `fe 72 …`: only the byte for that word changes |

So the checksum changes in steps: adding a move only shows once it completes an
8-byte block, and once `m` is 3 or more a move's bytes share a sum with their
neighbours. It is far too weak to identify a game, but it is enough to catch a
record that was damaged or read at the wrong offset, which is presumably what
it's for. It is not in v1.

## Guiding texts

A guiding text's record has the same framing as a game's, and its content is
HTML, a complete document for each language: (wch2, probe)

| Size | Type | Description |
|------|------|-------------|
| 4 | | `00 10 05 00` in every text |
| 4 | int | the number of bytes that follow this field |
| 4 | int | the number of languages |
| … | | that many entries, each as below |

Each entry is:

| Size | Type | Description |
|------|------|-------------|
| 4 | int | the language, as a nation code: 42 ENG, 43 ESP, 49 FRA, 53 GER, 70 ITA, 103 NED, 117 POR, or 0 |
| 4 | int | the length of the HTML in bytes |
| n | | the HTML, in UTF-8 |

The languages are the seven ChessBase offers, in ascending order, with the same
codes as the [titles of a text](FORMAT.md#game-tag-and-text-title). The title is
not stored here: it is a separate entity. Every language gets an entry even when
it has no text. The `probe` text, written in English and German, has an entry
of length 0 for each of the other five languages, and a bold sentence is plain
HTML:

```
00 10 05 00
02 01 00 00                  258 bytes follow
07 00 00 00                  7 languages
2a 00 00 00  74 00 00 00     ENG, 116 bytes:
<!DOCTYPE HTML><html><head></head><body><p>This is a text in English. <strong>Bold style!</strong></p></body></html>
2b 00 00 00  00 00 00 00     ESP, empty
31 00 00 00  00 00 00 00     FRA, empty
35 00 00 00  52 00 00 00     GER, 82 bytes:
<!DOCTYPE HTML><html><head></head><body><p>Ein bisschen Deutsch!</p></body></html>
46 00 00 00  00 00 00 00     ITA, empty
67 00 00 00  00 00 00 00     NED, empty
75 00 00 00  00 00 00 00     POR, empty
```

This accounts for every byte of the `probe` text and of all 13 texts in `wch2`.

### What the conversion from v1 changed

v1 stores a text's content as HTML too (text format version 3 in the v1 notes),
with its eight languages numbered 0 to 7 rather than by nation. Comparing the
texts of `wch2` with `wch1`: (wch2)

- v1's language 0 is ENG and language 1 is GER: the HTML moved across under
  those codes. v1's languages 2 to 7 all hold the same empty document in the
  samples, so which becomes which cannot be told. Eleven of the thirteen
  converted texts have an eighth entry with language **0**, which ChessBase's own
  `probe` text does not have, so it is presumably v1's eighth language, which
  has no nation of its own.
- In those eleven, the languages without text keep v1's placeholder, an empty
  HTML document of 142 to 221 bytes (`<!DOCTYPE HTML PUBLIC … ChessBase 13
  internal …></BODY></HTML>`). The other two, like the text written in
  ChessBase itself, have entries of length 0 instead.
- The HTML was rewritten. Links to database searches changed from
  `CBLink(tag_search_mask,<mask>)` to `CBLink(tag_search_mask2,<mask in base64>)`,
  and the base64 decodes to exactly v1's mask. Image paths such as
  `World-ch.html/Mega_d.png` became `wch2.html\`, apparently losing the file
  name. Text 99's German version has the same 3240 lines in both formats, and
  559 of them differ, all in links like these.
- The title moved out of the text into a game tag entity, see FORMAT.md.

## `.2cba` — annotations

**The annotations are v1's, repackaged.** Every annotation of `wch2` decodes to
exactly the data `wch1` has: the same types with the same numbers, on the same
moves, in the same order. What changed is the framing around them and some field
widths, while a few types have their bytes reversed. And one thing changed
completely: which move a position number means. (wch2)

### File header and records

The file header is the same as the [`.2cbg` header](#file-header), apart from
`00 00` at 0x0a. Records have the same framing as `.2cbg` records: the magic,
`A`, `B`, an 8-byte checksum, `A + 2` bytes of content, the spare area and the record
length. They are also stored back to back in game order, and grow in the same
way. A game's `.2cbh` record has the offset of its `.2cba` record at 0x10.

**Every game has a record**, even with no annotations, where v1 stores offset 0.
A guiding text has none: its `.2cbh` record has no annotation offset.

The checksum at 0x10 is computed [as in `.2cbg`](#the-checksum-at-0x10), over
the content after its `00 20`. For a record with no annotations that is
`ff ff ff 7f`, fewer than 8 bytes, so the checksum is `00 00 00 00 7f ff ff ff`.
(wch2, probe)

### Content

| Size | Type | Description |
|------|------|-------------|
| 2 | | `00 20` |
| | | position blocks, each as below, in ascending order of position |
| 4 | int | `0x7fffffff`, the end |

A position block:

| Size | Type | Description |
|------|------|-------------|
| 4 | int | the position, see below |
| 4 | int | the number of annotations |
| … | | the annotations, each a short type and then its data |

A game with no annotations is just `00 20 ff ff ff 7f`.

v1 writes each annotation with its own position (3 bytes) and length (2 bytes).
v2 groups the annotations by position, and has no length at all, so every type
has to be understood to get past it.

### Positions

Position −1 is the game as a whole, before the first move. Otherwise a position
counts the moves of the tree, starting from 0, **in the order a PGN lists
them**: each alternative, with everything that follows it, comes right after the
move it is an alternative to, before the main line goes on. v1 numbers them the
other way round: the whole main line first, then the variations, deepest first.
That is also the order v2 stores the moves in, so the two formats have swapped
orders between their moves and their annotations. (wch2)

In `1.e4 c5 (1...c6 2.d4) 2.Nf3`, the positions are e4 0, c5 1, c6 2, d4 3,
Nf3 4. v1 would have Nf3 at 2, c6 at 3 and d4 at 4.

Annotations on the same move keep v1's order. (wch2)

### Annotation types

The same type numbers as v1, as a short. The table lists every type in `wch2`,
with how its data differs from v1's; see the v1 notes
(`morphy-cbh/docs/cbh-format/annotations.md`) for what the fields mean.
Everything is checked against all the annotations of each type in `wch2`:
(wch2)

| Type | Count | Name | Data in v2 |
|------|------:|------|------|
| `02` | 16,387 | text after move | short 0, short language, int length, text |
| `82` | 2,049 | text before move | as `02` |
| `03` | 23,249 | symbols | 3 bytes: move, evaluation, prefix; v1 leaves out trailing zeros |
| `04` | 522 | colored squares | int length, then v1's data |
| `05` | 1,025 | arrows | int length, then v1's data |
| `07` | 830 | time spent | v1's 4 bytes, reversed |
| `09` | 5 | training | see below |
| `13` | 12 | game quotation | see below |
| `14` | 8 | pawn structure | 1 byte, as v1 |
| `15` | 2 | piece path | int length, then v1's data |
| `18` | 162 | critical position | 1 byte, as v1 |
| `22` | 72 | medals | v1's 4 bytes, reversed |
| `23` | 102 | variation color | v1's 4 bytes, reversed |
| `24` | 1 | time control | `01`, v1's three series little-endian, int 0; 38 bytes |
| `25` | 102 | video stream time | v1's 4 bytes, reversed |
| `26` | 6 | evaluations | see below |

"Reversed" means v1's big-endian int is now little-endian, so these are the same
value.

#### Text

The text bytes are exactly v1's, in all 18,436 comments. That means they are
**not UTF-8**, unlike names in the `.2lid` file and guiding texts: `…` is the
single byte `85`, and German umlauts are single bytes, as in cp1252. (wch2)

The language is no longer a nation code but a small number: (wch2)

| v2 | v1 | Language | Comments in `wch2` |
|---:|---:|---|---:|
| 0 | 42 | English | 8,804 |
| 1 | 53 | German | 2,153 |
| 2 | 49 | French | 17 |
| 3 | 43 | Spanish | 591 |
| 7 | 0 | any language | 6,871 |

This is the order v1 uses for the languages of guiding texts, where Italian,
Dutch and Portuguese come next, so they are presumably 4, 5 and 6.

A text made in ChessBase for a setup or Chess960 game holds `[#]`, the diagram
marker, and for Chess960 the start position, e.g. `[#] Chess 960-Position 566`.
(probe)

#### Evaluations

Type `0x26` holds an engine evaluation for each move of the main line, at
position −1. v1 has this type too (the Java code does not know it), and in
`wch2` it is in the six games where `wch1` has it:

| Size | Type | Description |
|------|------|-------------|
| 1 | | `01` |
| 4 | int | the number of bytes that follow |
| 2 | short | the number of entries |
| 4 · n | | the entries |

Each entry is v1's entry reversed:

| Size | Type | Description |
|------|------|-------------|
| 2 | short | the evaluation in centipawns (or moves to mate, see the flag) |
| 1 | byte | the search depth |
| 1 | byte | 0 for an evaluation, `ff` for none (value and depth 0), 1 in a few entries, presumably mate |

In `wch2`, there are as many entries as positions in the main line, the start
included. ChessBase itself writes one per move (`reveng1`), and doesn't update
them when moves are added: `probe` game 2 has 90, which is what fits the 89 plies
it had after its first round of edits, not the 103 it has now. Of the games made
in ChessBase, both games of `reveng1` and game 2 of `probe` have evaluations, at
depth 1, while the other `probe` games with moves (1, 13-16) have none. What
makes ChessBase add them is not known. (wch2, probe)

#### Training

A training question, with the same content as v1 but slightly wider fields. v1
doesn't decode it either (the Java code keeps the raw bytes), so the fields below
are only what the samples show:

| Size | Description |
|------|-------------|
| 6 | `01 01 01 00 00 00`, where v1 has `01 00 01` and a short length |
| 4 | int, the time allowed in seconds (e.g. 300) |
| 2 | short, the points |
| … | the question texts, see below |
| 6 | `?` zero |
| 1 | the number of solutions |
| … | each solution: from and to square (v1 numbering, 1 byte each), 2 bytes `?`, then its texts |

A list of texts is a short count, then per text a short `?` (0), an int length
(a short in v1) and the text. Converting v1 by those rules gives exactly the v2
bytes for all five. (wch2)

#### Game quotation

A game quoted in a comment, with a header of its own and, optionally, its moves:

| Size | Description |
|------|-------------|
| 1 | `01` |
| 2 | short, v1's quotation type: 1 header only, 2 with the moves |
| 2 | short, v1's unknown value |
| 4 | int 1 |
| 1 | 0 |
| … | six strings: white's last name, white's first name, black's last name, black's first name, site, event; each a length byte (counting the terminating 0), the text and a 0 |
| 35 | date (int), event type, nation, category, rounds, …, white and black Elo, ECO, result |
| 44 | `?` mostly zero |
| … | two rating types, each `01 00 01 00 00` and an int-length name (`FIDE`) |
| 29 | `?` |
| 4 | int, the number of moves |
| 5 · n | the moves: from and to square (v1 numbering, 0-based), 3 bytes `?` (0) |
| 4 | int 0 |

v1 writes the names as `last,first` and the event before the site. It stores
the moves in its own compact move encoding, where v2 just lists the squares.
This layout accounts for all 12 quotations in `wch2`, but most of the fixed
blocks are not decoded. (wch2)

## Open questions

- How a Chess960 game from a setup position is stored, if ChessBase allows one.
- What happens when the games after an edited game do not have enough spare
  between them (see [The spare area](#the-spare-area)).
- The two bytes at 0x0a of the file header.
- What the `00 10 05 00` at the start of a guiding text means (a version?).
- Where the images a text refers to are kept, and whether the rewritten image
  paths in `wch2` are broken or point to something.
- Whether word 0 and the words from `c30d` to `fff9` ever mean anything. Nothing
  in the samples uses them.

`.2cba`:

- The fields of a game quotation beyond the names, date, Elos and ECO, and the
  3 bytes after the squares of each of its moves (promotion, presumably).
- What the time control's leading `01` and trailing int 0 are.
- Which language index ITA, NED and POR get (4, 5 and 6 are guessed from v1's
  order for guiding texts), and whether a comment with non-ASCII text written in
  ChessBase itself is stored as cp1252 too.
- The meaning of the flag byte of an evaluation beyond `ff` (none), and why its
  entry count is one more than the main line's plies in some games and equal to
  it in others.
- The annotation types v1 knows that the samples don't have: sound, picture,
  video, correspondence move and header, web link, and types `0x08` and `0x1a`.

