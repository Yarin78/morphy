# How ChessBase uses the format

Conventions that are not part of the on-disk layout, but that a reader should
expect and a writer should probably follow. None of this is required to parse a
database.

## Free space in the move and annotation files

Records in `.2cbg` and `.2cba` are stored back to back with no gaps, yet a game
can be edited without rewriting the file. Each record carries a **spare area**,
counted by *B* in its header and always zero, which lets the content grow in
place.

When ChessBase writes a record from scratch it gives it

- 96 spare bytes for a game, or 192 for a guiding text, plus
- 1 to 8 bytes more, so that the record **ends at a file offset that is a
  multiple of 8**.

So *B* is 98, 100, 102 or 104 for a game — an odd value cannot occur, since the
move stream is a whole number of 16-bit words — and 193 to 200 for a text.

Editing then behaves as follows:

- A game that **still fits** is rewritten in place. The record keeps its length,
  and the spare area absorbs the difference in either direction.
- A game that **outgrows its record** takes exactly what it needs from the
  records that follow. The next record is moved up by that amount, loses that
  much spare area, and its offset in the `.2cbh` file is updated. The growing
  record can be left with no spare area at all.
- If the next record has too little spare, the **squeeze cascades** to the one
  after, and so on until enough has been reclaimed. Records beyond that point do
  not move and the file does not change size.

Because the reclaimed amounts are exact, a record that has been squeezed no
longer ends on an 8-byte boundary. The alignment holds only for records written
from scratch.

The file never contains holes, and nothing is moved to the end to make room.
What happens when a game needs more space than all the records after it have
between them, or when the last record in the file grows, has not been observed.

## Placeholder entities

A game header never stores −1 for an entity reference except for the two team
fields. When the user leaves a field blank, the game refers instead to an
**entity whose text is empty** — a player with no name, a game tag with no
titles, and so on.

Such a placeholder is created the first time it is needed, so its id is not
necessarily 0. A reader should treat an entity with empty text as "not set"
rather than reporting it as an unnamed entity.

## Creation timestamps identify a record, not a moment

The creation timestamp at 0x98 of a game record is written once, when the record
is first created, and is never touched again.

- **Copying a game carries the timestamp across.** A game and a copy of it in the
  same database have identical creation timestamps, to the resolution of the
  field.
- Editing does not reset it. Two records with the same creation timestamp may by
  now differ in every other respect.

At a resolution of about 240 nanoseconds it is close to unique in practice, which
makes it useful for recognising that two records share an origin — but it cannot
serve as an identifier, since copies share it.

## Classification scores

The six bytes at 0xb0 of a game record are zero until the database is classified.
Running ChessBase's classification fills them in for every game and, apart from
the version and last-changed timestamp, changes nothing else in the record. The
final material fields are rewritten with the values they already held. Guiding
texts only get a new version.

## Abandoned list blocks

Not every list block counted by the `.2lgd` header is reachable. A large database
can contain long runs of blocks that no list points at, holding stale copies of a
list as it was when the database was smaller. They can account for a substantial
fraction of the file.

They are recognisable: the *next* pointer of an abandoned block is 0 rather than
a block number or −1, whereas a live chain always ends with −1.

These blocks are still included in the header's count of blocks in use. A reader
should follow chains from the entity heads and ignore everything else; a writer
that rebuilds the file can simply omit them.

## What ChessBase reports about a database

The counts shown in ChessBase's interface come from these places:

| Shown | Source |
|---|---|
| games | the `.2cbh` header's next game id, minus 1 |
| players | the node count of the player index in `.2lcd` — players with at least one game, which is fewer than the player count in the `.2lid` header, since that includes annotators without games |
| tournaments | the tournament count in the `.2lid` header |
| keys, positions | the `.cko` and `.cpo` files, which are not part of this format |

## The `.ini` file

Plain text, and not needed to read the database. The first section describes the
database itself:

```ini
[Descr2CBG]
Type=0
Title=My database
Usage=2
Access=1037618
```

- `Type` is the database type set in its properties.
- `Title` is the displayed title, which need not match the file name.
- `Usage` increases over time, apparently counting how often the database has
  been opened.
- `Access` is the date of last access, in the
  [date encoding](README.md#dates).

The remaining sections hold import history, dialog defaults, the position of the
game list, and the column layout of the game list.
