# How ChessBase uses the format

Conventions that are not part of the on-disk layout, but that a reader should
expect and a writer should probably follow. None of this is required to parse a
database.

## Game ids and deletion

A game id is the position of the game's record in `.cbh` and in `.cbj`, counting
from 1. Records are never removed: a game that is deleted is marked in its
[type byte](1-game-headers.md#record-kinds), keeps its id, and keeps its record in
every file, its moves and annotations, and its place in the lists of
[`.cib`](5-search-boosters.md#cib-and-cib2). A new game gets the next game id, the
one held in the `.cbh` header, and the counter is written twice in the header.

## Free space in the move and annotation files

The records of `.cbg` and `.cba` are in game id order, so that the offsets in
`.cbh` ascend with the game id. A game whose moves or annotations become
**shorter** stays where it was, and the difference is a **hole** that nothing uses;
the header counts the bytes in holes. How a game that grows is written is
**unknown**.

## Entities

An entity is created when a game first needs it, and it is apparently deleted when the
last game or guiding text that refers to it lets go. Its **number of references** and
its **first game** are kept up to date: the first game is the lowest id among the
records that refer to it. A deleted entity's record joins the
[chain of deleted records](4-entities.md#deleted-records), and a new entity likely takes
the first record of the chain before the file is extended.

Creating an entity places it in the [tree](4-entities.md#the-tree). The ids of the other
entities do not change.

### Placeholder entities

A game header never stores −1 for an entity reference. When the user leaves a field
blank, the game refers instead to an **entity whose text is empty**: an annotator
with no name, a tournament with no title, and so on.

Such a placeholder is created the first time it is needed, so its id is not
necessarily 0. A reader should treat an entity with empty text as "not set" rather
than reporting it as an unnamed entity.

## Search boosters

ChessBase updates the [search boosters](5-search-boosters.md) as it changes a
database, and builds them when a search needs one that is missing.

## The `.ini` file

Plain text, and not needed to read the database. The first section describes the
database itself:

```ini
[DescrCBG]
Type=0
Title=My database
Usage=12
Access=1034650
```

- `Type` is the database type set in its properties.
- `Title` is the displayed title, which need not match the file name.
- `Usage` increases over time, apparently counting how often the database has
  been opened.
- `Access` is the date of last access, in the [date encoding](README.md#dates).

The `[SearchBooster]` section holds the flags `DontAskCPU`, `DontAskCbb` and
`DontAskCgi`, apparently the answers to the questions ChessBase asks about building
the search boosters. The remaining sections hold the last values entered in the
dialogs for adding a game and window settings.
