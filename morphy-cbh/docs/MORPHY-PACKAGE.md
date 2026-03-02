# The `se.yarin.morphy` Package

The `se.yarin.morphy` package provides the modern API for working with ChessBase databases. This package offers a transaction-based, type-safe interface for reading and writing chess games and entities.

## Overview

This package implements a complete ChessBase database system with:
- **Transaction-based operations**: Read and write transactions ensure data consistency
- **Entity management**: Players, tournaments, annotators, sources, teams, and game tags
- **Game storage**: Efficient storage and retrieval of chess games with moves and annotations
- **Query system**: Powerful querying capabilities for searching games and entities
- **Type safety**: Immutable data structures with compile-time type checking

## Core Classes

### Database

`Database` is the main entry point for working with ChessBase databases. It provides:

**Opening databases:**
- `open(File file)`: Open an existing database
- `open(File file, DatabaseMode mode)`: Open with specific mode (read-only, read-write)
- `create(File file)`: Create a new database
- `openInMemory(File file)`: Load entire database into memory

**Accessing components:**
- `gameHeaderIndex()`: Access to game headers
- `playerIndex()`, `tournamentIndex()`, etc.: Access to entity indexes
- `moveRepository()`: Access to move storage
- `annotationRepository()`: Access to annotation storage

**Convenience methods:**
- `getGame(int gameId)`: Get a game by ID
- `getPlayer(int playerId)`: Get a player entity
- `getTournament(int tournamentId)`: Get a tournament entity

### DatabaseReadTransaction

`DatabaseReadTransaction` provides read-only access to the database:

**Creating transactions:**
```java
try (DatabaseReadTransaction txn = database.beginReadTransaction()) {
    // Read operations
}
```

**Key methods:**
- `getGame(int gameId)`: Get a game by ID
- `iterable()`: Iterate over all games
- `iterable(GameFilter filter)`: Iterate over filtered games
- `stream()`: Get a stream of games
- `playerTransaction()`, `tournamentTransaction()`, etc.: Access entity transactions

**Entity access:**
- `getPlayer(int id)`: Get player entity
- `getTournament(int id)`: Get tournament entity
- Similar methods for other entity types

### DatabaseWriteTransaction

`DatabaseWriteTransaction` provides write access to the database:

**Creating transactions:**
```java
try (DatabaseWriteTransaction txn = database.beginWriteTransaction()) {
    // Write operations
    txn.commit();
}
```

**Adding games:**
- `addGame(GameModel gameModel)`: Add a new game
- `addGame(GameModel gameModel, Map<String, Object> metadata)`: Add with metadata
- `replaceGame(int gameId, GameModel gameModel)`: Replace existing game
- `deleteGame(int gameId)`: Delete a game

**Updating entities:**
- `updatePlayerById(int id, Player updated)`: Update player metadata
- `updateTournamentById(int id, Tournament updated)`: Update tournament metadata
- Similar methods for other entity types

**Transaction control:**
- `commit()`: Commit changes to database
- `setAutoCommit(boolean)`: Enable/disable auto-commit mode
- `version()`: Get transaction version number

### Game

`Game` represents a game stored in the database:

**Accessing game data:**
- `id()`: Game ID
- `header()`: Game header (players, date, result, etc.)
- `extendedHeader()`: Extended header (ratings, teams, etc.)
- `white()`, `black()`: Player entities
- `tournament()`: Tournament entity
- `getModel()`: Get full `GameModel` with moves
- `getTextModel()`: Get text model for guiding texts

**Accessing raw data:**
- `getMovesBlob()`: Raw move data
- `getAnnotationsBlob()`: Raw annotation data
- `getMovesOffset()`, `getAnnotationOffset()`: Storage offsets

## Entity System

### Entity Types

The package supports several entity types:
- **`Player`**: Chess players with name, nationality, birth/death dates
- **`Tournament`**: Tournaments with name, location, dates, type
- **`Annotator`**: Game annotators
- **`Source`**: Game sources (books, magazines, etc.)
- **`Team`**: Teams (for team competitions)
- **`GameTag`**: Custom tags for games

### Entity Indexes

