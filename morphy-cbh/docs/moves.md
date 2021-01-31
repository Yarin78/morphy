# Moves

The game moves (including variations) are stored in the .cbg file encoded in a storage-efficient way.
Most moves are encoded into 1 byte, including markers for start and end of variations.
The move data is also "encrypted" using a substitution scheme.

For guiding texts, the actual textual content (including the title) is also stored in the .cbg files.

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
but instead there will be "holes" of unused bytes in the file. The size of this fragmentation is stored in the header.

### <a name="cbg_header">CBG file header</a>

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

### <a name="cbg_game">CBG game data</a>

If the game is a text entry (determined by the CBH file), see [CBG text data](#cbg_text).

| Offset | Bytes | Bits | Description
| --- | --- | --- | ---
| 0 | 1 | 7   | Always set if text entry, but can also be set for games (very rarely, unknown effect).
|   |   | 6   | If set, the move data doesn't start from the ordinary initial position. Instead, the start position is explicitly given, see below.
|   |   | 0-5 | Encoding mode and chess variant. Determines how to parse the moves, see [moves data](#moves_data)
| 1 | 3 |     | Number of bytes this game occupies in the CBG file (including this header)

#### <a name="setup_position">Setup position</a>

If the moves doesn't start from the initial chess position, it's specified here. 
Otherwise the [moves data](#moves_data) follows instead.

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

The squares are represented in column major order: a1, a2, a3, .. a8, b1, b2, ... h8.
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
we get empty, white pawn, empty * 6, black rook, empty * 2, white knight. This corresponds to a white pawn on a2, black rook on b1 and a white knight on b4.

If this is a Chess960 game, there are 8 additional bytes describing the game start position.
This is required to be able to decode some moves in the move data.

The order the pieces are listed determines which piece is the "first piece", and so on - see [moves data](#moves_data).  

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

#### <a name="moves_data">Moves data</a>

| Encoding mode | Description
| --- | ---
| 0-7 | Regular chess
| 8-9 | Chess variant [Losing Chess](https://en.wikipedia.org/wiki/Losing_chess)
| 10-11 | Chess variant [Chess960](https://en.wikipedia.org/wiki/Fischer_random_chess) (Fischer Random)
| 12-13 | Chess variant [Out Chatrang](https://en.wikipedia.org/wiki/Shatranj)
| 14-15 | Chess variant [Twins](https://www.chessvariants.com/diffmove.dir/twin.html)
| 16-17 | Chess variant [Makruk](https://en.wikipedia.org/wiki/Makruk) (Thai chess)
| 18-19 | Chess variant Pawns
| 63   | Illegal positions allowed

### <a name="cbg_text">CBG text data</a>

All integers describing the text data are in Little Endian, unless otherwise specified.

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


[At game start]

+0  byte    1   bit 7: If set, the data is not encoded (iff guiding text?)
bit 6: If set, the game doesn't start with the initial position.
bit 0-3: 0xA = chess960?? can also be 0x4 and 0x5
This whole byte is set to 0x7F if the position is an illegal position!?
+0  int     3   Game size, in number of bytes (including this, excluding trash bytes)
+4  byte   (28) If game does not start with initial position, here that position follows.
?  byte   ?    Game data (see below), ends with 0C (=pop position)


Game data
=========
The moves in a game are represented as a stream of bytes. Most moves are represented as a single byte, describing the
relative movement of a piece. Pieces are described in terms such as "first rook" (meaning the rook that for white
starts on a1 and for black on a8), "second rook" etc.

When decoding the byte stream, first decrease the byte value with the number of moves so far processed in the game
data (minus 0 for the first move). The special codes for "multiple byte moves" and begin/end of variations don't count.
The move count should not be restarted when a variation end. Then translate the byte according to the
translation table below.

All pawn moments are seen from the point of view of the player to move. Pawn captures include en passant.

When a "first piece" is captured, the "second piece" becomes the new "first piece" for that player, and the
"third piece" becomes the "second piece". When a "second piece" is cpatured, the old "third piece" becomes the
"second piece". A "fourth piece" (or higher) never "promotes" and its movements are always represented with
multiple bytes. Pawns are never adjusted in this way, so the e-pawn remains as the "fifth pawn" throughout the game.

Multiple byte moves describes a move by denoting the start square and the destination square. This is done
with 6 bits each. Bit 0-5 in the second byte is the start square (a1=0, a2=1, h8=63), bit 6-7 in the second
byte and 0-3 in the first is the destination square. For pawn promotions, bit 4-5 is also used
(0=queen, 1=rook, 2=bishop, 3=knight). Any type of move can be described as a multipe byte move, but it's
typically only done when moving a fourth piece or doing a pawn promotion.

Single byte move translation table
----------------------------------
Note: These bytes look random, but they have actually been "encrypted" using a static array of 256 bytes that can be found at position 0x7AE4A8 in CBase9.exe and position 0x9D6530 in CBase10.exe (it starts with 0xAA, 0x49, 0x39, 0xD8, 0x5D, 0xC2, 0xB1, 0xB2). By reverse looking up these values, the new value will correspond to the order the are listed here.

Special
-------
AA = null move

King
----
49 = y+1
39 = x+1, y+1
D8 = x+1
5D = x+1, y+7
C2 = y+7
B1 = x+7, y+7
B2 = x+7
47 = x+7, y+1
76 = 0-0
B5 = 0-0-0

First queen
-----------
A5 = y+1
B8 = y+2
CB = y+3
53 = y+4
7F = y+5
6B = y+6
8D = y+7
79 = x+1
BE = x+2
EB = x+3
21 = x+4
99 = x+5
D2 = x+6
57 = x+7
4D = x+1, y+1
B4 = x+2, y+2
BF = x+3, y+3
62 = x+4, y+4
BD = x+5, y+5
24 = x+6, y+6
96 = x+7, y+7
A7 = x+1, y+7
48 = x+2, y+6
28 = x+3, y+5
6E = x+4, y+4 (not primarly used)
2F = x+5, y+3
5A = x+6, y+2
18 = x+7, y+1

First rook (a1/a8 at start)
----------
4E = y+1
F8 = y+2
43 = y+3
D7 = y+4
63 = y+5
9C = y+6
E6 = y+7
2E = x+1
C6 = x+2
26 = x+3
88 = x+4
30 = x+5
61 = x+6
6F = x+7

Second rook (h1/h8 at start)
-----------
14 = y+1
A9 = y+2
68 = y+3
EE = y+4
FB = y+5
77 = y+6
E2 = y+7
A6 = x+1
05 = x+2
8B = x+3
A1 = x+4
98 = x+5
32 = x+6
52 = x+7

First bishop (c1/c8 at start)
------------
02 = x+1, y+1
97 = x+2, y+2
E1 = x+3, y+3
41 = x+4, y+4
C3 = x+5, y+5
7C = x+6, y+6
E4 = x+7, y+7
06 = x+1, y+7
B7 = x+2, y+6
55 = x+3, y+5
D9 = x+4, y+4 (not primarily used)
2C = x+5, y+3
AE = x+6, y+2
37 = x+7, y+1

Second bishop (f1/f8 at start)
-------------
F6 = x+1, y+1
3F = x+2, y+2
08 = x+3, y+3
93 = x+4, y+4
73 = x+5, y+5
5E = x+6, y+6
78 = x+7, y+7
35 = x+1, y+7
F2 = x+2, y+6
6D = x+3, y+5
71 = x+4, y+4 (not primarly used)
A2 = x+5, y+3
F3 = x+6, y+2
16 = x+7, y+1

First knight (b1/b8 at start)
------------
58 = x+2, y+1
3D = x+1, y+2
FA = x-1, y+2
E9 = x-2, y+1
BA = x-2, y-1
D4 = x-1, y-2
DD = x+1, y-2
4A = x+2, y-1

Second knight (g1/g8 at start)
-------------
C4 = x+2, y+1
0E = x+1, y+2
FE = x-1, y+2
5F = x-2, y+1
75 = x-2, y-1
07 = x-1, y-2
89 = x+1, y-2
34 = x+2, y-1

a2/a7-pawn
------------
2D = one step forward
C1 = two steps forward
8E = capture right
F5 = capture left

b2/b7-pawn
------------
64 = one step forward
17 = two steps forward
70 = capture right
A4 = capture left

c2/c7-pawn
------------
7B = one step forward
DA = two steps forward
E0 = capture right
85 = capture left

d2/d7-pawn
------------
C5 = one step forward
0B = two steps forward
90 = capture right
F9 = capture left

e2/e7-pawn
------------
84 = one step forward
FF = two steps forward
15 = capture right
36 = capture left

f2/f7-pawn
------------
09 = one step forward
9E = two steps forward
7D = capture right
DE = capture left

g2/g7-pawn
------------
BB = one step forward
DF = two steps forward
BC = capture right
3A = capture left

h2/h7-pawn
------------
12 = one step forward
33 = two steps forward
13 = capture right
19 = capture left

Second queen
------------
E5 = y+1
94 = y+2
50 = y+3
11 = y+4
EA = y+5
31 = y+6
01 = y+7
5C = x+1
95 = x+2
CA = x+3
D3 = x+4
1D = x+5
7E = x+6
EF = x+7
44 = x+1, y+1
80 = x+2, y+2
A0 = x+3, y+3
1F = x+4, y+4
83 = x+5, y+5
00 = x+6, y+6
4B = x+7, y+7
67 = x+1, y+7
20 = x+2, y+6
5B = x+3, y+5
2A = x+4, y+4 (not primarly used)
92 = x+5, y+3
B6 = x+6, y+2
60 = x+7, y+1

Third queen
-----------
1A = y+1
42 = y+2
0F = y+3
0D = y+4
B0 = y+5
D1 = y+6
23 = y+7
F0 = x+1
7A = x+2
54 = x+3
4F = x+4
F4 = x+5
A8 = x+6
72 = x+7
E7 = x+1, y+1
40 = x+2, y+2
38 = x+3, y+3
59 = x+4, y+4
87 = x+5, y+5
E8 = x+6, y+6
6C = x+7, y+7
86 = x+1, y+7
04 = x+2, y+6
F1 = x+3, y+5
8C = x+4, y+4 (not primarly used)
CE = x+5, y+3
6A = x+6, y+2
DB = x+7, y+1

Third rook
----------
81 = y+1
82 = y+2
9A = y+3
1B = y+4
9D = y+5
0A = y+6
2B = y+7
8F = x+1
CD = x+2
ED = x+3
10 = x+4
74 = x+5
69 = x+6
D6 = x+7

Third bishop
------------
51 = x+1, y+1
B9 = x+2, y+2
45 = x+3, y+3
3B = x+4, y+4
56 = x+5, y+5
91 = x+6, y+6
FD = x+7, y+7
AB = x+1, y+7
66 = x+2, y+6
3E = x+3, y+5
46 = x+4, y+4 (not primarily used)
B3 = x+5, y+3
FC = x+6, y+2
C8 = x+7, y+1

Third knight
------------
9B = x+2, y+1
C0 = x+1, y+2
E3 = x-1, y+2
A3 = x-2, y+1
AC = x-2, y-1
C9 = x-1, y-2
EC = x+1, y-2
27 = x+2, y-1

Special
-------
29 = multiple byte move to follow
9F = dummy - skip and don't count thi move. Used by earlier CB verions as padding!?
25 = unused
C7 = unused
CC = unused
65 = unused
4C = unused
D5 = unused
1E = unused
CF = unused
03 = unused
8A = unused
AF = unused
F7 = unused
AD = unused
3C = unused
D0 = unused
22 = unused
1C = unused
DC = push position (start of variant)
0C = pop position (end of variant)
