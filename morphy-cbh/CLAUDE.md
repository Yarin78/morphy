# morphy-cbh

Core library for reading/writing ChessBase databases (.cbh binary format) and format-independent chess logic.

## Module Structure

Three-layer architecture:

1. **Chess Core** (`se.yarin.chess`) - Format-independent chess logic (Position, Move, GameModel, PGN)
2. **Database API** (`se.yarin.morphy`) - Transaction-based database access, entity indexes, query engine
3. **Storage Layer** (`se.yarin.morphy.storage`) - Low-level file I/O with ItemStorage (fixed-size) and BlobStorage (variable-length)

## Key Dependencies

- **org.immutables:value** - Code generation for immutable value objects (`@Value.Immutable`)
- **org.jetbrains:annotations** - Null-safety (`@NotNull`/`@Nullable`)
- **com.googlecode.concurrent-locks** - Read/write lock management for transactions
- **me.tongfei:progressbar** - Progress bar visualization
- **com.github.albfernandez:juniversalchardet** - Character encoding detection

## Storage Layer

Two storage abstractions in `se.yarin.morphy.storage`:

- **ItemStorage** - Fixed-size records indexed by position (for game headers, entity indexes). Implementations: `FileItemStorage`, `InMemoryItemStorage`.
- **BlobStorage** - Variable-length blobs with offset tracking (for moves, annotations). Implementations: `FileBlobStorage`, `InMemoryBlobStorage`.

Both have serializer interfaces (`ItemStorageSerializer`, `BlobStorageSerializer`) for binary conversion.

## Transaction System

- **DatabaseReadTransaction** - Shared read lock, multiple concurrent reads allowed. Provides stream/iterable access to games and entities.
- **DatabaseWriteTransaction** - Exclusive write lock. Buffers changes in memory, flushed on `commit()`. Supports `rollback()`.
- Always use try-with-resources pattern.

## Test Organization

Tests mirror the source structure under `src/test/java/se/yarin/morphy/`. Test databases live in `src/test/resources/`. Key test utilities: `DatabaseTestSetup`, `GameGenerator`, `TestGames`, `ResourceLoader`.

## Documentation

See `docs/` for detailed documentation:
- `ARCHITECTURE.md` - System design, layered architecture, concurrency model, design decisions
- `USER-GUIDE.md` - Library usage: databases, games, entities, querying, best practices
- `DEVELOPER-GUIDE.md` - Contributing guide: setup, patterns, testing, adding features
- `DATABASE-REFERENCE.md` - All game header fields and entity fields with types and descriptions
- `GAME-REPRESENTATION.md` - How games are represented across three layers (storage, logic, PGN) and annotation architecture
- `ANNOTATION-TEXT-ENCODING.md` - Spec for encoding ChessBase annotations in PGN comments (lossless round-trip)
- `CHESS-PACKAGE.md` - Detailed `se.yarin.chess` package docs (Position, Move, GameModel, PGN)
- `MORPHY-PACKAGE.md` - Detailed `se.yarin.morphy` package docs (Database, transactions, entities)
- `QUERY-ENGINE.md` - Query engine internals: query plans, operators, cost-based optimization, joins
- `cbh-format/` - Reverse-engineered binary file format specification (games, moves, annotations, entities, search boosters)

Any new detailed documentation about something should go into the `docs/` folder.
