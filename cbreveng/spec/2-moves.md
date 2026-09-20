# `.2cbg` — moves

Holds the moves of every game and analysis, and the body of every guiding text.
A 12-byte file header is followed by one record per game, in game id order and
back to back, each starting where the previous one ended. The record for a game
is found through the offset at 0x08 of its `.2cbh` record.

## File header

| Offset | Size | Type | Description |
|---|---|---|---|
| 0x00 | 8 | long | size of the file in bytes |
| 0x08 | 2 | short | header size, 12 |
| 0x0a | 1 | | unknown; 1 in a database with no games, 0 otherwise |
| 0x0b | 1 | byte | format version, 5; 0 in a database with no games |

## Records

`.2cba` records use the same framing.

| Offset | Size | Type | Description |
|---|---|---|---|
| 0x00 | 8 | | magic `88 77 66 55 44 33 22 11` |
| 0x08 | 4 | int | *A*, the size of the content minus 2 |
| 0x0c | 4 | int | *B*, the size of the spare area |
| 0x10 | 8 | | [checksum](#checksum) of the content, **big-endian** |
| 0x18 | *A* + 2 | | the content |
| 0x1a + *A* | *B* | | spare, all zero |
| 0x1a + *A* + *B* | 8 | long | the record length, `A + B + 34` |

The content always begins with a 2-byte tag, which *A* excludes: `01 00` or
`02 00` for a game (the variant), `00 10` for a guiding text, `00 20` for
annotations.

The spare area is free space inside the record, allowing the game to grow without
moving. Its size varies and it is always zero. How ChessBase chooses it, and what
happens when a game outgrows its record, is described in
[6-behaviour.md](6-behaviour.md#free-space-in-the-move-and-annotation-files).

### Checksum

A 64-bit value over the *A* bytes of content that follow the 2-byte tag, stored
**big-endian** — the only big-endian field in this file. With *m* = ⌊*A* / 8⌋:

1. Only the first 8*m* bytes take part; the last *A* mod 8 bytes are ignored.
2. They are divided into 8 consecutive runs of *m* bytes.
3. Byte *i* of the value, counting from the least significant, is the sum
   modulo 256 of run *i*, that is of bytes *i·m* to *i·m* + *m* − 1.

If *A* < 8 then *m* = 0 and the value is the content itself read as a
little-endian integer, zero-padded to 8 bytes — the same rule with runs of one
byte.

The checksum detects a damaged record or one read at the wrong offset. It is far
too weak to identify a game: adding a move changes nothing until it completes an
8-byte run.

## The word stream

A game's content is a sequence of 16-bit little-endian words. Words from `fffa`
upward are markers; everything below is a [move](#move-words) or a
[piece placement](#set-up-positions), numbered in one continuous scheme up to
`c30c`. No other values occur.

| Word | Meaning |
|---|---|
| `fffa` | null move |
| `fffb` | begins the start position section |
| `fffc` | begins the move section |
| `fffd` | the move just given has a further alternative, stored later |
| `ffff` | end of the current line |

`fffe` has not been seen.

The stream starts with the **variant**: 1 for normal chess, 2 for Chess960. Then
come sections, each introduced by its marker:

- **`fffb`, the start position**, present only when the game does not begin from
  the standard position. It holds either
  - a single word, the Chess960 start position number 0-959; or
  - a [set-up position](#set-up-positions); or
  - the word 1000 followed by a set-up position, for a Chess960 game that also
    starts from a set-up position.
- **`fffc`, the moves**, which run to the end of the stream.

A game from the standard position therefore starts `0001 fffc`, and a game with
no moves at all is `0001 fffc ffff`.

### The move tree

The main line is written first, in full, to its end. Alternatives follow
afterwards, **most recent branch point first**.

- A move word makes that move and becomes the current position.
- `fffd` immediately after a move means that move has at least one further
  sibling, stored later. The position the move was made from is pushed on a
  stack.
- `ffff` ends the current line. If the stack is empty the stream is finished.
  Otherwise the top position is popped and the next move continues as the next
  alternative from that position.

There is exactly one `fffd` for every `ffff` but the last.

For example

```
1.e4 c5 (1...c6 2.d4) (1...Nf6 2.e5) 2.Nf3 d6 (2...Nc6 3.Bb5) 3.d4
```

is stored as

```
e4 c5 fffd Nf3 d6 fffd d4 ffff Nc6 Bb5 ffff c6 fffd d4 ffff Nf6 e5 ffff
```

`c5` takes a `fffd` because `c6` and `Nf6` follow later, and `d6` because `Nc6`
does. After the main line ends at `3.d4`, the most recent branch point is
resumed first, giving `Nc6 Bb5`; then `c6`, which takes a `fffd` of its own
because `Nf6` is still to come; and finally `Nf6 e5`.

A null move is written as `fffa` and is treated like any other move.

## Move words

A move word encodes the moving piece, its origin and destination, the piece
captured and the piece promoted to. The words are every such move enumerated in a
fixed order, so a word has the same meaning in any position and a game can be
decoded without a board. Word 0 is unused; moves run from 1 to `c02c`, 49,196 in
all.

| Words | Moves |
|---|---|
| `0001`-`09d8` | white king |
| `09d9`-`2bf8` | white queen |
| `2bf9`-`33d8` | white knight |
| `33d9`-`40f8` | white bishop |
| `40f9`-`55f8` | white rook |
| `55f9`-`5fd0` | black king |
| `5fd1`-`81f0` | black queen |
| `81f1`-`89d0` | black knight |
| `89d1`-`96f0` | black bishop |
| `96f1`-`abf0` | black rook |
| `abf1`-`ae8c` | white pawn |
| `ae8d`-`b128` | black pawn |
| `b129`-`b12c` | castling |
| `b12d`-`c02c` | Chess960 castling |

### Pieces other than pawns

Within a piece's block the moves are listed by origin square in numerical order,
`a1`, `a2`, … `h8`. From each origin, the destinations reachable on an empty
board are listed by direction, in the order below, following each direction
outward one square at a time:

| Piece | Directions, as (file, rank) steps |
|---|---|
| king | (−1,−1) (−1,0) (−1,+1) (0,−1) (0,+1) (+1,−1) (+1,0) (+1,+1) |
| knight | (−2,−1) (−2,+1) (+2,−1) (+2,+1) (−1,−2) (−1,+2) (+1,−2) (+1,+2) |
| bishop | (−1,−1) (+1,−1) (+1,+1) (−1,+1) |
| rook | (−1,0) (0,−1) (+1,0) (0,+1) |
| queen | the four bishop directions, then the four rook directions |

Each destination takes **six consecutive words**: the move without a capture,
then capturing a queen, a knight, a bishop, a rook and a pawn, in that order.

A block is therefore six times the number of moves that piece has on an empty
board: 420 for the king, 1456 queen, 336 knight, 560 bishop, 896 rook. The
enumeration takes no account of legality, so words exist for capturing a pawn on
the first or last rank.

For example the white king on `a1` reaches `a2`, `b1` and `b2`, so `a1-a2` is
word 1 and `a1-b1` is word 7; the white king block ends after 6 · 420 words, so
the white queen block begins at `09d9`.

### Pawns

Pawn moves are listed by file `a` to `h`, and within a file by square from rank 2
to rank 7, for both colours — so the black pawn block also begins with the
promotions from `a2`. For each square, in order:

1. **Forward moves.** From the starting rank: the double step, then the single
   step. From the rank before promotion: the four promotions, to queen, knight,
   bishop and rook. Otherwise: the single step alone.
2. **Captures**, first toward the lower file and then toward the higher (only one
   of the two on the `a` and `h` files). Each capture is 5 words, one per piece
   captured (queen, knight, bishop, rook, pawn); 6 on the rank where en passant
   is possible, the sixth being the en passant capture; and 16 on the rank before
   promotion, being the four capturable pieces (queen, knight, bishop, rook)
   crossed with the four promotions.

This gives 668 words per colour. En passant is on rank 5 for white and rank 4 for
black.

### Castling

Castling is neither a king move nor a rook move but has four words of its own:

| Word | Move |
|---|---|
| `b129` | white O-O-O |
| `b12a` | white O-O |
| `b12b` | black O-O-O |
| `b12c` | black O-O |

A Chess960 game uses a separate set of four for each start position, the four for
position *n* beginning at `b12d + 4n` in the same order. Since position 518 is
stored as an ordinary game, its four words never occur.

### Set-up positions

A start position section that is not a Chess960 number holds three words followed
by one word per piece on the board:

| Word | Description |
|---|---|
| 0 | the move number |
| 1 | low byte: side to move, 0 white, 1 black. High byte: en passant file, 1-8 for `a`-`h`, 0 if none |
| 2 | castling rights: 1 white O-O-O, 2 white O-O, 4 black O-O-O, 8 black O-O |
| 3… | the pieces, in square order |

A piece word is `c02d` plus an index over pieces and squares, continuing the
numbering directly above the move words:

| Words | Pieces |
|---|---|
| `c02d`-`c16c` | white K, Q, N, B, R: `c02d` + 64 · piece + square |
| `c16d`-`c2ac` | black k, q, n, b, r, likewise |
| `c2ad`-`c2dc` | white pawns: `c2ad` + 6 · file + rank − 2 |
| `c2dd`-`c30c` | black pawns, likewise |

Pawns take 48 words each since they occupy only ranks 2 to 7.

## Guiding texts

A guiding text's record has the same framing, and content tagged `00 10`:

| Size | Type | Description |
|---|---|---|
| 4 | | `00 10 05 00`; the 5 is probably the format version |
| 4 | int | number of bytes that follow this field |
| 4 | int | number of languages |
| … | | that many entries, below |

Each entry:

| Size | Type | Description |
|---|---|---|
| 4 | int | language, as a nation code |
| 4 | int | length of the HTML in bytes |
| *n* | | the HTML, in UTF-8 |

The seven languages ChessBase offers each get an entry, in ascending order of
code, even when the text is absent — such an entry has length 0. A language code
of 0 also occurs. The body is a complete HTML document. The title is not stored
here; it is a game tag entity.
