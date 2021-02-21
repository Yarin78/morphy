# Game Headers

**Game Headers**, metadata about a game, are stored across multiple database files. The main game header is stored in the [.cbh file](#cbh_file), 
and the extended header in the [.cbj file](#cbj_file). Additional game information (Top Games) is stored in the [flags file](#flags).

Besides games, metadata about *guiding texts* are also stored in the same files. As the attentive user of ChessBase may have noticed,
these guiding texts are mixed with the games, sharing the same id-space. The first byte of each record contains information
if the record is a game or a text. For more information about textual content, see [Text](text.md).

Games are referenced by their id; the first game/text has id 1.

## <a name="cbh_file">CBH file format</a>

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

The id references to players, tournament, annotator and source are all 0-based; see [Entities](entities.md).
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
| 24  | 3    | <a href="types.md#date">Game date</a>
| 27  | 1    | <a href="types.md#game_result">Game result</a>
| 28  | 1    | <a href="types.md#nag">Line evaluation NAG</a> (only set if Game result was set to `Line`)
| 29  | 1    | Round (`0` if not specified)
| 30  | 1    | Subround (`0` if not specified)
| 31  | 2    | White rating (`0` if not specified)
| 33  | 2    | Black rating (`0` if not specified)
| 35  | 2    | <a href="types.md#eco">ECO code</a> _or_ Chess960 starting position (65536 - 960 + <a href="https://en.wikipedia.org/wiki/Chess960_numbering_scheme">&lt;position id&gt;</a>)
| 37  | 2    | <a href="types.md#medals">Medals</a>
| 39  | 4    | <a href="types.md#annotation_flags">Annotation flags</a>
| 43  | 2    | <a href="types.md#annotation_magnitude">Annotation magnitude</a>
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


## <a name="cbj_file">CBJ file format</a>

The CBJ file stores additional metadata about games, such as team information, endgame information, timestamps etc. 
In databases created in CB6 the file doesn't exist, but it was added shortly after. The size of each game record has changed multiple
times, indicating that this is a dynamic file format that may continue to change. An important aspect of the format is that
databases create in later version of ChessBase can still be opened in earlier version; unrecognized fields are simple ignored.

As with the CBH file, the file starts with a <a href="#cbj_header">header</a> containing metadata about the file itself.
Then follows one record per <a href="#cbj_game">game</a>/<a href="#cbh_text">text</a>. The header is 32 bytes,
while the size of the record varies depending on CBJ version. 

All integers are stored in Big Endian (most significant byte first) unless otherwise specified.

### <a name="cbj_header">CBJ file header</a>

| Offset | Bytes | Description
| --- | --- | ---
| 0   | 4   | .cbj file version, see table below.
| 4   | 4   | Size of the game records. `120` in newer database, but earlier versions have shorter headers; see table below.
| 8   | 4   | Number of game records.
| 12  | 20  | Presumably random dirty bytes, perhaps reserved for future use (may contain parts of random strings etc).

The version of the .cbj file loosely correspond to the ChessBase version. The .cbj file itself did not exist in the earliest versions
(the .cbh file format was introduced in ChessBase 6.0).

| ChessBase version | .cbj file version | Game record size
| --- | --- | ---
| 8? | 1 | 8
| 9? | 5 | 30
| 10? | 6 | 38
| 11? | 7 | 74
| 12 | 8 | 78
| 13-16 | 11 | 120

If a database has an old version of the extended header and a game is saved to the database, the entire .cbj file is upgraded to the latest version and the additional fields are assigned default values.

### <a name="cbj_game">CBJ game record</a>

The id references to teams and tags are 0-based; see [Entities](entities.md).
For games with no teams or tag, `-1` is used. Note that this differs compared to how entities are referenced in the .cbh file.

| Offset | Bytes | Description
| --- | --- | ---
| 0   | 4   | The id of the White team (-1 if no team)
| 4   | 4   | The id of the Black team (-1 if no team)
| 8   | 4   | Offset into the .cbm file where media data is stored. Only used by guiding texts; -1 if not used.
| 12  | 8   | Offset into the .cba file where annotations data start. Same as in the .cbh file, but 64 bit version. 
| 20  | 10  | <a href="types.md#final_material">Final material</a> 
| 30  | 8   | Offset into the .cbg file where moves data start. Same as in the .cbh file, but 64 bit version. 
| 38  | 16  | <a href="types.md#rating_type">Rating type for White player</a>
| 54  | 16  | <a href="types.md#rating_type">Rating type for Black player</a>
| 70  | 4   | Unknown. Fairly often set, but data seems very random, unsure if it's actually used. Stays unchanged when game is changed or copied.
| 74  | 4   | Unknown. Fairly often set, but data seems very random, unsure if it's actually used. Stays unchanged when game is changed or copied.
| 78  | 2   | Version. Starts at 1, increases every time the game is saved. Also bumped when copied to a new database.
| 80  | 8   | The timestamp when the game was originally created (also a globally unique ID of the game). Remains unchanged when game is copied to a new database. If multiple games are created at the same time, they get sequentially increasing numbers.
| 88  | 20  | <a href="#endgame_info">Endgame information</a>
| 108 | 4   | Timestamp when the game was last save. When a game is copied between databases, it's not updated though. 
| 116 | 4   | The id of the game tag (-1 if no game tag)



## <a name="flags_file">flags file format</a>

The .flags file stores additional flags for games in the database.
Currently this only seems to refer to which games are marked as "Top Games".

### <a name="flags_header">flags header</a>

The first 12 bytes of the file is a header. The integers are stored in Big Endian.

| Offset | Bytes | Description
| --- | --- | ---
| 0 | 4 | The integer 0x0F010B09. Unknown purpose.
| 4 | 4 | Game capacity in this file; the number of 16-game "chunks" currently reserved. Is increased 16 at a time.
| 8 | 4 | The integer 2. Unknown purpose. Number of bits per game!? (gets reset to 2 when saving if changed)

### flags bits

There are 2 bits per game, starting at offset 12 and the least significant bits. The first two bits refers to game 0 which doesn't exist so they are not used.

| Bit | Description
| --- | ---
| 0 | Set if the game has been evaluate as a Top Game or not
| 1 | Set if the game is a Top Game

It's unclear what causes a game to be evaluated to a Top Game. In Mega Database, all games are evaluated and bit 0 is always set.
When adding new games to a database, bit 0 typically is not set right away.
