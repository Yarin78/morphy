# ChessBase Annotation Text Encoding Specification

This document specifies the format for encoding ChessBase annotations as text in PGN comments, enabling lossless round-trip conversion between CBH (ChessBase) and PGN formats.

## Table of Contents

1. [Overview](#overview)
2. [Encoding Principles](#encoding-principles)
3. [Character Escaping](#character-escaping)
4. [Before/After Move Disambiguation](#beforeafter-move-disambiguation)
5. [Annotation Specifications](#annotation-specifications)
   - [Already Implemented](#already-implemented)
   - [To Be Implemented](#to-be-implemented)
6. [Deprecated/Unsupported Annotations](#deprecatedunsupported-annotations)
7. [Complete Example](#complete-example)

---

## Overview

### The Problem

ChessBase databases support rich annotations beyond what standard PGN can represent. When converting games between CBH and PGN formats, we need a way to encode these annotations in PGN comments without losing information.

### The Solution

All complex ChessBase annotations are encoded using **square bracket notation** within PGN comments:

```
[%command arg1 arg2 ...]
```

This format is:
- Compatible with PGN parsers (treated as comment text)
- Used by other tools (ChessBase, SCID, Lichess) for graphical annotations
- Extensible for new annotation types
- Machine-parseable for round-trip conversion

---

## Encoding Principles

### 1. Square Bracket Notation

All annotations use the format:
```
[%command parameters]
```

Where:
- `%` prefix indicates a special command (standard PGN convention)
- `command` is a short identifier for the annotation type
- `parameters` are space or comma-separated values

### 2. Multiple Annotations

Multiple annotations in the same comment are space-separated:
```
{ [%csl Ga4,Rb5] [%cal Ge2e4] Some comment text }
```

### 3. Order Convention

Annotations should appear in this order within a comment:
1. Graphical annotations (`[%csl ...]`, `[%cal ...]`)
2. Clock/time annotations (`[%clk ...]`, `[%emt ...]`)
3. Computer evaluation (`[%eval ...]`)
4. Other metadata annotations
5. Plain text commentary

### 4. Data Types

| Type | Format | Example |
|------|--------|---------|
| Square | lowercase file + rank | `e4`, `a1`, `h8` |
| Color (graphical) | Single letter: G=Green, R=Red, Y=Yellow | `G`, `R`, `Y` |
| Color (RGB) | Hex format `#RRGGBB` | `#FF0000` |
| Time (duration) | `H:MM:SS` or `M:SS` or `S` | `1:30:00`, `5:30`, `45` |
| Time (centiseconds) | Integer | `9000` (= 1:30) |
| Integer | Decimal | `42`, `-150` |
| String | Quoted if contains spaces | `"text with spaces"` |
| Boolean | `true`/`false` or `1`/`0` | `true`, `1` |

---

## Character Escaping

### Within Square Bracket Notation

Characters that need escaping inside `[%...]` blocks:

| Character | Escape Sequence | Notes |
|-----------|-----------------|-------|
| `]` | `\]` | Closes the bracket block |
| `\` | `\\` | Escape character itself |
| `"` | `\"` | Inside quoted strings |

### Example
```
[%weblink "https://example.com/path?a=1" "Click \"here\" for more"]
```

### Plain Text in Comments

Standard PGN escaping applies to the comment body:
- `{` and `}` delimit comments
- Nested braces are not allowed in standard PGN
- Use UTF-8 encoding for international characters

---

## Before/After Move Disambiguation

### The Problem

In PGN, comments can appear before or after a move, but parsing can be ambiguous:
```
1.e4 { comment } e5
```
Does the comment belong after 1.e4 or before 1...e5?

### Solution: Position Markers

The default assumption is that comments belong **after** the preceding move. Use position markers when needed:

| Position | Default Language | Specific Language |
|----------|------------------|-------------------|
| After-move | Plain text | `[%post:LANG text]` |
| Before-move | `[%pre text]` | `[%pre:LANG text]` |

Where `LANG` is a 3-letter IOC language code (e.g., `ENG`, `GER`, `FRA`).

### Design Rationale

This approach separates two concerns:
1. **Position** (before/after): `%pre` and `%post` indicate position
2. **Language**: Optional `:LANG` suffix specifies language

Most comments are after-move with default language, so they require no special encoding—keeping the common case simple.

### When `%pre` Is Not Needed

The `%pre` marker is **not necessary** for comments that appear before the first move in a variation, since the left parenthesis already makes the position unambiguous:

```
1.e4 e5 2.Nf3 ({ This is clearly before 2...Nc6 } 2...Nc6)
```

The `{` immediately follows `(`, so the comment must be a before-move comment. Use `%pre` only when the position would otherwise be ambiguous.

### Examples

**After-move comment (default language):**
```
1.e4 { This comment is after e4 }
```

**After-move comment (with language):**
```
1.e4 { [%post:GER Dies ist ein deutscher Kommentar] }
```

**Before-move comment (default language):**
```
1.e4 { [%pre This comment is before Black's move] } e5
```

**Before-move comment (with language):**
```
1.e4 { [%pre:FRA Ce commentaire est avant le coup des Noirs] } e5
```

**Before-move in variation (no marker needed):**
```
1.e4 e5 2.Nf3 ({ A common alternative is the Sicilian } 2...c5)
```

---

## Annotation Specifications

### Already Implemented

#### SymbolAnnotation

**Conversion:** Direct mapping to PGN NAGs (Numeric Annotation Glyphs)

| Storage | PGN NAG | Symbol |
|---------|---------|--------|
| Move comment: Good move | $1 | ! |
| Move comment: Poor move | $2 | ? |
| Move comment: Very good | $3 | !! |
| Move comment: Very poor | $4 | ?? |
| Move comment: Speculative | $5 | !? |
| Move comment: Questionable | $6 | ?! |
| Line eval: White winning | $18 | +- |
| Line eval: White better | $16 | +/- |
| Line eval: White slightly better | $14 | += |
| Line eval: Equal | $11 | = |
| Line eval: Unclear | $13 | ∞ |
| Line eval: Black slightly better | $15 | =+ |
| Line eval: Black better | $17 | -/+ |
| Line eval: Black winning | $19 | -+ |
| Prefix: With idea | $140 | |
| Prefix: Better is | $142 | |

**Note:** SymbolAnnotation stores up to 3 NAGs (move comment, line evaluation, move prefix). In PGN, these become separate NAG annotations.

#### GraphicalSquaresAnnotation

**Command:** `%csl` (Colored Square List)

**Format:**
```
[%csl ColorSquare,ColorSquare,...]
```

**Parameters:**
- `Color`: `G` (Green), `R` (Red), `Y` (Yellow)
- `Square`: Chess square notation (`a1`-`h8`)

**Example:**
```
[%csl Ge4,Re5,Yd4]
```
Highlights e4 in green, e5 in red, d4 in yellow.

#### GraphicalArrowsAnnotation

**Command:** `%cal` (Colored Arrow List)

**Format:**
```
[%cal ColorFromTo,ColorFromTo,...]
```

**Parameters:**
- `Color`: `G` (Green), `R` (Red), `Y` (Yellow)
- `From`: Source square
- `To`: Target square

**Example:**
```
[%cal Ge2e4,Rd7d5,Yb1c3]
```
Green arrow e2→e4, red arrow d7→d5, yellow arrow b1→c3.

#### TextAfterMoveAnnotation / TextBeforeMoveAnnotation

**Commands:** `%pre` (before move), `%post` (after move with language)

**Format:**
```
Plain text                    # After-move, default language
[%post:LANG text]             # After-move, specified language
[%pre text]                   # Before-move, default language
[%pre:LANG text]              # Before-move, specified language
```

**Parameters:**
- `LANG`: 3-letter IOC code
- `text`: The comment text (until closing `]`)

**Language Codes:**

| Code | Language |
|------|----------|
| `ENG` | English |
| `GER` | German |
| `FRA` | French |
| `ESP` | Spanish |
| `ITA` | Italian |
| `NED` | Dutch |
| `POR` | Portuguese |

**Encoding Rules:**
- After-move with default language: use plain text (no encoding needed)
- After-move with specific language: use `[%post:LANG text]`
- Before-move with default language: use `[%pre text]`
- Before-move with specific language: use `[%pre:LANG text]`
- Before first move in a variation: plain text (position is unambiguous)

**Examples:**
```
{ This is after the move }
{ [%post:GER Dies ist Deutsch] }
{ [%pre This is before the next move] }
{ [%pre:FRA Ceci est en français avant le coup] }
({ No marker needed before first move in variation } 2...Nc6)
```

---

### To Be Implemented

#### WhiteClockAnnotation / BlackClockAnnotation

**Commands:** `%clk` (preferred), `%clkw` (white), `%clkb` (black)

**Format:**
```
[%clk H:MM:SS]         # Clock of the player who just moved (preferred)
[%clkw H:MM:SS]        # White's clock (explicit)
[%clkb H:MM:SS]        # Black's clock (explicit)
```

**Storage Data:**
- `clockTime`: Time in centiseconds (hundredths of a second)

**Conversion:**
- centiseconds → `H:MM:SS` format
- Leading zeros optional for hours: `1:30:00` or `01:30:00`

**Encoding Rules:**

The standard `%clk` command is **preferred** when the clock annotation belongs to the player who just made the move. This is the common case and is compatible with Lichess and other tools.

Use `%clkw` or `%clkb` only when you need to record a clock value for the player who did **not** just move (e.g., if the database stores both players' clock times after each move).

**Examples:**
```
1.e4 { [%clk 1:29:45] }       # White's clock after 1.e4 (preferred format)
1...e5 { [%clk 1:28:30] }     # Black's clock after 1...e5 (preferred format)

# Explicit format (use when recording opponent's clock):
1.e4 { [%clk 1:29:45] [%clkb 1:30:00] }  # Both clocks after White's move
```

**Decoding Rules:**
- `[%clk ...]` after a White move → WhiteClockAnnotation
- `[%clk ...]` after a Black move → BlackClockAnnotation
- `[%clkw ...]` → WhiteClockAnnotation (regardless of position)
- `[%clkb ...]` → BlackClockAnnotation (regardless of position)

---

#### ComputerEvaluationAnnotation

**Command:** `%eval`

**Format:**
```
[%eval score/depth]
[%eval #N/depth]
```

**Storage Data:**
- `eval`: Centipawns (or moves to mate if evalType=1)
- `evalType`: 0=centipawns, 1=mate distance, 3=unknown (skip)
- `ply`: Search depth in half-moves

**Conversion:**
- Type 0: `eval/100` with sign, e.g., `+1.50`, `-0.75`
- Type 1: `#N` where N is moves to mate, e.g., `#5`, `#-3`
- Type 3: Do not encode (ChessBase doesn't display these)

**Examples:**
```
{ [%eval +1.50/20] }     # +1.50 pawns at depth 20
{ [%eval -0.25/18] }     # -0.25 pawns at depth 18
{ [%eval #5/30] }        # White mates in 5 at depth 30
{ [%eval #-3/25] }       # Black mates in 3 at depth 25
```

---

#### TimeSpentAnnotation

**Command:** `%emt` (Elapsed Move Time)

**Format:**
```
[%emt H:MM:SS]
```

**Storage Data:**
- `hours`, `minutes`, `seconds`: Time components
- `unknownByte`: Internal flag (preserve in round-trip as optional parameter)

**Conversion:**
- Combine components to `H:MM:SS`
- Omit leading zero components: `5:30` instead of `0:05:30`
- If unknownByte != 0, append as `|flag`: `[%emt 5:30|30]`

**Examples:**
```
{ [%emt 0:05:30] }       # 5 minutes 30 seconds on this move
{ [%emt 1:15:00] }       # 1 hour 15 minutes
{ [%emt 0:00:45|30] }    # 45 seconds, with internal flag
```

---

#### TimeControlAnnotation

**Command:** `%tc` (Time Control)

**Format:**
```
[%tc period1+period2+period3]
```

Where each period is:
```
time/moves
(time+inc)/moves
```

**Storage Data:**
- List of `TimeSerie` with: `start` (centiseconds), `increment` (centiseconds), `moves`, `type`

**Conversion:**
- `start/100` → seconds → format as minutes/seconds
- `increment/100` → increment in seconds
- `moves`: number of moves (1000 = rest of game, omit `/moves` suffix)
- `type`: Internal flag (preserve if != 0)

**Examples:**
```
{ [%tc 90m/40+30m+30s/1000] }     # 90 min for 40 moves, then 30 min, then 30 sec/move
{ [%tc (15m+10s)/1000] }          # 15 minutes + 10 seconds increment
{ [%tc 3m/1000] }                 # 3 minute game
```

---

#### CriticalPositionAnnotation

**Command:** `%crit`

**Format:**
```
[%crit type]
```

**Storage Data:**
- `type`: NONE(0), OPENING(1), MIDDLEGAME(2), ENDGAME(3)

**Values:**

| Type | Encoding |
|------|----------|
| OPENING | `opening` |
| MIDDLEGAME | `middlegame` |
| ENDGAME | `endgame` |

**Examples:**
```
{ [%crit opening] }      # Critical opening position
{ [%crit middlegame] }   # Critical middlegame position
{ [%crit endgame] }      # Critical endgame position
```

---

#### MedalAnnotation

**Command:** `%medal`

**Format:**
```
[%medal medal1,medal2,...]
```

**Storage Data:**
- `medals`: EnumSet of Medal values

**Medal Values:**

| Medal | Encoding |
|-------|----------|
| BEST_GAME | `best` |
| DECIDED_TOURNAMENT | `decided` |
| MODEL_GAME | `model` |
| NOVELTY | `novelty` |
| PAWN_STRUCTURE | `pawn` |
| STRATEGY | `strategy` |
| TACTICS | `tactics` |
| WITH_ATTACK | `attack` |
| SACRIFICE | `sacrifice` |
| DEFENSE | `defense` |
| MATERIAL | `material` |
| PIECE_PLAY | `piece` |
| ENDGAME | `endgame` |
| TACTICAL_BLUNDER | `tactblunder` |
| STRATEGICAL_BLUNDER | `stratblunder` |
| USER | `user` |

**Example:**
```
{ [%medal tactics,sacrifice,best] }
```

---

#### VariationColorAnnotation

**Command:** `%varcolor`

**Format:**
```
[%varcolor #RRGGBB flags]
```

**Storage Data:**
- `red`, `green`, `blue`: Color components (0-255)
- `onlyMoves`: Boolean
- `onlyMainline`: Boolean

**Flags:**
- `M` = onlyMoves (color only moves, not annotations)
- `L` = onlyMainline (mainline only, not subvariations)

**Examples:**
```
{ [%varcolor #FF0000] }         # Red, all content, include sublines
{ [%varcolor #00FF00 M] }       # Green, moves only
{ [%varcolor #0000FF L] }       # Blue, mainline only
{ [%varcolor #FFFF00 ML] }      # Yellow, moves only, mainline only
```

---

#### PiecePathAnnotation

**Command:** `%path`

**Format:**
```
[%path square type]
```

**Storage Data:**
- `sqi`: Square index (0-63)
- `type`: Internal type value (usually 3)

**Example:**
```
{ [%path e4 3] }     # Piece path starting from e4
```

---

#### PawnStructureAnnotation

**Command:** `%pawnstruct`

**Format:**
```
[%pawnstruct type]
```

**Storage Data:**
- `type`: Internal type value (usually 3)

**Example:**
```
{ [%pawnstruct 3] }
```

---

#### WebLinkAnnotation

**Command:** `%weblink`

**Format:**
```
[%weblink "url" "displaytext"]
```

**Storage Data:**
- `url`: The URL string
- `text`: Display text for the link

**Escaping:**
- Quotes within strings: `\"`
- Backslashes: `\\`

**Example:**
```
{ [%weblink "https://lichess.org/study/abc123" "See analysis"] }
```

---

#### VideoStreamTimeAnnotation

**Command:** `%vst`

**Format:**
```
[%vst time]
```

**Storage Data:**
- `time`: Integer timestamp

**Example:**
```
{ [%vst 12345] }     # Video stream position
```

---

#### GameQuotationAnnotation

**Command:** `%quote`

**Format:**
```
[%quote "White" "Black" "Event" "Site" YYYY.MM.DD result elo_w elo_b eco "moves"]
```

**Storage Data:**
- `header`: GameHeaderModel with player names, event, site, date, result, elos, ECO
- `gameData`: Optional encoded moves

**Fields:**
- Player names in quotes
- Event and site in quotes
- Date in PGN format: `YYYY.MM.DD`
- Result: `1-0`, `0-1`, `1/2-1/2`, `*`
- Elo ratings as integers (0 if unknown)
- ECO code (e.g., `B90`)
- Moves in SAN notation, space-separated, in quotes

**Example:**
```
{ [%quote "Carlsen, Magnus" "Nepomniachtchi, Ian" "World Championship" "Dubai" 2021.12.03 1-0 2856 2782 C88 "1.e4 e5 2.Nf3 Nc6 3.Bb5 a6"] }
```

**Note:** For games without moves, omit the moves parameter:
```
{ [%quote "Kasparov, Garry" "Karpov, Anatoly" "World Ch" "Moscow" 1985.09.03 1-0 2700 2720 E12] }
```

---

#### TrainingAnnotation

**Command:** `%train`

**Format:**
```
[%train base64data]
```

**Storage Data:**
- `rawData`: Binary data (format not fully understood)

**Conversion:**
- Encode raw bytes as Base64

**Example:**
```
{ [%train SGVsbG8gV29ybGQ=] }
```

---

#### CorrespondenceMoveAnnotation

**Command:** `%corr`

**Format:**
```
[%corr base64data]
```

**Storage Data:**
- `rawData`: Binary data (format not fully understood)

**Conversion:**
- Encode raw bytes as Base64

**Example:**
```
{ [%corr AQIDBAU=] }
```

---

## Deprecated/Unsupported Annotations

These annotation types are deprecated in ChessBase and should be preserved for round-trip but may not display correctly:

### SoundAnnotation (0x10)

**Command:** `%sound`

```
[%sound base64data]
```

### VideoAnnotation (0x20)

**Command:** `%video`

```
[%video base64data]
```

### PictureAnnotation (0x11)

**Command:** `%picture`

```
[%picture base64data]
```

---

## Complete Example

```pgn
[Event "World Championship"]
[Site "Dubai"]
[Date "2021.12.03"]
[Round "6"]
[White "Carlsen, Magnus"]
[Black "Nepomniachtchi, Ian"]
[Result "1-0"]
[WhiteElo "2856"]
[BlackElo "2782"]
[ECO "C88"]

1.e4 { [%clk 1:59:52] [%eval +0.25/22] } e5 { [%clk 1:58:30] }
2.Nf3 Nc6 3.Bb5 a6 { [%crit opening] The Ruy Lopez. }
4.Ba4 Nf6 5.O-O Be7 { [%csl Ge8,Yg8] [%cal Ge8g8] Preparing to castle. }
6.Re1 b5 7.Bb3 O-O { [%medal model,strategy] A model game in the Closed Ruy Lopez. }
8.h3 { [%emt 0:02:15] } Na5 { [%pre An important decision.] }
9.Nxe5 { [%varcolor #FF6600 M] } Nxb3 { [%eval +1.50/25] Winning the bishop pair. }
10.axb3 { [%weblink "https://lichess.org/analysis" "Analyze on Lichess"] } 1-0
```

---

## Implementation Notes

### Parsing Priority

When parsing annotations from text, process in this order:
1. `[%pre ...]` - Extract before-move text annotations first
2. `[%csl ...]`, `[%cal ...]` - Graphical annotations
3. `[%clk ...]`, `[%clkw ...]`, `[%clkb ...]`, `[%emt ...]` - Time annotations
4. `[%eval ...]` - Computer evaluation
5. `[%post ...]` - Language-tagged after-move text
6. All other `[%...]` commands
7. Remaining text - Plain after-move commentary

### Error Handling

- Malformed annotations should be logged and preserved as plain text
- Unknown commands should be passed through unchanged
- Invalid parameters should use sensible defaults

### Round-Trip Fidelity

The encoding must ensure:
1. No data loss when converting Storage → PGN → Storage
2. Unknown annotation types preserved via raw data encoding
3. Internal flags/unknown bytes preserved where documented

---

## See Also

- [GAME-REPRESENTATION.md](GAME-REPRESENTATION.md) - Overall architecture
- [cbh-format/annotations.md](cbh-format/annotations.md) - Binary format specification
- `AnnotationConverter.java` - Implementation reference
