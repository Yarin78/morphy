# Game Headers

**Game Headers**, metadata about a game, are stored across multiple database files. The main game header is stored in the .cbh file, 
and the extended header in the .cbj file. Additional game information is stored in the .flags file.

Besides games, metadata about *guiding texts* are also stored in the same files. As the attentive user of ChessBase may have noticed,
these guiding texts are mixed with the games, sharing the same id-space. The first byte of each record contains information
if the record is a game or a text.

Games are referenced by their id; the first game/text has id 1.

## CBH file format

The file starts with a <a href="#cbh_header">header</a> containing metadata about the file itself. Then follows one record per <a href="#cbh_game">game</a>/<a href="#cbh_text">text</a>. Both header and records are 46 bytes each.

There are values in the header that seems to indicate that the size of the header (and the records) might be configurable; however, that never happens in the wild.
It's very convenient that both the header and each record is the same size. This is not the case in the other database files.

(It may be that the header of the file is just another type of record outside of game and text; the first byte in the header record would support that hypothesis.) 

All integers are stored in Big Endian (most significant byte first) unless otherwise specified.

### <a name="cbh_header">CBH file header</a>

| Offset | Bytes | Description
| --- | ---- | ----
| 0   | 1    | Unknown. Always `0`?
| 1   | 2    | Unknown. `36` in really old databases (created by CB6), `44` in CB9 or later. Might be number of bytes of this header that is actually used. 
| 3   | 2    | Unknown. Always `46`. Presumably the size of both the header and the records.
| 5   | 1    | Unknown. Always `1`?
| 6   | 4    | The id of the next game to be added.
| 10  | 2    | Unknown. Always `0`?
| 12  | 4    | The id of the next embedded sound clip (deprecated feature)
| 16  | 4    | The id of the next embedded picture (deprecated feature)
| 20  | 4    | The id of the next embedded video clip (deprecated feature)
| 24  | 16   | Unknown. Always `0`?
| 40  | 4    | The id of the next game to be added (same as offset 6) _or_ 0 in some old bases (but doesn't align with the value at offset 1 as one might expect).  
| 44  | 2    | Unknown. Always `0`?

### <a name="cbh_game">CBH game record</a>

The id-references to player, tournament, annotator and source are all 0-based; see [Entities](entities.md).
For games with no annotator, the game will refer to an annotator with an empty name.

| Offset | Bytes | Description
| --- | ---- | ---- |
| 0   | 1    | Bit 0: always set (?); bit 1: guiding text, bit 7: the game has been marked as deleted
| 1   | 4    | Offset into the .cbg file where the moves data starts
| 5   | 4    | Offset into the .cba file where annotations data start (_or_ `0` if the game has no annotations)
| 9   | 3    | The id of the White player
| 12  | 3    | The id of the Black player
| 15  | 3    | The id of the tournament
| 18  | 3    | The id of the annotator
| 21  | 3    | The id of the source
| 24  | 3    | <a href="#date">Game date</a>
| 27  | 1    | <a href="#game_result">Game result</a>
| 28  | 1    | <a href="https://en.wikipedia.org/wiki/Numeric_Annotation_Glyphs">Line evaluation NAG</a> (only set if Game result was set to `Line`)
| 29  | 1    | Round (`0` = unset)
| 30  | 1    | Subround (`0` = unset)
| 31  | 2    | White rating (`0` = unset)
| 33  | 2    | Black rating (`0` = unset)
| 35  | 2    | <a href="#eco">ECO code</a> _or_ Chess960 starting position (65536 - 960 + <a href="https://en.wikipedia.org/wiki/Chess960_numbering_scheme">&lt;position id&gt;</a>)
| 37  | 2    | <a href="#medals">Medals</a>
| 39  | 4    | <a href="#annotation_flags">Annotation flags</a>
| 43  | 2    | <a href="#annotation_magnitude">Annotation magnitude</a>
| 45  | 1    | Number of moves in the main variation of the game (`255` if 255 or more moves were made)

### <a name="cbh_text">CBH guiding text record</a>

| Offset | Bytes | Description
| --- | ---- | ---- |
| 0   | 1    | Bit 0: always set (?); bit 1: guiding text, bit 7: the guiding text has been marked as deleted
| 1   | 4    | Offset into .cbg file where the text data starts
| 5   | 2    | Unknown. Always `0`?
| 7   | 3    | The id of the tournament
| 10  | 3    | The id of the source
| 13  | 3    | The id of the annotator
| 16  | 1    | Round (`0` = unset)
| 17  | 1    | Subround (`0` = unset)
| 18  | 4    | <a href="#annotation_flags">Annotation flags</a> (only Media and Embedded audio, picture and video seems to be possible)
| 22  | 24   | Always `0` ? 

### <a name="game_result">Game Result</a>

| Value | Desccription
| ----  | ----
| 0     | 0-1
| 1     | draw
| 2     | 1-0
| 3     | Line
| 4     | -:+
| 5     | =:=
| 6     | +:-
| 7     | 0-0

### <a name="date">Date</a>

A date is represented as a 24 bit word. A date might be incomplete; for instance only the year might be specified, or only the year and month.

| Bits | Description
| ---- | ----
| 0-4  | The day of the month (0 = unspecified)
| 5-8  | The month (0 = unspecified)
| 9-20 | The year (0 = unspecified)

### <a name="eco">ECO</a>

ECO refers to the opening classification system used by the <a href="https://en.wikipedia.org/wiki/Encyclopaedia_of_Chess_Openings">Encyclopaedia of Chess Openings (ECO)</a>.
A game is typically classified into one of the 500 codes A00-E99. Each code can further be classified into 100 subcodes, e.g. `B97/08` (the use of subcodes is very rare; no standard classification of these exists).
It's encoded as a 16 bit word.

| Bits | Description
| ---- | ----
| 0-6  | Sub ECO (00-99)
| 7-15 | ECO (0 = unset, 1 = A00, 500 = E99)

### <a name="medals">Medals</a>

A 16 bit word, one bit per "medal".

| Bit | Medal
| --- | ----
| 0   | Best game
| 1   | Decided tournament
| 2   | Model game (opening plan)
| 3   | Novelty
| 4   | Pawn structure
| 5   | Strategy
| 6   | Tactics
| 7   | With attack
| 8   | Sacrifice
| 9   | Defense
| 10  | Material
| 11  | Piece play
| 12  | Endgame
| 13  | Tactical blunder
| 14  | Strategical blunder
| 15  | User

### <a name="annotation_flags">Annotation flags</a>

Flags indicating if different types of annotations are used in the game, or if some other specific game properties exist.
The flags are set automatically when a game is saved, based on the game data.

The bits that are left out are unknown and are expected not to be set.

| Bit | Annotation
| --- | ----
| 0   | Starting position (`P`) - game does not start from the initial position 
| 1   | Variations (`v`)
| 2   | Commentary (`c`)
| 3   | Symbols (`s`)
| 4   | Graphical squares
| 5   | Graphical arrows
| 7   | Time spent
| 8   | ? Unknown annotation
| 9   | Training annotation
| 16  | Embedded audio
| 17  | Embedded picture
| 18  | Embedded video
| 19  | Game quotation
| 20  | Path structure
| 21  | Piece path
| 22  | White clock
| 23  | Black clock
| 24  | Critical position
| 25  | Correspondence header
| 26  | ? Media annotation (denoted with M in the AIT column)
| 27  | Unorthodox (not a regular chess game, e.g. Chess960)
| 28  | Web link

#### <a name="annotation_magnitude">Annotation magnitude</a>

Specifies the magnitude for some of the annotation flags above. The corresponding annotation flag must be set for its magnitude value to matter (there may be dirty data). 

| Bit | Size | Annotation
| --- | ---- | ----
| 0   | 2    | Many variations: 1 = [51,300] moves (`V`), 2 = [301,1000] moves (`r`), 3 = [1001,] moves (`R`)
| 2   | 1    | Many commentaries (`C`) (at least 10)
| 3   | 1    | Many symbols (`S`) (at least 10)
| 4   | 1    | Many colored squares (in at least 10 positions)
| 5   | 1    | Many arrows (in at least 6 positions)
| 7   | 1    | Many time spent (at least 11)
| 9   | 1    | Many training annotations (at least 11)

## CBJ file format

## flags file format

N/A
