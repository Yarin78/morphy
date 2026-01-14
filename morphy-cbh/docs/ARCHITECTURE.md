# Morphy Architecture

This document describes the architecture of the Morphy library, explaining how components are organized, how they interact, and the key design decisions behind the implementation.

## Table of Contents

1. [Overview](#overview)
2. [Layer Architecture](#layer-architecture)
3. [Chess Core Layer](#chess-core-layer)
4. [Database API Layer](#database-api-layer)
5. [Storage Layer](#storage-layer)
6. [Data Flow](#data-flow)
7. [Concurrency Model](#concurrency-model)
8. [File Format Support](#file-format-support)
9. [Design Decisions](#design-decisions)

---

## Overview

Morphy is structured as a layered library with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────────────┐
│                     Application Code                             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Database API (se.yarin.morphy)                 │
│         Transaction-based, type-safe database operations         │
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
┌─────────────────────────┐      ┌─────────────────────────┐
│      Chess Core         │      │     Storage Layer       │
│   (se.yarin.chess)      │      │ (se.yarin.morphy.storage)│
└─────────────────────────┘      └────────────┬────────────┘
                                              │
                                              ▼
                              ┌─────────────────────────┐
                              │    ChessBase Files      │
                              │  (.cbh, .cbg, .cba...)  │
                              └─────────────────────────┘
```

### Key Characteristics

- **Layered design**: Each layer has a specific responsibility
- **Dependency direction**: Higher layers depend on lower layers, never reverse
- **Chess core independence**: Chess logic has no database dependencies

---

## Layer Architecture

### Layer Responsibilities

| Layer | Package | Responsibility |
|-------|---------|----------------|
| **Database API** | `se.yarin.morphy` | Transaction-based database operations, querying, type-safe entities |
| **Chess Core** | `se.yarin.chess` | Chess rules, positions, moves, game models |
| **Storage** | `se.yarin.morphy.storage` | Low-level file I/O, item serialization |
| **Utilities** | `se.yarin.util` | Shared utilities (byte handling, etc.) |

### Package Dependencies

```
se.yarin.morphy
    ├── depends on → se.yarin.chess
    ├── depends on → se.yarin.morphy.storage
    └── depends on → se.yarin.util

se.yarin.chess
    └── depends on → (none - standalone)

se.yarin.morphy.storage
    └── depends on → se.yarin.util
```

---

## Chess Core Layer

The `se.yarin.chess` package provides format-independent chess functionality.

### Class Hierarchy

```
                    ┌─────────────────┐
                    │    Position     │
                    │  (immutable)    │
                    └────────┬────────┘
                             │ creates
                             ▼
                    ┌─────────────────┐
                    │      Move       │
                    │  (from→to+ctx)  │
                    └────────┬────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                       GameModel                             │
│  ┌─────────────────────┐  ┌─────────────────────────────┐   │
│  │  GameHeaderModel    │  │     GameMovesModel          │   │
│  │  - white, black     │  │  ┌─────────────────────┐    │   │
│  │  - event, date      │  │  │       Node          │    │   │
│  │  - result, eco      │  │  │  - position         │    │   │
│  │  - ratings          │  │  │  - lastMove         │    │   │
│  └─────────────────────┘  │  │  - annotations      │    │   │
│                           │  │  - children (tree)  │    │   │
│                           │  └─────────────────────┘    │   │
│                           └─────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Key Design: Immutable Positions

`Position` is immutable. Applying a move creates a new position:

```java
Position pos = Position.start();
Move e4 = new Move(pos, Chess.E2, Chess.E4);
Position newPos = pos.doMove(e4);  // Returns new Position
// pos is unchanged
```

This enables:
- Safe sharing across threads
- Easy undo (keep reference to previous position)
- Zobrist hashing for position comparison

### Key Design: Game Tree Structure

Games are trees, not lists, to support variations:

```
        1.e4
         │
         ▼
        1...e5     ← main line
         │
    ┌────┼────┐
    ▼         ▼
  2.Nf3    2.Bc4   ← variations
    │
    ▼
  2...Nc6
```

Each `Node` contains:
- `position`: Board state at this point
- `lastMove`: Move that led here (null for root)
- `annotations`: Comments, symbols, graphics
- `children`: List of continuation nodes

---

## Database API Layer

The `se.yarin.morphy` package provides the database API.

### Entity System

Entities are immutable value objects generated by the Immutables library:

```
        ┌───────────────────┐
        │      Entity       │ (interface)
        │  - id: int        │
        │  - count: int     │
        │  - firstGameId    │
        └─────────┬─────────┘
                  │
    ┌─────────────┼─────────────┬─────────────┐
    ▼             ▼             ▼             ▼
┌─────────┐ ┌───────────┐ ┌───────────┐ ┌─────────┐
│ Player  │ │Tournament │ │ Annotator │ │ Source  │ ...
└─────────┘ └───────────┘ └───────────┘ └─────────┘
```

Each entity type has:
- **Entity interface**: Defines the data (`Player`, `Tournament`, etc.)
- **EntityIndex**: Stores and retrieves entities
- **EntityIndexTransaction**: Read/write operations

### Query System

```
┌─────────────────────────────────────────────────────────────┐
│                        GameQuery                            │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                    GameFilter[]                     │    │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────────┐   │    │
│  │  │PlayerFilter│ │ DateFilter │ │ RatingFilter   │   │    │
│  │  └────────────┘ └────────────┘ └────────────────┘   │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
                  ┌─────────────────┐
                  │  QueryPlanner   │ → optimizes execution
                  └────────┬────────┘
                           │
                           ▼
                  ┌─────────────────┐
                  │  Query Result   │ → Iterable<Game>
                  └─────────────────┘
```

### Game Reference

`Game` is a lightweight reference, not a full model:

```java
Game game = txn.getGame(1);
// Game contains references, not full data

// Header data is readily available
GameHeader header = game.header();

// Full model requires parsing (lazy loaded)
GameModel model = game.getModel();
```

---

## Storage Layer

Low-level storage abstraction in `se.yarin.morphy.storage`.

### Storage Hierarchy

```
┌─────────────────────────────────────────────────────────────┐
│                     ItemStorage<T>                          │
│                       (interface)                           │
└─────────────────────────┬───────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ FileItemStorage │ │InMemoryItemStor.│ │ItemStorageFilter│
│  (disk-backed)  │ │  (RAM-only)     │ │ (filtered view) │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

### Blob Storage

For variable-length data (moves, annotations):

```
┌─────────────────────────────────────────────────────────────┐
│                      BlobStorage                            │
│                       (interface)                           │
└─────────────────────────┬───────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ FileBlobStorage │ │InMemoryBlobStor.│ │   (others)      │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

---

## Data Flow

### Reading a Game

```
Application
    │
    │ db.beginReadTransaction()
    ▼
DatabaseReadTransaction
    │
    │ txn.getGame(id)
    ▼
GameHeaderIndex.get(id)
    │
    │ returns GameHeader
    ▼
Game (reference created)
    │
    │ game.getModel()
    ▼
┌─────────────────────────────────────────────────────────────┐
│                      GameAdapter                            │
│  1. Get moves blob from MoveRepository                      │
│  2. Deserialize moves using MoveSerializer                  │
│  3. Get annotations blob from AnnotationRepository          │
│  4. Deserialize annotations                                 │
│  5. Build GameMovesModel tree                               │
│  6. Build GameHeaderModel from header + entities            │
│  7. Combine into GameModel                                  │
└─────────────────────────────────────────────────────────────┘
    │
    │ returns GameModel
    ▼
Application
```

### Writing a Game

```
Application
    │
    │ db.beginWriteTransaction()
    ▼
DatabaseWriteTransaction
    │
    │ txn.addGame(GameModel)
    ▼
┌─────────────────────────────────────────────────────────────┐
│                      GameAdapter                             │
│  1. Extract header metadata                                 │
│  2. Resolve entities (find existing or create new)          │
│     - Player resolution by name                             │
│     - Tournament resolution by title + date                 │
│  3. Serialize moves using MoveSerializer                    │
│  4. Serialize annotations                                   │
│  5. Store move blob in MoveRepository                       │
│  6. Store annotation blob in AnnotationRepository           │
│  7. Build GameHeader with entity IDs and offsets            │
└─────────────────────────────────────────────────────────────┘
    │
    │ GameHeaderIndex.add(header)
    ▼
Changes buffered in write transaction
    │
    │ txn.commit()
    ▼
┌─────────────────────────────────────────────────────────────┐
│                   Flush to disk                              │
│  - Write pending headers                                    │
│  - Write pending entity updates                             │
│  - Sync all files                                           │
└─────────────────────────────────────────────────────────────┘
```

---

## Concurrency Model

### Transaction Isolation

```
                    Database Lock
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
    Read Txn 1      Read Txn 2      Write Txn
    (shared)        (shared)        (exclusive)
         │               │               │
         │               │               │
     ┌───┴───────────────┴───┐           │
     │  Can run concurrently │           │
     └───────────────────────┘           │
                                         │
         ┌───────────────────────────────┘
         │
         ▼
    Write waits for all reads to complete before commit
```

### Transaction Rules

1. **Multiple read transactions** can be open concurrently
2. **Only one write transaction** can be open at a time
3. **Write transaction isolation**: Changes not visible to reads until commit
4. **Commit blocking**: Commit waits for all read transactions to close
5. **Not ACID**: Failure during commit may leave database inconsistent

### Thread Safety

```java
// Safe: Multiple threads reading
ExecutorService executor = Executors.newFixedThreadPool(4);
for (int i = 0; i < 4; i++) {
    executor.submit(() -> {
        try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
            // Read operations
        }
    });
}

// Unsafe without synchronization: Writing from multiple threads
// Use a single write transaction or external synchronization
```

---

## File Format Support

### ChessBase Database Files

```
database.cbh     ← Game headers (fixed size records)
         │
         ├──► database.cbg     ← Encoded moves (blob)
         ├──► database.cba     ← Annotations (blob)
         │
         ├──► database.cbp     ← Player index
         ├──► database.cbt     ← Tournament index
         ├──► database.cbc     ← Annotator index
         ├──► database.cbs     ← Source index
         ├──► database.cbe     ← Team index
         ├──► database.cbl     ← Game tags
         │
         ├──► database.cbj     ← Extended headers (optional)
         ├──► database.cbtt    ← Tournament extra data (optional)
         │
         └──► database.cbb     ← Search booster (optional)
              database.cit
              database.cib
              ...
```

### Format Versions

Morphy supports ChessBase databases from various versions:
- ChessBase 6-9: Basic format
- ChessBase 10-13: Extended headers
- ChessBase 14+: Additional indexes

---

## Further Reading

- [User Guide](USER-GUIDE.md) - How to use the library
- [Developer Guide](DEVELOPER-GUIDE.md) - Contributing to Morphy
- [Chess Package](chess-package.md) - Chess core details
- [Morphy Package](morphy-package.md) - Modern API details
- [CBH Format](cbh-format/README.md) - File format specification
