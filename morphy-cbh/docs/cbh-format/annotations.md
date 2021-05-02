# Annotations

Game annotations are stored in the .cba file in a similar way as the <a href="moves.md">moves data</a>.

## CBA file format

The file starts with a short <a href="#cba_header">header</a> containing metadata about the file itself.
The size of the header varies and is given in the first word.

### <a name="cba_header">CBA file header</a>

The header of the .cba file is the same as the [header in the .cbg file](moves.md#cbg_header).

| Offset | Bytes | Description
| --- | --- | ---
| 0   | 2   | Length of this header. Typically `26`, but in very old databases (created in CB6) it can be `10`.
| 2   | 4   | Total file size
| 6   | 4   | Number of unused bytes due to "holes" between games (number of bytes that would be saved when compacting the database)
| 10  | 8   | Total file size (64-bit version)
| 18  | 8   | Number of unused bytes due to "holes" between games (64-bit version)







CBA file (annotation data)
==========================
26 byte header. Actual annotation data pointer out from CBH file.

Game start
----------
Ofs 0, Len 3 - The game ID this is the annotation for (big-endian)
Ofs 3, Len 4 - ? 01 00 0E 0E ?
Ofs 7, Len 3 - Number of annotations in this game + 1
Ofs 10, Len 4 - The number of bytes used for annotations in this game, starting at ofs 0 (big-endian)
Ofs 14 - start of annotation data

Annotation data
---------------
Ofs 0, Len 3 - Position in gaming sequence (0 = start position, 1 = after first move, -1 = game general) for this annotation (big-endian)
Ofs 3, Len 1 - Type of annotation:
0x02 = text after move
0x03 = symbols
0x04 = graphical squares
0x05 = graphical arrows
0x07 = time spent on the move. 4 bytes:
1 byte - hours spent (?)
1 byte - minutes spent
1 byte - seconds spent
1 byte - ?? (1E on first annotation per player, 0 for rest!?)
0x09 = training annotation (TODO)
0x10 = sound
0x11 = picture
0x13 = game quotation
0x14 = pawn structure (ofs 6 = 3?! TODO)
0x15 = piece path (ofs 6-7 = 03 2b?  03 0d? TODO)
0x16 = whiteclock. 4 bytes, number of hundreths of seconds. [WhiteClock] PGN tag
0x17 = blackclock. 4 bytes, number of hundreths of seconds. [BlackClock] PGN tag
0x18 = critical position
0x19 = correspondence move (header must exist?)
0x20 = video
0x22 = medal
0x23 = variation color
0x24 = Time control in text
33 bytes data, 11 bytes per time serie. For each serie,
4 bytes initial time (in hundredths of seconds)
4 bytes increment time (in hundredths of seconds)
2 bytes number of moves (1000 = rest of the game)
1 byte type of serie (??)  0, 1 and 3 possible values?  3 only on last serie, 1 on intermediatery serie, 0 = !?

                 0x61 = correspondence header (always move -1?)
                 0x82 = text before move

Ofs 4, Len 2 - Number of bytes for this annotation (from ofs 0, little-endian).


Symbols:
Ofs 6, Len 1 - Move comment (! ? !? ?! !! ?? Zugzwang Only move)
Ofs 7, Len 1 - Position evaluation (optional byte, 0 if missing)
Ofs 8, Len 1 - Prefix (optional byte, 0 if missing)

Text:
Ofs 6, Len 1 - 0?
Ofs 7, Len 1 - Language 0 = All, 0x2A = English, 0x35 = Deutsch, 0x31 = France, 0x2B = Spain, 0x46 = Italian, 0x67 = Dutch, 0x75 = Portugese, 0x00 = Pol (bug??)
Ofs 8, Len ? - Text (remaining bytes)  0x9E = diagram (denoted "{#}")

Graphical squares:
Ofs 6, Len 1 - Color (2 = green, 3 = yellow, 4 = red)
Ofs 7, Len 1 - Square (1=a1, 2=a2, 9=b1, ... 64=h8)
These two bytes are repeated until end of annotation

Graphical arrows:
Ofs 6, Len 1 - Color (2 = green, 3 = yellow, 4 = red)
Ofs 7, Len 1 - From square (1=a1, 2=a2, 9=b1, ... 64=h8)
Ofs 8, Len 1 - To square (1=a1, 2=a2, 9=b1, ... 64=h8)
These three bytes are repeated until end of annotation

Critical Position:
Ofs 6, Len 1 - 1 = Critical opening position
2 = Critical middlegame position
3 = Critical endgame position

Game Quotation:
Not done yet

Medal:
Ofs 6, Len 4 - Bit 0-15 (bit 0 = 1 in ofs 9) sets medals according to medal table below

Variation Color
Ofs 6, Len 1 - 0?
Ofs 7, Len 1 - blue component
Ofs 8, Len 1 - green component
Ofs 9, Len 1 - red component

Move comments
-------------
1 = !
2 = ?
3 = !!
4 = ??
5 = !?
6 = ?!
0x16 = Zugzwang
8 = Only move

Position evaluations
--------------------
0x12 = +-
0x10 = +/-
0x0E = +/=
0x0B = =
0x0D = unclear
0x0F = =/+
0x11 = -/+
0x13 = -+
0x92 = theoretical novelty
0x2C = with compensation for the material
0x84 = with counterplay
0x24 = with initiative
0x28 = with attack
0x20 = development advantage
0x8A = zeitnot

Prefix
-----
0x91 = Editorial annotation
0x8E = better is
0x8F = worse is
0x90 = Equivalent is
0x8C = with the idea
0x8D = directed against


