# ChessBase file format

This documention gives a low level description of the ChessBase file format, also
known as the CBH format. 
It's the result of many hours of manual work investigating (reverse engineering) the data files
by studying and modifying databases in ChessBase.

The documentation is not complete and is likely to contain errors and incorrect assumptions, but should
be thorough enough to parse most ChessBase databases you are likely to encounter.

The .cbh format was introduced in ChessBase 6.0 and has evolved
through each new version of ChessBase, the most recent as of this writing being CB16.
A nice thing with the format is that it's both backward and forward compatible, 
meaning older versions of ChessBase can still read databases created in later versions of ChessBase.

## Games

The foundation in a database are the chess games stored in it. As an avid user of ChessBase
knows, there are two types of "games" in the database: regular _chess games_ (including chess variants such as Chess960) 
and _guiding texts_ (or just _text_ for short).
The latter is formatted textual content that may also contain multimedia files. Throughout this documentation,
"game" may in many cases either mean only chess game entries, or both type of entries; it should hopefully be obvious from the context.

Metadata about the games are stored in the .cbh and .cbj files, see [Game Headers](games.md).
The actual game move data (including variations) are stored in the .cbg file, see [Moves](moves.md). The contents of a text entry is also stored in this file.
Game annotations (textual annotation, symbols, graphical annotations etc) are stored in the .cba file, see [Annotations](annotations.md).

## Other entities

The database also contains secondary entities that are referenced to in the games. They're
stored in what the ChessBase documentation refers to as index files.
The index structure of all these files are similar, see [Entities](entities.md).

The specific entities are: [Players](players.md) in the .cbp file, [Tournaments](tournaments.md) in the .cbt file,
[Annotators](annotators.md) in the .cbc file, [Sources](sources.md) in the .cbs file,
[Teams](teams.md) in the .cbe file and [Game Tags](game_tags.md) in the .cbl file.

## Multimedia

Multimedia files (images, video) are stored in a subfolder with the same name as the database
with the extension .bmp or .html.
A manifest of what media files are use in a game is stored in the .cbm file.

## Search boosters and additional indexes

There are a number of additional files that are used to speed up the search.
They can be recreated from the other files if they go missing and are therefore less essential.
These are generally called [Search Boosters](search_boosters.md).
