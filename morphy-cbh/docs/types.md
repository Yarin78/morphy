# Types

The following data types and enumerations are used across multiple files.

### <a name="strings">Strings</a>

All strings are by default encoded using code page ISO-8859-1, 8 bits per character.
If you type in a character that doesn't exist in that encoding, a `?` will be stored instead.
There are databases with cyrillic letters; it's unclear what causes that.

If there is a fixed number of bytes in a record for a string, shorter strings
are zero terminated and usually (but not always) padded with all zeros.

Sometimes strings are prefixed with the length of the string followed by that many bytes;
exact format varies depending on the context.

### <a name="game_result">Game Result</a>

The result of a chess game.

| Value | Description
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
May also be stored as a 32 bit word, in which case the 8 upper bits are unused.

| Bits | Description
| ---- | ----
| 0-4  | The day of the month (0 = unspecified)
| 5-8  | The month (0 = unspecified)
| 9-20 | The year (0 = unspecified)


### <a name="nag">Numeric Annotation Glyphs</a>

Annotation glyphs, or NAG's, are chess symbols used when annotating the games.
ChessBase encode these in 1 byte using a <a href="https://en.wikipedia.org/wiki/Numeric_Annotation_Glyphs">
standardized numbering system</a>


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

### <a name="rating_type">Rating type</a>

A 16 byte record used to specify what type of rating was given in the .cbh file for the corresponding player.
Only certain combinations seems possible in practice.

| Offset | Bytes | Description
| --- | --- | ---
| 0   | 2   | Bit 0-2 (`1` = Internal rating, `2` = National rating); other bits may be set as well
| 2   | 1   | National time control (`0` = normal, `1` = blitz, `2` = rapid, `3` = corr.); ignore if not a national rating
| 3   | 1   | International time control (`1` = normal, `2` = blitz, `3` = rapid, `4` = corr.); ignore if not an international rating
| 4   | 1   | `0` if international rating, otherwise the <a href="#nation">Nation code</a> (TODO)
| 5   | 11  | The name of the rating system as a zero-terminated string, e.g. "FIDE", "ICCF" etc

### <a name="final_material">Final material</a>

A summary of the material at the final position of the game (number of pawns, knights etc)

For details, see code.

### <a name="endgame_info">Endgame info</a>

Represents different types of Endgames that a game contained at various stages.

For details, see code.

### <a name="nation">Nation</a>

Single byte representing a country. Sometimes also used for language.

For details, see code.
