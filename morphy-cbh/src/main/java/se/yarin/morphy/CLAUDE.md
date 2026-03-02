# se.yarin.morphy

Top-level database API package. This is the main entry point for working with ChessBase databases.

## Key Classes

- **Database** - Main facade for opening/creating databases. Provides access to all indexes, repositories, and game operations. Use `Database.open(file)` or `Database.create(file)`. For unit tests, an in-memory database created with `new Database()` is often enough.
- **DatabaseReadTransaction** - Shared read lock. Provides `stream()`, `iterable()` for games and entity read transactions. Multiple concurrent reads allowed.
- **DatabaseWriteTransaction** - Exclusive write lock. Buffers changes in memory. Call `commit()` to flush, `rollback()` to discard. Handles entity resolution automatically (creates/looks up players, tournaments, etc. from game data).
- **DatabaseConfig** - Configuration options for database behavior.
- **DatabaseContext** - Shared context for metrics, instrumentation, and configuration.
- **Game** - Represents a single game with lazy-loaded model via `getModel()`.
- **GameAdapter** - Converts between `GameModel` (chess layer) and database storage format.
- **EntityRetriever** - Interface for looking up entities by ID across transaction boundaries.

## Sub-packages

- **entities/** - Entity model and indexes (Player, Tournament, Annotator, Source, Team, GameTag)
- **games/** - Game header storage, move/annotation repositories, game filters
- **boosters/** - Cross-reference indexes mapping games to entities
- **queries/** - Query engine with cost-based optimization
- **storage/** - Low-level ItemStorage/BlobStorage abstractions
- **metrics/** - Performance instrumentation
- **exceptions/** - Domain-specific exceptions
- **text/** - Text content handling
- **validation/** - Database integrity validation

## File Format

A database consists of multiple files with the same base name:
- **Mandatory**: `.cbh` (headers), `.cbg` (moves), `.cba` (annotations), `.cbp` (players), `.cbt` (tournaments), `.cbc` (annotators), `.cbs` (sources)
- **Optional**: `.cbj` (extended headers), `.cbtt` (tournament extra), `.cbe` (teams), `.cbl` (game tags), `.flags` (top games)
- **Search boosters**: `.cbb`, `.cit`, `.cib`, `.cit2`, `.cib2`, `.cbgi`
