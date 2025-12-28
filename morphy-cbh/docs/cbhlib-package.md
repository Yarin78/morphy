# The `se.yarin.cbhlib` Package

The `se.yarin.cbhlib` package provides the legacy API for working with ChessBase databases. This package offers direct, low-level access to database files and is useful for migration, conversion, and advanced use cases.

## Overview

This package provides:
- **Direct file access**: Low-level access to ChessBase database files
- **Legacy compatibility**: Support for older ChessBase database formats
- **In-memory operations**: Ability to work with databases entirely in memory
- **Game loading**: Conversion between database format and `GameModel`
- **Search capabilities**: Game searching and filtering

## Core Classes

### Database

`Database` is the main class for working with ChessBase databases in the legacy API:

**Opening databases:**
- `open(File file)`: Open an existing database from disk
- `openInMemory(File file)`: Load entire database into memory (faster reads, writes not persisted)
- `Database()`: Create a new in-memory database

**Accessing database components:**
- `getHeaderBase()`: Access to game headers (`.cbh` file)
- `getExtendedHeaderBase()`: Access to extended headers (`.cbj` file)
- `getMovesBase()`: Access to moves (`.cbg` file)
- `getAnnotationBase()`: Access to annotations (`.cba` file)
- `getPlayerBase()`: Access to players (`.cbp` file)
- `getTournamentBase()`: Access to tournaments (`.cbt` file)
- `getAnnotatorBase()`: Access to annotators (`.cbc` file)
- `getSourceBase()`: Access to sources (`.cbs` file)
- `getTeamBase()`: Access to teams (`.cbe` file)
- `getGameTagBase()`: Access to game tags (`.cbl` file)

**Working with games:**
- `getGame(int gameId)`: Get a `Game` object by ID
- `getGameModel(Game game)`: Convert database game to `GameModel`
- `getTextModel(Game game)`: Get text model for guiding texts
- `addGame(GameModel gameModel)`: Add a new game to the database
- `replaceGame(int gameId, GameModel gameModel)`: Replace an existing game
- `deleteGame(int gameId)`: Delete a game

**Searching:**
- `search(SearchFilter filter)`: Search games with a filter
- `stream()`: Stream all games

### Game

`Game` represents a game in the database:

**Accessing game data:**
- `getId()`: Game ID
- `getHeader()`: Game header
- `getExtendedHeader()`: Extended header
- `getWhite()`, `getBlack()`: Player entities
- `getTournament()`: Tournament entity
- `getAnnotator()`, `getSource()`: Other entities
- `getModel()`: Get full `GameModel` with moves
- `getTextModel()`: Get text model

**Raw data access:**
- `getMovesBlob()`: Raw move data as ByteBuffer
- `getAnnotationsBlob()`: Raw annotation data as ByteBuffer
- `getMovesOffset()`, `getAnnotationOffset()`: Storage offsets

### DatabaseUpdater

`DatabaseUpdater` handles updating the database when games are added or modified:
- Automatically creates/updates entity entries
- Manages entity references
- Updates statistics and counts
- Handles database consistency

### GameLoader

`GameLoader` converts between database format and `GameModel`:
- `loadGame(Game game)`: Load game into `GameModel`
- `loadTextModel(Game game)`: Load text model
- Handles move decoding and annotation parsing

## Entity System

### Entity Bases

Each entity type has a corresponding "Base" class that provides direct access to entity storage:

**PlayerBase:**
- `get(int id)`: Get player by ID
- `getAll()`: Get all players
- `add(PlayerEntity player)`: Add a player
- `put(int id, PlayerEntity player)`: Update a player
- `search(String name)`: Search players by name

**TournamentBase:**
- Similar methods for tournaments
- `search(String name)`: Search tournaments

**Other bases:**
- `AnnotatorBase`, `SourceBase`, `TeamBase`, `GameTagBase`: Similar patterns

### Entity Classes

Entity classes represent the data:
- **`PlayerEntity`**: Player information (name, nationality, dates, etc.)
- **`TournamentEntity`**: Tournament information (name, location, dates, type, etc.)
- **`AnnotatorEntity`**: Annotator information
- **`SourceEntity`**: Source information
- **`TeamEntity`**: Team information
- **`GameTagEntity`**: Game tag information

## Game Storage

### GameHeaderBase

`GameHeaderBase` provides access to game headers:
- `get(int id)`: Get header by game ID
- `add(GameHeader header)`: Add a new header
- `put(int id, GameHeader header)`: Update header
- `getAll()`: Get all headers
- `size()`: Number of games

### ExtendedGameHeaderBase

`ExtendedGameHeaderBase` provides access to extended headers:
- Similar methods to `GameHeaderBase`
- Stores additional metadata not in main header

