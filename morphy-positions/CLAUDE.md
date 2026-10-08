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
morphy positions build <database> [--filter Q] [--index DIR]
morphy positions update <database> [--index DIR] [--changed ID,...]
morphy positions compact <database> [--index DIR]
morphy positions lookup <database> "<fen>" [--index DIR | --scan [--filter Q]]
```

An index holds every game of a database, or those matching a filter in the database's game
search language (`"tournament.time:normal rating:2300..,mode=both"`): the scan
(`GameScanning.openScan(filter)`) checks it on each game's header, and the entities it refers to,
before reading any moves (v2 compiles it with `GameSearch.compile`; v1 runs its query planner up
front). A database can have several indexes; the service defines them in `position-indexes.json`
and builds and updates them itself.

`PositionIndexBuilder` builds, updates and compacts an index, `PositionIndex.open` opens one, and
`PositionIndex.find` answers a position: the moves played from it with their games and
`MoveStats`, the games that ended there, and all the game ids. The index goes in a directory next
to the database (`IndexFiles.indexDirectoryOf`: `Mega.2cbh` → `Mega.positions/`). It records the
database's main file size, modification time and game count (`DatabaseIdentity`), and the filter,
and is stale when any of them changes (`PositionIndex.isStale(database, filter)`); an update adds
the games after the last one indexed.

## What's indexed

Every position of each game's main line (variations are not), keyed by its 64-bit Zobrist hash
(`Position.getZobristHashLo()`; the en passant file only counts when a pawn can take). A position
repeated in a game counts once, with the move first played from it. Guiding texts, analyses,
deleted and Chess960 games, and games whose moves can't be decoded are left out by the scan.

## Segments

An index is a list of immutable segments, like Cassandra's sstables, named by its manifest
(`index.properties`, `IndexMeta`, written last and atomically), with one facts file
(`GameFactsTable`) for all its games:

- A **build** collects the games' positions in memory in batches, writes each batch sorted as a
  segment (`SegmentWriter`) and merges them into one (`SegmentMerger`); the batches need about
  the room of the index itself.
- An **update** adds a segment of the games added since (and of games given as changed, which it
  lists in its `supersedes.bin`: their entries in earlier segments no longer count).
- A **compaction** merges all segments into one; an update compacts past 4 segments, or when the
  newer segments hold more than 10% of the games.
- A **lookup** (`PositionIndex.find`) asks every segment (`SegmentReader`), drops superseded games
  (`SupersededGames`), joins the groups by move and adds up the stored statistics
  (`MoveStats.plus`).

## Files of a segment (numbers big-endian)

| File | Contents |
|---|---|
| `segment.properties` | `SegmentMeta`: the position counts, the directory's bits, the bytes of a game id |
| `shared.keys` | The hashes of the positions several games reached, in **unsigned** order |
| `shared.offsets` | A long per key into `shared.data`, then the end |
| `shared.data` | Per position: `varint(moves << 1 \| whiteToMove)`; per move: its 16-bit field (`IndexFiles.moveField`: White to move in the top bit, the `MoveCode` below, `0` = `GAME_ENDED` for the games that ended there), its games (varint), a flag byte, the `MoveStats` when flagged (moves of 50 games or more), the game ids as varint differences |
| `single.dir` | `int[2^bits + 1]`: where each value of the top bits of a hash starts in `single.data`; 24 bits for a large segment, fewer for a small one |
| `single.data` | Per position one game reached, in hash order: the hash's bits below the directory's, the 16-bit move field and the game id (3 or 4 bytes) |
| `supersedes.bin` | (optional) the ids of the games whose entries in earlier segments don't count |

For Mega Database 2026 (12M games, 688M positions, 96.5% reached by one game) the index is
8.2 GB, of which 6.6 GB is `single.data`; the keys, offsets, directory and facts (some 650 MB) are
held in memory when open. It builds in about 4 minutes, with some 16 GB of disk at the peak.

## Tests

`PositionIndexTest` builds an index of a few made-up games (transpositions, repetitions, games
ending in a shared position, stored stats) and checks building in tiny batches, updates with new,
changed and deleted games, and compaction against fresh builds; `SampleDatabaseIndexTest` builds
the sample databases' indexes, in both formats, checks every position against the games read one
by one, and compares a build in small batches, and one of half the games updated with the rest.
