# Game Format

This document describes how chess games in Morphy are represented across different abstraction layers, from low-level storage to frontend display, and how data is converted between these layers.

## Table of Contents

1. [Overview](#overview)
2. [Storage Layer](#storage-layer)
3. [Logic Layer](#logic-layer)
4. [Frontend Layer](#frontend-layer)
5. [Layer Summary](#layer-summary)
6. [Annotation Mapping](#annotation-mapping)
7. [Header Mapping](#header-mapping)

---

## Overview

A chess game consists of three main components:

- **Game Header (metadata)**: Player names, location, date, event, result, ratings, etc.
- **Moves**: The sequence of moves in the game, including variations
- **Annotations**: Move evaluations, text comments (before or after moves), graphical annotations (colored squares or arrows), line evaluations, and more

Morphy represents games at three distinct layers, each serving a different purpose:

| Layer | Representation | Purpose |
|-------|----------------|---------|
| **Storage** | `Game` class | Low-level database storage (CBH format) |
| **Logic/Backend** | `GameModel` class | Convenient working representation |
| **Frontend/PGN** | `Chess` class | UI display and PGN interchange |

---

## Storage Layer

At the storage layer (the CBH file format), a game is stored across multiple files:

- **Header information** in the `.cbh` file
- **Move data** (including variations) in the `.cbg` file
- **Annotation data** in the `.cba` file

The exact binary format details can be found in the [ChessBase File Format documentation](cbh-format/README.md).

### Storage Representation

The `Game` class represents a game at the storage level, providing helper methods to access various metadata fields.

**Note:** Not all "games" at the storage layer are actually chess games. There are also "guiding texts" (text documents), also represented by the `Game` class. This special case is ignored in the rest of this document.

---

## Logic Layer

The logic layer (backend) provides a more convenient representation for working with chess games in application code.

### Conversion to Logic Layer

The `GameAdapter` class converts a storage-level `Game` into a `GameModel`, a more convenient container for working with chess games.

**Important:** Not all details from the storage layer are represented at the logic layer, so some information may be lost during conversion.

### GameModel Structure

```
GameModel
├── GameHeaderModel     (metadata: players, event, result, etc.)
└── GameMovesModel      (move tree with variations and annotations)
```

---

## Frontend Layer

The frontend layer (not yet implemented) will use the `@jackstenglein/chess` library to represent chess games in the UI. This library is an extension of the popular `chess.js` library, with support for additional features such as variations and common annotation types.

### PGN Format

Data is passed between the backend and frontend using the **PGN (Portable Game Notation)** format.

Since PGN is a pure string-based format, a key challenge is ensuring that information in the `GameModel` can be correctly represented when converting back and forth without data loss.

### Conversion Tools

- **Backend → PGN**: `PgnExporter` class
- **PGN → Backend**: `PgnParser` class

---

## Layer Summary

The data flow between layers:

```
┌─────────────────────────────────────────────────────────────┐
│                     Storage Layer                           │
│                     Game class                              │
│  (.cbh, .cbg, .cba files)                                   │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ GameAdapter
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                   Logic/Backend Layer                       │
│                    GameModel class                          │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ PgnExporter / PgnParser
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    Frontend Layer                           │
│                     Chess class                             │
│  (PGN format for interchange)                               │
└─────────────────────────────────────────────────────────────┘
```

### Class and Converter Reference

| Layer | Class | Converter |
|-------|-------|-----------|
| Storage | `Game` | `GameAdapter` ↓ |
| Backend | `GameModel` | `PgnExporter` / `PgnParser` ↕ |
| Frontend | `Chess` | - |

---

## Annotation Mapping

All annotations at the logic level inherit from the `Annotation` class in the `se.yarin.chess.annotations` package.

### Annotation Types

There are two categories of annotations:

1. **Storage-layer specific annotations** (`se.yarin.morphy.annotations` package)
2. **Generic annotations** (`se.yarin.chess.annotations` package)

These categories partially overlap in functionality, making the conversion somewhat complex.

### Conversion Behavior

| Conversion Path | Annotations Used |
|-----------------|------------------|
| Storage → GameModel | Storage-layer specific annotations |
| PGN → GameModel | Generic annotations |
| GameModel → Storage | **Requires conversion** from generic to storage-layer annotations (not yet implemented) |

### Example: NAG Annotations

**Storage layer preference:**
- `SymbolAnnotation` can store up to three NAGs at the same move (of different types, see `se.yarin.chess.NAG`)

**Generic annotation:**
- `NAGAnnotation` stores a single NAG

### Text Annotation Handling

Most storage-layer annotations are represented as text in PGN format. When converting from PGN back to `GameModel`:

1. Text is parsed into the generic `CommentaryAfterMoveAnnotation`
2. These text annotations must be parsed and split into specific storage annotation types
3. This conversion is necessary before writing to the database

---

## Header Mapping

**TODO**: Document the mapping between PGN header tags and `GameHeaderModel` / storage header fields.

---

## See Also

- [Architecture Overview](ARCHITECTURE.md) - System design and layer architecture
- [Database Reference](DATABASE-REFERENCE.md) - Database entities and game headers
- [CBH Format Documentation](cbh-format/README.md) - Binary file format specification