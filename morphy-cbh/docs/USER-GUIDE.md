# Morphy User Guide

This comprehensive guide covers how to use the Morphy library for working with ChessBase databases and chess data programmatically. This guide is intended for developers integrating Morphy into their applications.

## Table of Contents

1. [Introduction](#introduction)
2. [Getting Started](#getting-started)
3. [Working with Databases](#working-with-databases)
4. [Working with Games](#working-with-games)
5. [Working with Entities](#working-with-entities)
6. [Querying and Searching](#querying-and-searching)
7. [Chess Core Library](#chess-core-library)
8. [Common Use Cases](#common-use-cases)
9. [Best Practices](#best-practices)
10. [Troubleshooting](#troubleshooting)

---

## Introduction

### What is Morphy?

Morphy is a Java library for working with [ChessBase](https://en.chessbase.com/) databases.
It allows you to read, write and query games and entities in the native ChessBase format.

A ChessBase database contains very many files with the same name but different extensions.
Some of these files are critical while some contain search boosters and can be re-created.

### Key Concepts

| Concept | Description                                                             |
|---------|-------------------------------------------------------------------------|
| **Database** | A ChessBase database containing games and entities                      |
| **Game** | A chess game with header metadata, moves, variations, annotations etc.  |
| **Entity** | A database object referenced by games (Player, Tournament, etc.)        |
| **Index** | The physical storage of a specific Entity (e.g. Players are stored in the .cbp file) 
| **Transaction** | A scoped operation ensuring data consistency when carrying out write operations |
| **GameModel** | In-memory representation of a complete game                             |
| **Position** | Immutable representation of a chess board state                         |

---

## Getting Started

### Maven Dependency

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>se.yarin</groupId>
    <artifactId>morphy-cbh</artifactId>
    <version>VERSION</version>
</dependency>
```

### Required Java Version

Morphy requires **Java 11** or later.

### Basic Example

```java
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.games.Game;
import se.yarin.chess.GameModel;

import java.io.File;

public class BasicExample {
    public static void main(String[] args) throws Exception {
        // Open a database
        try (Database db = Database.open(new File("games.cbh"))) {

            // Start a read transaction
            try (DatabaseReadTransaction txn = db.beginReadTransaction()) {

                // Get a specific game
                Game game = txn.getGame(1);
                System.out.println("White: " + game.white().fullName());
                System.out.println("Black: " + game.black().fullName());
                System.out.println("Result: " + game.header().result());

                // Get the full game model with moves
                GameModel model = game.getModel();
                System.out.println("Moves: " + model.moves().countPly(false));
            }
        }
    }
}
```

---

## Working with Databases

### Opening a Database

```java
// Open in read-write mode (default)
Database db = Database.open(new File("database.cbh"));

// Open in read-only mode
Database db = Database.open(new File("database.cbh"), DatabaseMode.READ_ONLY);

// Open entirely in memory (faster reads, but uses more RAM)
Database db = Database.openInMemory(new File("database.cbh"));

// Create a new database
Database db = Database.create(new File("new_database.cbh"));
```

### Database Information

```java
try (Database db = Database.open(new File("database.cbh"))) {
    // Number of games
    int gameCount = db.count();

    // Number of entities
    int playerCount = db.playerIndex().count();
    int tournamentCount = db.tournamentIndex().count();

    // Database context (for advanced operations)
    DatabaseContext context = db.context();
}
```

### Closing Databases

Always close databases when done. Use try-with-resources:

```java
try (Database db = Database.open(new File("database.cbh"))) {
    // Work with database
} // Automatically closed
```

---

## Working with Games

### Reading Games

#### Get a Single Game

```java
try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
    Game game = txn.getGame(1); // Game IDs start at 1

    // Access header information
    GameHeader header = game.header();
    int whiteElo = header.whiteElo();
    // ...

    // Access player entities
    Player white = game.white();
    Player black = game.black();
    Tournament tournament = game.tournament();

    // Get full game model with moves
    GameModel model = game.getModel();
}
```

#### Iterate Over All Games

```java
try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
    for (Game game : txn.iterable()) {
        System.out.println(game.id() + ": " +
            game.white().lastName() + " - " + game.black().lastName());
    }
}
```

#### Stream Games

```java
try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
    // Find all games where White has Elo > 2600
    txn.stream()
        .filter(g -> g.header().whiteElo() > 2600)
        .forEach(g -> System.out.println(g.white().fullName()));
}
```

### Writing Games

#### Add a New Game

```java
try (DatabaseWriteTransaction txn = db.beginWriteTransaction()) {
    // Create a game model
    GameModel game = new GameModel();
    GameHeaderModel header = game.header();

    // Set header information
    header.setWhite("Carlsen, Magnus");
    header.setBlack("Nakamura, Hikaru");
    header.setEvent("Tata Steel 2024");
    header.setDate(new Date(2024, 1, 15));
    header.setResult(GameResult.WHITE_WINS);
    header.setEco(Eco.fromString("B90"));
    header.setWhiteElo(2830);
    header.setBlackElo(2780);

    // Add moves (see Chess Core Library section for details)
    GameMovesModel moves = game.moves();
    // ... add moves ...

    // Add to database
    int newGameId = txn.addGame(game);

    // Commit the transaction
    txn.commit();

    System.out.println("Added game with ID: " + newGameId);
}
```

#### Replace an Existing Game

```java
try (DatabaseWriteTransaction txn = db.beginWriteTransaction()) {
    // Get existing game
    Game existingGame = txn.getGame(gameId);
    GameModel model = existingGame.getModel();

    // Modify the model
    model.header().setResult(GameResult.DRAW);

    // Replace in database
    txn.replaceGame(gameId, model);
    txn.commit();
}
```

#### Delete a Game

```java
try (DatabaseWriteTransaction txn = db.beginWriteTransaction()) {
    txn.deleteGame(gameId);
    txn.commit();
}
```

### Batch Operations

For adding many games, disable auto-commit for better performance:

```java
try (DatabaseWriteTransaction txn = db.beginWriteTransaction()) {
    txn.setAutoCommit(false);

    for (GameModel game : gamesToAdd) {
        txn.addGame(game);
    }

    // Commit all at once
    txn.commit();
}
```

---

## Working with Entities

Entities are database objects referenced by games: Players, Tournaments, Annotators, Sources, Teams, and Game Tags.

Entities can never be added directly to an index, and can only exist if at least one game contains a reference to it.
If a game is deleted or updated so that an Entity is no longer used, it will automatically be deleted from the entity index.

### Entity Types

| Entity Type | Description | Index Class |
|-------------|-------------|-------------|
| `Player` | Chess player (name, nationality, dates) | `PlayerIndex` |
| `Tournament` | Tournament/event information | `TournamentIndex` |
| `Annotator` | Game annotator | `AnnotatorIndex` |
| `Source` | Publication source | `SourceIndex` |
| `Team` | Team (for team events) | `TeamIndex` |
| `GameTag` | Custom game tags | `GameTagIndex` |

### Reading Entities

#### Get Entity by ID

```java
try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
    Player player = txn.getPlayer(playerId);
    Tournament tournament = txn.getTournament(tournamentId);
    Annotator annotator = txn.getAnnotator(annotatorId);
}
```

#### Access Entity Data

```java
Player player = txn.getPlayer(playerId);

// Player properties
String firstName = player.firstName();
String lastName = player.lastName();
String fullName = player.fullName();
Nation nation = player.nation();
Date birthDate = player.birthDate();
int gameCount = player.count();       // Number of games
int firstGameId = player.firstGameId(); // ID of first game

// Tournament properties
Tournament tournament = txn.getTournament(tournamentId);
String title = tournament.title();
Date startDate = tournament.date();
String place = tournament.place();
Nation nation = tournament.nation();
int category = tournament.category();
int rounds = tournament.rounds();
TournamentType type = tournament.type();
TournamentTimeControl timeControl = tournament.timeControl();
```

### Iterating Entities

As the name implies, entities are stored in a index that is sorted by one or more keys.
It allows you to iterate over entities either by _id order_ or _sort order_.

```java
try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
    // Get entity transaction
    EntityIndexReadTransaction<Player> playerTxn = txn.playerTransaction();

    // Iterate all players (in default sort order: by last name)
    for (Player player : playerTxn.iterable()) {
        System.out.println(player.fullName());
    }

    // Stream players
    playerTxn.stream()
        .filter(p -> p.count() > 100)  // Players with 100+ games
        .forEach(p -> System.out.println(p.fullName() + ": " + p.count() + " games"));

    // Iterate by ID order
    for (Player player : playerTxn.iterableAscending()) {
        System.out.println(player.id() + ": " + player.fullName());
    }
}
```

### Updating Entities

New entities can't be added directly, but only via adding a new game.
Metadata about entities can be updated in a database write transaction:

```java
try (DatabaseWriteTransaction txn = db.beginWriteTransaction()) {
    // Get current entity
    Player player = txn.getPlayer(playerId);

    // Create updated version (entities are immutable)
    Player updated = ImmutablePlayer.builder()
        .from(player)
        .nation(Nation.fromIOC("SWE"))
        .build();

    // Update in database
    txn.updatePlayerById(playerId, updated);
    txn.commit();
}
```

### Entity Resolution

When adding games, entities are automatically resolved or created:

```java
// In the GameModel header
header.setWhite("Carlsen, Magnus");  // Will find or create this player
header.setBlack("Nakamura, Hikaru");
header.setEvent("Tata Steel 2024"); // Will find or create this tournament
```

---

## Querying and Filtering

Filters are used to limit iteration and streaming of games and entities based on some criteria.
Think WHERE in a simple SQL-statement.

Queries are used to combine iterables across games and entities. Think JOIN in a SQL-statement. 

TODO: This is still under heavy development.

---

## Chess Core Library

The `se.yarin.chess` package provides chess functionality independent of databases.

### Positions

```java
import se.yarin.chess.Position;
import se.yarin.chess.Chess;
import se.yarin.chess.Move;

// Standard starting position
Position startPos = Position.start();

// Check position state
boolean isCheck = startPos.isCheck();
boolean isCheckmate = startPos.isCheckmate();
boolean isStalemate = startPos.isStalemate();

// Get piece at square
Stone piece = startPos.stoneAt(Chess.E1);  // WHITE_KING

// Generate legal moves
List<Move> legalMoves = startPos.generateAllLegalMoves();
```

### Moves

```java
// Create a move from squares
int fromSqi = Chess.E2;  // Square index for e2
int toSqi = Chess.E4;    // Square index for e4
Move move = new Move(position, fromSqi, toSqi);

// For promotions
Move promotion = new Move(position, Chess.E7, Chess.E8, Piece.QUEEN);

// Apply move to get new position
Position newPosition = position.doMove(move);

// Get move notation
String san = move.toSAN();  // e.g., "e4", "Nf3", "O-O"
String lan = move.toLAN();  // e.g., "e2-e4", "g1-f3"
```

### Square Indices

```java
// Named constants
int e4 = Chess.E4;
int d8 = Chess.D8;

// Convert from coordinates (file 0-7, rank 0-7)
int sqi = Chess.coorToSqi(4, 3);  // e4 (file=4, rank=3)

// Convert to coordinates
int file = Chess.sqiToCol(Chess.E4);  // 4
int rank = Chess.sqiToRow(Chess.E4);  // 3

// Convert to string
String name = Chess.sqiToStr(Chess.E4);  // "e4"
```

### Game Models

```java
// Create a new game
GameModel game = new GameModel();

// Access header
GameHeaderModel header = game.header();
header.setWhite("Player One");
header.setBlack("Player Two");
header.setResult(GameResult.DRAW);

// Access moves
GameMovesModel moves = game.moves();
GameMovesModel.Node root = moves.root();

// Add moves
Position pos = root.position();
Move e4 = new Move(pos, Chess.E2, Chess.E4);
GameMovesModel.Node afterE4 = root.addMove(e4);

pos = afterE4.position();
Move e5 = new Move(pos, Chess.E7, Chess.E5);
GameMovesModel.Node afterE5 = afterE4.addMove(e5);

// Add a variation
Move c5 = new Move(afterE4.position(), Chess.C7, Chess.C5);  // Sicilian
GameMovesModel.Node sicilian = afterE4.addMove(c5);  // Creates variation
```

### Navigating Game Trees

```java
GameMovesModel moves = game.moves();
GameMovesModel.Node node = moves.root();

// Navigate forward
while (node.hasMainLine()) {
    node = node.mainLine();
    System.out.println(node.lastMove().toSAN());
}

// Navigate with variations
for (GameMovesModel.Node variation : node.variations()) {
    System.out.println("Variation: " + variation.lastMove().toSAN());
}

// Count plies
int plyCount = moves.countPly(false);  // Main line only
int totalPly = moves.countPly(true);   // Including variations
```

### Annotations

```java
import se.yarin.chess.annotations.*;

// Add text annotation
node.addAnnotation(new TextAfterMoveAnnotation("Excellent move!"));

// Add NAG (Numeric Annotation Glyph)
node.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));  // !
node.addAnnotation(new NAGAnnotation(NAG.DUBIOUS_MOVE));  // ?!

// Get annotations
Annotations annotations = node.annotations();
for (Annotation ann : annotations) {
    if (ann instanceof TextAfterMoveAnnotation) {
        String text = ((TextAfterMoveAnnotation) ann).getText();
    }
}
```

### Chess960

```java
import se.yarin.chess.Chess960;

// Get starting position for a specific position number (0-959)
Position chess960Start = Chess960.getStartPosition(518);

// Get position number from a position
int positionNumber = Chess960.getStartPositionNumber(position);
```

---

## Common Use Cases

### Export Games to PGN

```java
try (Database db = Database.open(new File("database.cbh"))) {
    try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
        for (Game game : txn.iterable()) {
            GameModel model = game.getModel();

            // Build PGN string
            StringBuilder pgn = new StringBuilder();
            pgn.append("[White \"").append(game.white().fullName()).append("\"]\n");
            pgn.append("[Black \"").append(game.black().fullName()).append("\"]\n");
            pgn.append("[Result \"").append(game.header().result()).append("\"]\n");
            pgn.append("[Date \"").append(game.header().playedDate()).append("\"]\n\n");
            pgn.append(model.moves().toSAN()).append("\n\n");

            System.out.println(pgn);
        }
    }
}
```

### Find All Games by a Player

```java
try (Database db = Database.open(new File("database.cbh"))) {
    try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
        // Find player by name
        EntityIndexReadTransaction<Player> playerTxn = txn.playerTransaction();
        Player player = playerTxn.stream()
            .filter(p -> p.lastName().equals("Carlsen") && p.firstName().equals("Magnus"))
            .findFirst()
            .orElseThrow();

        // Get all games
        PlayerFilter filter = new PlayerFilter(player.id());
        int wins = 0, losses = 0, draws = 0;

        for (Game game : txn.iterable(filter)) {
            boolean isWhite = game.header().whitePlayerId() == player.id();
            GameResult result = game.header().result();

            if (result == GameResult.WHITE_WINS) {
                if (isWhite) wins++; else losses++;
            } else if (result == GameResult.BLACK_WINS) {
                if (isWhite) losses++; else wins++;
            } else if (result == GameResult.DRAW) {
                draws++;
            }
        }

        System.out.println(player.fullName() + ": +" + wins + " =" + draws + " -" + losses);
    }
}
```

### Analyze Opening Statistics

```java
try (Database db = Database.open(new File("database.cbh"))) {
    try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
        Map<String, int[]> ecoStats = new HashMap<>(); // [whiteWins, draws, blackWins]

        for (Game game : txn.iterable()) {
            String eco = game.header().eco().toString();
            int[] stats = ecoStats.computeIfAbsent(eco, k -> new int[3]);

            switch (game.header().result()) {
                case WHITE_WINS -> stats[0]++;
                case DRAW -> stats[1]++;
                case BLACK_WINS -> stats[2]++;
            }
        }

        // Print ECO statistics
        ecoStats.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> {
                int[] s = e.getValue();
                int total = s[0] + s[1] + s[2];
                System.out.printf("%s: %d games (%.1f%% white, %.1f%% draw, %.1f%% black)%n",
                    e.getKey(), total,
                    100.0 * s[0] / total,
                    100.0 * s[1] / total,
                    100.0 * s[2] / total);
            });
    }
}
```

### Merge Databases

```java
try (Database source = Database.open(new File("source.cbh"))) {
    try (Database target = Database.open(new File("target.cbh"))) {
        try (DatabaseReadTransaction srcTxn = source.beginReadTransaction()) {
            try (DatabaseWriteTransaction tgtTxn = target.beginWriteTransaction()) {
                tgtTxn.setAutoCommit(false);

                for (Game game : srcTxn.iterable()) {
                    GameModel model = game.getModel();
                    tgtTxn.addGame(model);
                }

                tgtTxn.commit();
            }
        }
    }
}
```

---

## Best Practices

### Transaction Management

1. **Always use try-with-resources** to ensure transactions are closed:
   ```java
   try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
       // Work with transaction
   } // Automatically closed
   ```

2. **Keep transactions short-lived** to avoid blocking other operations.

3. **Use read transactions for read-only operations** - they allow concurrent access.

4. **Batch writes** by disabling auto-commit when adding many games.

### Memory Management

1. **Close databases** when done to release file handles.

2. **Use streaming** for large result sets instead of loading all into memory:
   ```java
   // Good - streaming
   txn.stream().filter(...).forEach(...);

   // Avoid - loading all into list
   List<Game> all = txn.stream().collect(toList());
   ```

3. **Consider `openInMemory()`** for read-heavy workloads on smaller databases.

### Error Handling

1. **Handle database corruption** gracefully:
   ```java
   try {
       Database db = Database.open(file);
   } catch (DatabaseException e) {
       // Handle corruption, offer to repair
   }
   ```

2. **Validate before committing** large batch operations.

### Performance Tips

1. **Use filters** instead of loading all games and filtering in Java.

2. **Access only needed data** - don't call `getModel()` if you only need header info.

3. **Use entity indexes** for entity lookups instead of iterating games.

---

## Database Files

A ChessBase database consists of multiple files:

| Extension | Description |
|-----------|-------------|
| `.cbh` | Main file (game headers) |
| `.cbg` | Encoded moves |
| `.cba` | Annotations |
| `.cbp` | Player index |
| `.cbt` | Tournament index |
| `.cbc` | Annotator index |
| `.cbs` | Source index |
| `.cbe` | Team index |
| `.cbl` | Game tags |
| `.cbj` | Extended game headers |
| `.cbtt` | Tournament extra data |

Ensure all files are present when opening a database.

---

## Further Reading

- [Database Reference](DATABASE-REFERENCE.md) - Complete reference of all game header fields and entities
- [Architecture Overview](ARCHITECTURE.md) - System design and component interactions
- [Developer Guide](DEVELOPER-GUIDE.md) - For contributors to the Morphy library
- [Chess Package](chess-package.md) - Detailed `se.yarin.chess` documentation
- [Morphy Package](morphy-package.md) - Detailed `se.yarin.morphy` documentation
- [CBH Format](cbh-format/README.md) - ChessBase file format specification (low-level binary format)
