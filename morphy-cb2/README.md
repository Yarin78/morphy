# morphy-cb2

Reads and writes ChessBase v2 databases, the `.2cbh` family of files described in
[../format/v2](../format/v2).

## Layout

| Package | Contents |
|---|---|
| `se.yarin.morphy.cb2` | `Database2Cbh`, the engine; `ReadTransaction` and `WriteTransaction`; `Game`, a record seen through a transaction; `Database2CbhFacade`, the vendor-neutral `Database` |
| `storage` | `ByteStore` over a file or memory, and `RecordFile`, the framed records of `.2cbg` and `.2cba` |
| `games` | `.2cbh`: `GameHeader`, `TextHeader`, `AnalysisHeader` and their packed fields |
| `moves` | the move words, the word stream of a game, and guiding text bodies |
| `annotations` | the annotation types and the position blocks of `.2cba` |
| `entities` | `.2lid`: players, tournaments, sources, teams and game tags |
| `indexes` | `.2lgd` game lists and `.2lcd` sort orders, with the sort keys and collation |
| `convert` | DTOs from v2 games and entities, and back |
| `query` | `GameSearch` and `EntitySearch`, answering queries with the same fields as v1 |

Each file class reads and writes its own file only. The transactions tie them together.

## Writing

A `WriteTransaction` gathers changes and applies them all on `commit()`:

- Move and annotation records are appended with ChessBase's spare area. A replaced record
  that doesn't fit takes space from the spare areas of the records after it, moving them up,
  so the files never get holes.
- Entities are found by name, or by the id a `GameHeaderModel` is bound to, and created if
  missing. Blank fields refer to the empty placeholder entities, as in ChessBase. An entity no
  record refers to any more is deleted.
- The game lists and the sort orders are updated for every entity whose references or fields
  changed.

## Limitations

- Endgame types and classification scores aren't derived; new and replaced games get 0.
- Game quotations and training questions can only be written as they were read from a v2
  database; those from elsewhere, such as a v1 database, are left out. Any annotation that
  doesn't read back as written is left out rather than stored.
- Comments and texts are written as UTF-8, also those read as cp1252.
- Games can't be deleted, analyses can't be written, and the `.ini` file isn't touched.
- The collation of sort orders follows the specification, which doesn't fit a few pairs of
  entities in large databases.

## Tests

The tests read `../test-databases/wch2`, a conversion of `../test-databases/world-ch` by
ChessBase, and compare the two game by game through morphy-cbh, which is a test dependency
only. `Consistency.check` verifies that the files of a database agree with each other, and runs
after every kind of write in the write tests.
