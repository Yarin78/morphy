# morphy-positions

An index from the positions in a database's games to the games that reached them and the moves
they played from them, with the statistics of those moves. It's what the service's position
search (`/api/position-indexes/{id}/search`) and the board's Games pane run on; without one,
`PositionScanner` finds the same answer by playing through every game, which takes seconds.
Depends on morphy-api only: the games are read through the `GameScanning` extension of the
`Database` facade, which the v1 and v2 facades implement.

The whole approach (files, building, lookups, the service, the classes, and what could be
simpler) is described, with diagrams, in [docs/POSITION-INDEX.md](docs/POSITION-INDEX.md).

## Build and use

```bash
morphy positions build <database> [--filter Q] [--index DIR] [--work-dir DIR]
morphy positions lookup <database> "<fen>" [--index DIR | --scan [--filter Q]]
```

An index holds every game of a database, or those matching a filter in the database's game
search language (`"tournament.time:normal rating:2300..,mode=both"`): the scan
(`GameScanning.openScan(filter)`) checks it on each game's header, and the entities it refers to,
before reading any moves (v2 compiles it with `GameSearch.compile`; v1 runs its query planner up
front). A database can have several indexes; the service defines them in `position-indexes.json`
and builds them itself.

`PositionIndexBuilder` builds an index, `PositionIndex.open` opens one, and `PositionIndex.find`
answers a position: the moves played from it with their games and `MoveStats`, the games that
ended there, and all the game ids. The index goes in a directory next to the database
(`IndexFiles.indexDirectoryOf`: `Mega.2cbh` → `Mega.positions/`), and is rebuilt as a whole: it
records the database's main file size, modification time and game count (`DatabaseIdentity`),
and the filter, and is stale when any of them changes (`PositionIndex.isStale(database, filter)`).

## What's indexed

Every position of each game's main line (variations are not), keyed by its 64-bit Zobrist hash
(`Position.getZobristHashLo()`; the en passant file only counts when a pawn can take). A position repeated
in a game counts once, with the move first played from it. Guiding texts, analyses, deleted and
Chess960 games, and games whose moves can't be decoded are left out by the scan.

## Files (numbers big-endian)

| File | Contents |
|---|---|
| `meta.properties` | `IndexMeta`: format version, build time, database identity, `recentSince` (newest year − 2), the stats threshold, the bytes of a game id, the position counts, the filter and the number of games indexed |
| `facts.bin` | `GameFactsTable`: two longs per game id: result, date, Elos, player ids |
| `shared.keys` | The hashes of the positions several games reached, in **unsigned** order |
| `shared.offsets` | A long per key into `shared.data`, then the end |
| `shared.data` | Per position: the number of moves (varint); per move: its 16-bit code (`MoveCode`; `0xFFFF`, `IndexFiles.GAME_ENDED`, for the games that ended there), its games (varint), a flag byte, the `MoveStats` when flagged (moves of 50 games or more), the game ids as varint differences |
| `single.dir` | `int[2^24 + 1]`: where each value of the top 24 bits of a hash starts in `single.data` |
| `single.data` | Per position one game reached, in hash order: bits 39–24 of the hash (16 bits) and the game id (3 or 4 bytes) |

A single-game position isn't stored by its full hash: its 40-bit prefix (the directory's 24 bits
and the 16 stored) finds candidates, and each candidate game is played through to check it
reaches the position (`PositionIndex.moveAfter`), which also gives the move. For Mega Database
2026 (12M games, 688M positions, 96.5% reached by one game) the index is 4.85 GB, of which 3.3 GB
is `single.data`; `shared.*`, `single.dir` and `facts.bin` (650 MB) are held in memory when open.

## Building

The games are read in parallel and each position becomes a 16-byte record (hash; game id, side
to move, move) in one of 256 bucket files in the work directory, by the top 8 bits of the hash —
some 15 GB for a Megabase, deleted as they're read. Each bucket is then radix-sorted by hash and
its positions written out in order. The index is built in `<index>.building` and moved in place
when done.

## Tests

`PositionIndexTest` builds an index of a few made-up games (transpositions, repetitions, games
ending in a shared position, stored stats); `SampleDatabaseIndexTest` builds the sample databases'
indexes, in both formats, and checks every position against the games read one by one.
