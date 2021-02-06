# Moves

The game moves (including variations) are stored in the .cbg file in a storage-efficient way.
Most moves are encoded into 1 byte. For obfuscation purposes, the move data is also "encrypted" using
a [translation table](#translation_table) with a moving index.

For text entry games, the actual textual content (including the title) is also stored in the .cbg files.

## CBG file format

The file starts with a short <a href="#cbg_header">header</a> containing metadata about the file itself.
The size of the header varies and is given in the first word (integers are stored in Big Endian unless otherwise specified).

The number of bytes required for each game varies between games. There is no game index within the file
itself; the offsets given in the .cbh file from each game record is the index. 

The games are stored sequentially in id order. Where the move data of one game ends, the move data for the
next game starts. This means that if you were to insert additional moves to a game
in the beginning of the database, the entire file needs to be shifted the corresponding number of bytes - and
all offsets in the .cbh file (and .cbj file) needs to be updated.

If a game is shortened (for instance, when purging all variations), no corresponding shift will be done,
but instead there will be "holes" of unused bytes in the file. The size of this unused fragmented space is stored in the header.

## <a name="cbg_header">CBG file header</a>

The file size is stored twice, both as a 32-bit integer and a 64-bit integer.
The 64-bit versions were a later addition, presumably to support .cbg-files larger than 2 GB.
However, as of CB16 this still doesn't work; both integers overflow and you'll end up with a corrupt database.

| Offset | Bytes | Description
| --- | --- | ---
| 0   | 2   | Length of this header. Usually `26`, but in very old databases (created in CB6) it can be `10`.
| 2   | 4   | Total file size
| 6   | 4   | Number of unused bytes due to "holes" between games (number of bytes that would be saved when compacting the database)
| 10  | 8   | Total file size (64-bit version)
| 18  | 8   | Number of unused bytes due to "holes" between games (64-bit version)

## <a name="cbg_game">CBG game data</a>

If the game is a text entry (determined by the CBH file), see [CBG text data](#cbg_text).

| Offset | Bytes | Bits | Description
| --- | --- | --- | ---
| 0 | 1 | 7   | Always set if text entry, but can also be set for games (very rarely, unknown effect).
|   |   | 6   | If set, the move data doesn't start from the ordinary initial position. Instead, the start position is explicitly given, see <a href="#setup_position">Setup position</a> below.
|   |   | 0-5 | Encoding mode and chess variant. Determines how to parse the moves, see [encoding modes](#encoding_modes).
| 1 | 3 |     | Number of bytes this game occupies in the CBG file (including this header).

### <a name="setup_position">Setup position</a>

If the moves doesn't start from the initial chess position, it's specified here. 
Otherwise this section is skipped and the [moves data](#moves_data) follows directly.

| Offset | Bytes | Bit | Description
| --- | --- | --- | ---
| 0 | 1 | | Unknown. Always 1? (version?)
| 1 | 1 | 0-3 | En passant file (0 = no en passant, 1 = a, 8 = h)
| | | 4 | 0 = white to move, 1 = black to move
| 2 | 1 | 0 | White O-O-O is possible
|  |  | 1 | White O-O is possible
|  |  | 2 | Black O-O-O is possible
|  |  | 3 | Black O-O-O is possible
| 3 | 1 | | Next move number (0 and 1 both means move 1)
| 4 | 24 | | A 192 bit long bit stream representing the contents of the 64 squares. Starts with the most significant bit in the first byte.
| 28 | (8) | | Additional information for Chess960 games (see below)  

The squares are represented in column major order: a1, a2, a3, ..., a8, b1, b2, ..., h8.
The contents of the square is encoded according to the table below. There can be at most 32 pieces
on the board, so the total number of bits required is 32 * 5 + 32 * 1 bits = 192 bits = 24 bytes.
If there are fewer pieces, the bit stream is padded with zeros.

| Bit pattern | Piece
| ------ | -----
| 0 | Empty square
| 10001 | White king
| 10010 | White queen
| 10011 | White knight
| 10100 | White bishop
| 10101 | White rook
| 10110 | White pawn
| 11001 | Black king
| 11010 | Black queen
| 11011 | Black knight
| 11100 | Black bishop
| 11101 | Black rook
| 11110 | Black pawn

Example: If the first couple of bytes in the bit stream are `88, 14, 147`, this corresponds to the bit stream 
`010110000000111010010011...`. Going from left to right, and matching according to the table above,
we get `empty`, `white pawn`, `empty` * 6, `black rook`, `empty` * 2, `white knight`. 
This corresponds to a `white pawn` on `a2`, `black rook` on `b1` and a `white knight` on `b4`.

The order of the pieces in the setup position are listed determines which piece is the "first piece", and so on - see [moves data](#moves_data).

If this is a Chess960 game (determined by the encoding mode), there are 8 additional bytes describing the game start position.
This is required to be able to decode some moves in the move data.

| Ofs | Byte | Description
| --- | --- | --- 
| 0 | 1 | Square index of the white king
| 1 | 1 | Square index of the black king
| 2 | 1 | Square index of the white a-rook
| 3 | 1 | Square index of the black a-rook
| 4 | 1 | Square index of the white h-rook
| 5 | 1 | Square index of the black h-rook
| 6 | 2 | Chess 960 start position (0-959)

The last value is wrong in many games in some official ChessBase databases, probably due to a software bug.

### <a name="moves_data">Decoding the moves</a>

There are several ways the game moves can be encoded.
This is determined by the _encoding mode_ mentioned earlier. Virtually all games in known ChessBase databases
use encoding mode 0, which is the default for normal chess games. This is the mode describe here. Other modes
are documented in [encoding modes](#encoding_modes).

The moves in a game are represented as a stream of bytes. Most moves are represented as a single byte, describing the
_relative movement_ of a piece. Moves that can't be represented in a single byte, typically promotions,
take three bytes: first a special byte signaling that this is a multi-byte move, then two additional bytes describing the move.
Special bytes are also used to indicate the start or the end of a variation.

When decoding the byte stream, first decrease the byte value with the _number of moves processed so far_ in the game
data (0 for the first move). The special codes for "multiple byte moves" and start/end of variations don't count.
The move count should not be restarted when a variation ends. Then translate the byte according to a
256 byte [translation table](#translation_tables). All operations are done modulo 256.

The following table is then used to determine the move. Pieces are referred to in relative terms. The "first rook"
refers to the white rook starting at a1 (black at a8) and this remains the "first rook" throughout the game until it gets captured.
When a piece (not pawns) get captured, the pieces shift one step. If the "first bishop" gets captured, the second bishop becomes the first
and the third (if it exists) becomes the second. If the "second bishop" gets captured, the third bishop
become the second, and so on. Up to 3 pieces of each type and color are referred to this way. If a player gets
a fourth queen, the moves of that queen is always done using double byte moves (and the forth queen can't "promote" to become
third queen if some other queen is captured). References to pawns never change; the "d-pawn" always refers to the
pawn that started on d2/d7, regardless of where on the board it now is or which other pawns are left. _En passant_ captures are stored as ordinary captures.

The relative movements in the table is given as delta x,y coordinate; x being the file and y the rank. (0,0) corresponds to a1 and (7,7) h8.
All movements are module 8. So a queen at c2 (2,1), moving "x+7, y" actually moves one step "left" and reaches (1,1) or b2.
Where there are ... in table, it means it follows the same pattern as the previous movements of that piece, or
the same pattern as the previous piece of that type.

| Byte | Piece | Relative movement
| --- | --- | ---
| 0 | - | Null move
| 1 | King | x, y+1
| 2 | King | x+1, y+1
| 3 | King | x+1, y
| 4 | King | x+1, y-1
| 5 | King | x, y-1
| 6 | King | x-1, y-1
| 7 | King | x-1, y
| 8 | King | x-1, y+1
| 9 | King | O-O
| 10 | King | O-O-O
| 11 | 1st Queen | x, y+1
| 12 | 1st Queen | x, y+2
| ... | |
| 17 | 1st Queen | x, y+7
| 18 | 1st Queen | x+1, y
| ... | |
| 24 | 1st Queen | x+7, y
| 25 | 1st Queen | x+1, y+1
| ... | |
| 31 | 1st Queen | x+7, y+7
| 32 | 1st Queen | x+1, y-1
| ... | |
| 38 | 1st Queen | x+7, y-7
| 39 | 1st Rook | x, y+1
| ... | |
| 45 | 1st Rook | x, y+7
| 46 | 1st Rook | x+1, y
| ... | |
| 52 | 1st Rook | x+7, y
| 53 | 2nd Rook | x, y+1
| ... | |
| 66 | 2nd Rook | x+7, y
| 67 | 1st Bishop | x+1, y+1
| ... | |
| 73 | 1st Bishop | x+7, y+7
| 74 | 1st Bishop | x+1, y-1
| ... | |
| 80 | 1st Bishop | x+7, y-7
| 81 | 2nd Bishop | x+1, y+1
| ... | |
| 94 | 2nd Bishop | x+7, y-7
| 95 | 1st Knight | x+2, y+1
| 96 | 1st Knight | x+1, y+2
| 97 | 1st Knight | x-1, y+2
| 98 | 1st Knight | x-2, y+1
| 99 | 1st Knight | x-2, y-1
| 100 | 1st Knight | x-1, y-2
| 101 | 1st Knight | x+1, y-2
| 102 | 1st Knight | x+2, y-1
| 103 | 2nd Knight | x+2, y+1
| ... |
| 110 | 2nd Knight | x+2, y-1
| 111 | a-pawn | One step forward (x,y+1 if white, x,y-1 if black)
| 112 | a-pawn | Two steps forward
| 113 | a-pawn | Capture to the right (x+1,y+1 if white, x-1,y-1 if black)
| 114 | a-pawn | Capture to the left
| 115 | b-pawn | ...
| 119 | c-pawn | ...
| 123 | d-pawn | ...
| 127 | e-pawn | ...
| 131 | f-pawn | ...
| 135 | g-pawn | ...
| 139 | h-pawn | ...
| 143 | 2nd Queen | ...
| 171 | 3rd Queen | ...
| 199 | 3rd Rook | ...
| 213 | 3rd Bishop | ...
| 227 | 3rd Knight | ...
| 235 | - | The next two bytes will describe a multi byte move
| 236 | - | Ignore (no move in the move list)
| 237-253 | - | Unknown/unused
| 254 | - | Start of variation
| 255 | - | End of variation

#### Multiple byte moves

Any type of move can be described as a multiple bytes move, but it's typically only done when moving a fourth piece
or for pawn promotions. The two bytes are to be read as a 16-bit word with the most significant byte first.

| Bit | Description
| --- | ---
| 0-5 | Source square (0=a1, 1=a2, ...)
| 6-11 | Destination square
| 12-13 | Promotion piece (0=queen, 1=rook, 2=bishop, 3=knight)

A special case of a double byte move is castles in a Chess 960 game. Short castles are described as g1-g1 or g8-g8,
while long castle are described as c1-c1 or c8-c8 (source and destination square are the same).

#### Variations

The node tree is visited in depth first order, starting with the primary variation in all lines.
If there are more moves to come from the same position, before going into the next variation, a "start of variation"
byte is stored. When there are no more moves in the variation, and "end of variation" byte is stored.
This is also the case at the very end of the game, so there will always be one more "end of variation" byte than
"start of variation".

For instance, the following line with variations

    1. e4 c5 (1. - c6 2. d4; 1. - Nf6 2. e5) 2. Nf3 d6 (2. - Nc6 3. Bb5) 3. d4

would semantically be stored as

    e4 <start> c5 Sf3 <start> d6 d4 <end> Sc6 Lb5 <end> <start> c6 d4 <end> Sf6 e5 <end>

#### <a name="encoding_modes">Encoding modes</a>

The previous section described encoding mode 0. There are many more encoding modes as seen in this table.

| Mode | Encoder | modifier | order| Chess variant
| --- | --- | --- | --- | ---
| 0 | Compact | pre | default | Regular chess
| 1 | Simple | pre | default | Regular chess
| 2 | Compact | pre | reverse | Regular chess
| 3 | Simple | pre | reverse | Regular chess
| 4 | Compact | post | default | Regular chess
| 5 | Simple | post | default | Regular chess
| 6 | Compact | post | reverse | Regular chess
| 7 | Simple | post | reverse | Regular chess
| 8 | Compact | post | default | Chess variant [Losing Chess](https://en.wikipedia.org/wiki/Losing_chess)
| 9 | Compact | post | reverse | Chess variant [Losing Chess](https://en.wikipedia.org/wiki/Losing_chess)
| 10 | Compact | post | default | Chess variant [Chess960](https://en.wikipedia.org/wiki/Fischer_random_chess) (Fischer Random)
| 11 | Compact | post | reverse | Chess variant [Chess960](https://en.wikipedia.org/wiki/Fischer_random_chess) (Fischer Random)
| 12-13 | ? | ? | ? | Chess variant [Out Chatrang](https://en.wikipedia.org/wiki/Shatranj)
| 14-15 | ? | ? | ? | Chess variant [Twins](https://www.chessvariants.com/diffmove.dir/twin.html)
| 16-17 | ? | ? | ? | Chess variant [Makruk](https://en.wikipedia.org/wiki/Makruk) (Thai chess)
| 18-19 | ? | ? | ? | Chess variant Pawns
| 63   | ? | ? | ? | Illegal positions allowed

The _Compact_ move encoder was the single-byte move encoder described in the previous section.
The _Simple_ move encoder is a two-byte move encoder described [here](#simple_move_encoder). 

The _modifier_ determines the order of events in the byte reading sequence:

* pre: read byte from stream, decrease with number of moves seen, translate in translation table 
* post: read byte from stream, translate in translation table, decrease with number of moves seen

The _order_ determines which pieces are to be considered "1st rook" and so on. If reverse,
the 1st rook is the one on h1/h8, "a-pawn" is the one on h2/h7, and so on - effectively a horizontal mirror of the chess board.
For the Simple move encoder this has a slightly different meaning, see below.

#### <a name="translation_tables">Translation tables</a>

As a way of obfuscating the file format, all bytes passes through a _translation table_, a 256 byte 1-1 mapping
of a byte to another byte. The decoding step "decrease the byte with number of moves seen so far" also contributes in obfuscating
the format as the same move gets encoded differently depending on the move number.

Further, each encoding mode above uses a different translation table. There are hundreds of these translation tables
in the ChessBase executable files. See the source code of Morphy for the contents of these translation tables.

#### <a name="simple_move_encoder">Simple move encoder</a>

Every move is stored as a 16-bit word (Big Endian).

If the `reverse` flag above is _not_ set, bit 0-5 is the index of the source square, and bit 6-11 the index of the destination square (0=a1, 1=a2, etc).
If the `reverse` flag is set, the source and destination squares are swapped. If both squares are 0, the move is a null move.
If the move is a pawn promotion, bit 12-13 represents the new piece (0=queen, 1=rook, 2=bishop, 3=knight).

Variations are built up similarly as the compact (default) move encoder above, with the difference
that start- and end markers are stored in the same byte as the move.
If bit 15 is set, a new variation starts before the move has been decoded.
If bit 14 is set, a variation ends _after_ the move has been decoded.

## <a name="cbg_text">CBG text data</a>

This section describes "games" that contains textual content instead of a chess game. All integers describing the text data are in Little Endian, unless otherwise specified.

| Ofs | Bytes | Description
| --- | --- | ---
| 0   | 1   | Flags. Bit 7 always set for texts, remaining bits are 0.
| 1   | 3   | Number of bytes for this text (Big Endian)
| 4   | 2   | Text format version, see below.
| 6   | 2   | Number of language specific text titles to follow (can be 0)

This is followed by the title in one or more languages, as given by the last entry above:

| Ofs | Bytes | Description
| --- | --- | ---
| 0   | 0   | Language  (0=english, 1=german, 2=france, 3=spain, 4=italian, 5=dutch, 6=portuguese)
| 2   | 2   | Length of the title
| 4   | ?   | The title

This is followed by the text contents in one or more languages. 

| Ofs | Bytes | Description
| --- | --- | ---
| 0 | 1 | Unknown. Can either be 0 or 1.
| 1 | 2 | Number of language specific text contents to follow.

This is followed by the contents in one or more languages, as given by the last entry above.
Note that the contents can be in more or fewer number of languages than the title.
The format of the contents is determined by the "Text format version" field given earlier.

**Text format version 1 and 2**

Oldest format. The contents are given as plain text, followed by a separate section with formatting/embedding information.
Format 2 (rare) is the same as format 1, except that the size integers are 32 bit instead of 16 bit. 

| Ofs | Bytes | Description
| --- | --- | ---
| 0   | 2   | Language  (0=english, 1=german, 2=france, 3=spain, 4=italian, 5=dutch, 6=portuguese)
| 2   | 2/4 | Length of text contents (size depending on version, see above)
| 4/6 | ?   | Text contents
| ?   | 2/4 | Length of formatting data (size depending on version, see above)
| ?   | ?   | Formatting data. This is structured data that has yet to be figured out.

**Text format version 3**

The current format. The contents are in HTML.

| Ofs | Bytes | Description
| --- | --- | ---
| 0   | 2   | Language  (0=english, 1=german, 2=france, 3=spain, 4=italian, 5=dutch, 6=portuguese)
| 2   | 4   | Length of HTML contents
| 4   | ?   | HTML contents
| ?   | 4   | Unknown value. Always 0?
