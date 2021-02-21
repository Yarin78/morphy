# Search Boosters

_Search Boosters_ are auxilary files that speeds up the search in the database.
They can be recreated from the other files if needed, so they're not as essential.
If ChessBase notices they are missing when you open the database (and invoke a search),
it typically creates them on-the fly and then maintains them if the database is updated.

The [entity Search Booster files](#entity_search_booster_format), .cit, .cib, .cit2, .cib2, allows you to
quickly get all games that references an [entity](entities.md).

The [game event Search Booster file](#cbb_file_format), .cbb, allows you to more quickly
find games based on structures and events in the game.

The [moves Search Booster file](#cbgi_file_format), .cbgi, contains pointers to where
in the .cbg file the move data starts. This information also exists in the .cbh file,
but presumably this file is loaded in its entirety into memory.

## <a name="entity_search_booster_format">Entity Search Booster file format</a>

In short, the .cit/.cit2 file contains a table mapping an entity id to a position in the .cib/.cib2 file,
where the actual game id's are stored in a linked-list type of structure. The .cit file
is responsible for the entity types Players, Tournament, Annotators, Sources and Teams, while
the later addition of Game Tags are stored in the .cit2 file.

All integers in these files are 32 bit signed integers in Little Endian.

### .cit/.cit2 file format

The .cit file starts with a 12 byte header storing three integers.

| Offset | Bytes | Description
| --- | --- | ---
| 0 | 4 | The size of a record in this file (40)
| 4 | 4 | Unknown. Always 0?
| 8 | 4 | Unknown. Always 0?

Then follows the records. Each record corresponds to the entities with the same index
as this record, and contain 5 pairs of _block indices_
These reference the head and tail block, respectively in the .cib file.

The value -1 is used for non-existing entities. For instance, if a database
contains 7 players but only 3 tournaments; this file will have (at least) 7 records
containing block references for all 7 players, but the last four blocks will
have the value -1 for the tournament block indices.

| Offset | Bytes | Description
| --- | --- | ---
| 0 | 4 | Player head block index
| 4 | 4 | Player tail block index
| 8 | 4 | Tournament head block index
| 12 | 4 | Tournament tail block index
| 16 | 4 | Team head block index
| 20 | 4 | Team tail block index
| 24 | 4 | Source head block index
| 28 | 4 | Source tail block index
| 32 | 4 | Annotator head block index
| 36 | 4 | Annotator tail block index

The .cit2 file is constructed similarly, except that there is only one pair of
integers. These refer to the head/tail block index for Game Tags in the .cib2 file.

### .cib/.cib2 file format

The .cib/.cib2 file starts with a 12 byte header. All integers are Little Endian.

| Offset | Bytes | Description
| --- | --- | ---
| 0 | 4 | The size of a record (block) in this file (64)
| 4 | 4 | Number of blocks in this file
| 8 | 4 | Unknown. Always 0?

Then follows the blocks. Each block contains up to 16 integers.

| Offset | Bytes | Description
| --- | --- | ---
| 0 | 4 | Id of the next block in the linked list (-1 if there is no next block)
| 4 | 4 | Unknown. Always 0?
| 8 | 4 | Number of game references in this block
| 12 | 4 | Id of a game
| 16 | 4 | Id of a game
| .. | 4 | <more game ids>

If the size of a block is 64 bytes, there can be at most 13 games in a block.
If the block is not full, the remaining allocated data contains trash bytes.

If an entity occurs twice in a game (White and Black are the same player or the same team),
that game id is referenced twice in the .cib file. Entities in a logically deleted
game are still references in the file.

The games in these block-lists are always ordered incrementally, and each block
is always filled up entirely before moving on to the next block. This means that
if an entity is changed in a game, all game indexes for the old and the new entity
may have to be shifted one step left or right, depending on if it's an insert or delete.

### Using the Entity Search Booster

To lookup all games the player with id X has played:

* In the .cit file, get record X and look up the player head block index, H
* In the .cib file, fetch block H. In that block there may be up to 13 games and optionally a link to another block, N
* In the .cib file, fetch block N. There may be 13 more games here. Repeat until the next block link is -1.

## <a name="cbb_file_format">Game Position Search Booster file format</a>

The .cbb file contains one record of 52 bytes per game. The first 52 bytes in the file (record 0),
is a header. All integers are in Big Endian.

### .cbb header format

| Offset | Bytes | Description
| --- | --- | ---
| 0 | 4 | Number of records in the file
| 4 | 2 | Unknown. Always `1`. File format version number?
| 6 | 2 | The size of a record. Always `52`.
| 8 | 44 | Unused. Always `0`.

### .cbb record format

After the header follows all the actual game records, one per game.
The records describe events that has happened during the game (main variation only).
In some cases it refers specifically to the final position.

For games that are texts, all bytes are 0.

| Offset | Bytes | Bits | Description
| --- | --- | --- | ---
| 0 | 3 | | Always `0` and unused in later version of ChessBase. Set in earlier databases, unclear purpose.
| 3 | 1 | 0-2 | Number of white pawns left in final position (`7` if 7 or 8 pawns left)
|   |   | 3 | Set if white has lost all pieces (excluding pawns; not unset if getting a new Queen)
|   |   | 4-6 | Number of white pawns left in final position (`7` if 7 or 8 pawns left)
|   |   | 7 | Set if black has lost all pieces (excluding pawns; not unset if getting a new Queen)
| 4 | 1 | 0 | White Queen has been captured
|   |   | 1 | One White Rook has been captured
|   |   | 2 | One White Bishop has been captured
|   |   | 3 | One White Knight has been captured
|   |   | 4 | One Black Queen has been captured
|   |   | 5 | One Black Rook has been captured
|   |   | 6 | One Black Bishop has been captured
|   |   | 7 | One Black Knight has been captured
| 5 | 1 | 0 | White Queen has been captured (same as previous byte)
|   |   | 1 | Two White Rooks has been captured
|   |   | 2 | Two White Bishops has been captured
|   |   | 3 | Two White Knights has been captured
|   |   | 4 | One Black Queen has been captured (same as previous byte)
|   |   | 5 | Two Black Rooks has been captured
|   |   | 6 | Two Black Bishops has been captured
|   |   | 7 | Two Black Knights has been captured
| 6 | 1 |   | Bit 0-7 set if a White Pawn has occupied a3, b3, ..., h3, respectively, at some point during the game
| 7 | 1 |   | Bit 0-7 set if a White Pawn has occupied a4, b4, ..., h4, respectively, at some point during the game 
| 8 | 1 |   | Bit 0-7 set if a White Pawn has occupied a5, b5, ..., h5, respectively, at some point during the game
| 9 | 1 |   | Bit 0-7 set if a White Pawn has occupied a6, b6, ..., h6, respectively, at some point during the game
| 10 | 1 |   | Bit 0-7 set if a White Pawn has occupied a7, b7, ..., h7, respectively, at some point during the game
| 11 | 1 |   | Bit 0-7 set if a Black Pawn has occupied a2, b2, ..., h2, respectively, at some point during the game
| 12 | 1 |   | Bit 0-7 set if a Black Pawn has occupied a3, b3, ..., h3, respectively, at some point during the game
| 13 | 1 |   | Bit 0-7 set if a Black Pawn has occupied a4, b4, ..., h4, respectively, at some point during the game
| 14 | 1 |   | Bit 0-7 set if a Black Pawn has occupied a5, b5, ..., h5, respectively, at some point during the game
| 15 | 1 |   | Bit 0-7 set if a Black Pawn has occupied a6, b6, ..., h6, respectively, at some point during the game
| 16 | 1 |   | Bit 0-7 set if the White King has been on rank 1-8, respectively, at some point during the game
| 17 | 1 |   | Bit 0-7 set if the Black King has been on rank 1-8, respectively, at some point during the game
| 18 | 1 |   | Bit 0-7 set if the White King has been on file a-h, respectively, at some point during the game
| 19 | 1 |   | Bit 0-7 set if the Black King has been on file a-h, respectively, at some point during the game
| 20 | 1 |   | Bit 0-7 set if any White Rook or Queen has occupied a1, b1, ..., h1, respectively, at some point during the game 
| 21 | 1 |   | Bit 0-7 set if any White Rook or Queen has occupied a2, b2, ..., h2, respectively, at some point during the game 
| 22 | 1 |   | Bit 0-7 set if any White Rook or Queen has occupied a3, b3, ..., h3, respectively, at some point during the game 
| 23 | 1 |   | Bit 0-7 set if any White Rook or Queen has occupied a4, b4, ..., h4, respectively, at some point during the game 
| 24 | 1 |   | Bit 0-7 set if any White Rook or Queen has occupied a5, b5, ..., h5, respectively, at some point during the game 
| 25 | 1 |   | Bit 0-7 set if any White Rook or Queen has occupied a6, b6, ..., h6, respectively, at some point during the game 
| 26 | 1 |   | Bit 0-7 set if any White Rook or Queen has occupied a7, b7, ..., h7, respectively, at some point during the game 
| 27 | 1 |   | Bit 0-7 set if any White Rook or Queen has occupied a8, b8, ..., h8, respectively, at some point during the game 
| 28 | 1 |   | Bit 0-7 set if any Black Rook or Queen has occupied a1, b1, ..., h1, respectively, at some point during the game
| 29 | 1 |   | Bit 0-7 set if any Black Rook or Queen has occupied a2, b2, ..., h2, respectively, at some point during the game
| 30 | 1 |   | Bit 0-7 set if any Black Rook or Queen has occupied a3, b3, ..., h3, respectively, at some point during the game
| 31 | 1 |   | Bit 0-7 set if any Black Rook or Queen has occupied a4, b4, ..., h4, respectively, at some point during the game
| 32 | 1 |   | Bit 0-7 set if any Black Rook or Queen has occupied a5, b5, ..., h5, respectively, at some point during the game
| 33 | 1 |   | Bit 0-7 set if any Black Rook or Queen has occupied a6, b6, ..., h6, respectively, at some point during the game
| 34 | 1 |   | Bit 0-7 set if any Black Rook or Queen has occupied a7, b7, ..., h7, respectively, at some point during the game
| 35 | 1 |   | Bit 0-7 set if any Black Rook or Queen has occupied a8, b8, ..., h8, respectively, at some point during the game
| 36 | 1 |   | Bit 0-7 set if any White Knight or Bishop has occupied a1, b1, ..., h1, respectively, at some point during the game
| 37 | 1 |   | Bit 0-7 set if any White Knight or Bishop has occupied a2, b2, ..., h2, respectively, at some point during the game
| 38 | 1 |   | Bit 0-7 set if any White Knight or Bishop has occupied a3, b3, ..., h3, respectively, at some point during the game
| 39 | 1 |   | Bit 0-7 set if any White Knight or Bishop has occupied a4, b4, ..., h4, respectively, at some point during the game
| 40 | 1 |   | Bit 0-7 set if any White Knight or Bishop has occupied a5, b5, ..., h5, respectively, at some point during the game
| 41 | 1 |   | Bit 0-7 set if any White Knight or Bishop has occupied a6, b6, ..., h6, respectively, at some point during the game
| 42 | 1 |   | Bit 0-7 set if any White Knight or Bishop has occupied a7, b7, ..., h7, respectively, at some point during the game
| 43 | 1 |   | Bit 0-7 set if any White Knight or Bishop has occupied a8, b8, ..., h8, respectively, at some point during the game
| 44 | 1 |   | Bit 0-7 set if any Black Knight or Bishop has occupied a1, b1, ..., h1, respectively, at some point during the game
| 45 | 1 |   | Bit 0-7 set if any Black Knight or Bishop has occupied a2, b2, ..., h2, respectively, at some point during the game
| 46 | 1 |   | Bit 0-7 set if any Black Knight or Bishop has occupied a3, b3, ..., h3, respectively, at some point during the game
| 47 | 1 |   | Bit 0-7 set if any Black Knight or Bishop has occupied a4, b4, ..., h4, respectively, at some point during the game
| 48 | 1 |   | Bit 0-7 set if any Black Knight or Bishop has occupied a5, b5, ..., h5, respectively, at some point during the game
| 49 | 1 |   | Bit 0-7 set if any Black Knight or Bishop has occupied a6, b6, ..., h6, respectively, at some point during the game
| 50 | 1 |   | Bit 0-7 set if any Black Knight or Bishop has occupied a7, b7, ..., h7, respectively, at some point during the game
| 51 | 1 |   | Bit 0-7 set if any Black Knight or Bishop has occupied a8, b8, ..., h8, respectively, at some point during the game

## <a name="cbgi_file_format">Move index Search Booster file format</a>

The .cbgi file is a very simple file containing one 32-bit integer (Little Endian) for each game in the database.
The first integer in the file is the number of games in the file; then follows that many integers
which points to the position in the .cbg file where the move data starts.

This is exactly the same offset as stored in the .cbh file, except that it's only stored for proper games.
For text entries, the value is always 0.