Each entity type has an associated index:
- `PlayerIndex`: Searchable index of players
- `TournamentIndex`: Searchable index of tournaments
- `AnnotatorIndex`, `SourceIndex`, `TeamIndex`, `GameTagIndex`

**Entity Index Transactions:**
- `EntityIndexReadTransaction`: Read-only access to entities
- `EntityIndexWriteTransaction`: Write access to entities

**Key operations:**
- `get(int id)`: Get entity by ID
- `stream()`: Stream all entities
- `stream(EntityFilter filter)`: Stream filtered entities
- `put(T entity)`: Add/update entity (write transaction only)

## Query System

The package includes a powerful query system for searching games:

### GameQuery

`GameQuery` allows building complex queries:

```java
GameQuery query = GameQuery.builder()
    .filter(new PlayerFilter(whitePlayerId))
    .filter(new DateRangeFilter(startDate, endDate))
    .build();
```

**Common filters:**
- `PlayerFilter`: Filter by player
- `DateRangeFilter`: Filter by date range
- `TournamentFilter`: Filter by tournament
- `EcoFilter`: Filter by ECO code
- `ResultFilter`: Filter by game result
- `RatingFilter`: Filter by rating range

### EntityQuery

`EntityQuery` for searching entities:

```java
EntityQuery<Player> query = EntityQuery.builder(Player.class)
    .filter(new PlayerNameFilter("Kasparov"))
    .build();
```

## Game Storage

### GameHeader and ExtendedGameHeader

- **`GameHeader`**: Core game metadata (players, date, result, ECO, move count)
- **`ExtendedGameHeader`**: Extended metadata (ratings, teams, timestamps, game version)

### MoveRepository and AnnotationRepository

- **`MoveRepository`**: Stores and retrieves encoded move data
- **`AnnotationRepository`**: Stores and retrieves encoded annotation data

### GameAdapter

`GameAdapter` converts between database format and `GameModel`:
- `getGameModel(Game game)`: Convert database game to `GameModel`
- `getGameHeaderModel(Game game)`: Convert to header model
- `getTextModel(Game game)`: Convert to text model

## Advanced Features

### Boosters

Search performance boosters:
- **`GameEntityIndex`**: Indexes for fast entity-based searches
- **`GameEventStorage`**: Storage for game events

### Text Support

- **`TextModel`**: Represents guiding texts and text annotations
- **`TextContentsModel`**: Content of text entries

### Validation

- Database validation tools
- Consistency checking
- Repair capabilities

## Usage Examples

### Reading Games

```java
try (Database db = Database.open(new File("database.cbh"))) {
    try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
        // Get a specific game
        Game game = txn.getGame(1);
        GameModel model = game.getModel();

        // Iterate over all games
        for (Game g : txn.iterable()) {
            System.out.println("Game " + g.id() + ": " + g.white().fullName() +
                             " vs " + g.black().fullName());
        }

        // Stream games
        txn.stream()
            .filter(g -> g.whiteElo() > 2500)
            .forEach(g -> System.out.println(g.id()));
    }
}
```

### Writing Games

```java
try (Database db = Database.open(new File("database.cbh"))) {
    try (DatabaseWriteTransaction txn = db.beginWriteTransaction()) {
        GameModel game = new GameModel();
        // ... populate game ...

        int gameId = txn.addGame(game);
        txn.commit();
    }
}
```

### Querying Games

```java
try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
    GameQuery query = GameQuery.builder()
        .filter(new PlayerFilter(playerId))
        .filter(new DateRangeFilter(startDate, endDate))
        .build();

    for (Game game : txn.iterable(query)) {
        // Process matching games
    }
}
```

### Working with Entities

```java
try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
    // Get player
    Player player = txn.getPlayer(playerId);

    // Search players
    EntityIndexReadTransaction<Player> playerTxn = txn.playerTransaction();
    playerTxn.stream()
        .filter(p -> p.lastName().startsWith("Kas"))
        .forEach(p -> System.out.println(p.fullName()));
}
```

## Design Principles

- **Transactions**: All database operations use transactions for consistency
- **Immutability**: Entity objects are immutable; updates create new instances
- **Type Safety**: Strong typing throughout the API
- **Performance**: Efficient storage and retrieval mechanisms
- **Extensibility**: Query system allows custom filters and operations