### MovesBase

`MovesBase` stores encoded move data:
- `getMovesBlob(long offset)`: Get move data at offset
- `putMovesBlob(ByteBuffer moves)`: Store move data
- Handles move encoding/decoding

### AnnotationBase

`AnnotationBase` stores encoded annotation data:
- `getAnnotationsBlob(long offset)`: Get annotation data at offset
- `putAnnotationsBlob(ByteBuffer annotations)`: Store annotation data
- Handles annotation encoding/decoding

## Search System

### GameSearcher

`GameSearcher` provides game searching capabilities:

```java
GameSearcher searcher = new GameSearcher(database);
List<Game> results = searcher.search(filter);
```

### SearchFilter

`SearchFilter` allows building search criteria:
- `PlayerFilter`: Filter by player
- `DateRangeFilter`: Filter by date range
- `TournamentFilter`: Filter by tournament
- `EcoFilter`: Filter by ECO code
- `ResultFilter`: Filter by game result
- `RatingFilter`: Filter by rating range
- Combine filters with `and()`, `or()`, `not()`

## Annotations

The `se.yarin.cbhlib.annotations` subpackage provides annotation support:

**Annotation types:**
- `TextBeforeMoveAnnotation`, `TextAfterMoveAnnotation`: Text comments
- `NAGAnnotation`: Numeric Annotation Glyphs
- `GraphicalArrowsAnnotation`, `GraphicalSquaresAnnotation`: Graphical annotations
- `VideoAnnotation`, `PictureAnnotation`: Media annotations
- `TimeSpentAnnotation`, `WhiteClockAnnotation`, `BlackClockAnnotation`: Time annotations
- `ComputerEvaluationAnnotation`: Engine evaluations
- Many more annotation types

**AnnotationBase:**
- `getAnnotations(Game game)`: Get annotations for a game
- `putAnnotations(Game game, List<Annotation> annotations)`: Store annotations

## Media Support

The `se.yarin.cbhlib.media` subpackage handles ChessBase media files:
- `ChessBaseMediaLoader`: Load media associated with games
- `HeaderEvent`, `MarkerEvent`: Media event types

## Usage Examples

### Opening and Reading

```java
// Open database
Database db = Database.open(new File("database.cbh"));

// Get a game
Game game = db.getGame(1);
GameModel model = game.getModel();

// Iterate all games
for (int i = 1; i <= db.getHeaderBase().size(); i++) {
    Game g = db.getGame(i);
    System.out.println("Game " + i + ": " + g.getWhite().getFullName());
}
```

### Adding Games

```java
Database db = Database.open(new File("database.cbh"));

GameModel game = new GameModel();
// ... populate game ...

db.addGame(game);
```

### Searching

```java
Database db = Database.open(new File("database.cbh"));
GameSearcher searcher = new GameSearcher(db);

SearchFilter filter = SearchFilter.and(
    new PlayerFilter(whitePlayerId),
    new DateRangeFilter(startDate, endDate)
);

List<Game> results = searcher.search(filter);
```

### Working with Entities

```java
Database db = Database.open(new File("database.cbh"));

// Get player
PlayerEntity player = db.getPlayerBase().get(playerId);

// Search players
List<PlayerEntity> players = db.getPlayerBase().search("Kasparov");

// Add player
PlayerEntity newPlayer = PlayerEntity.builder()
    .firstName("Magnus")
    .lastName("Carlsen")
    .build();
db.getPlayerBase().add(newPlayer);
```

### In-Memory Database

```java
// Create in-memory database
Database db = new Database();

// Add games (not persisted)
db.addGame(game1);
db.addGame(game2);

// Use normally, but writes won't be saved
```

## Differences from `se.yarin.morphy`

The `cbhlib` package differs from `morphy` in several ways:

1. **No transactions**: Direct access without transaction management
2. **Mutable entities**: Entity objects can be modified directly
3. **Lower-level API**: More direct access to storage files
4. **Legacy format**: Supports older ChessBase formats
5. **Simpler model**: Less abstraction, more direct file operations

## When to Use

Use `cbhlib` when:
- Migrating from older code
- Need direct file access
- Working with in-memory databases
- Converting between formats
- Need fine-grained control over storage

Use `morphy` when:
- Building new applications
- Need transaction safety
- Want type-safe immutable APIs
- Need advanced querying capabilities

## Design Notes

- **File-based**: Each database component maps to a file (`.cbh`, `.cbg`, `.cba`, etc.)
- **Direct access**: Methods directly access underlying storage
- **Flexible**: Can work with databases entirely in memory
- **Compatible**: Supports various ChessBase database versions

