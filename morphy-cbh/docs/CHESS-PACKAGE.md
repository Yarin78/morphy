# The `se.yarin.chess` Package

The `se.yarin.chess` package provides core chess functionality for representing and manipulating chess games, positions, and moves. This package is independent of any database format and can be used as a standalone chess library.

## Overview

This package contains the fundamental building blocks for working with chess:
- **Positions**: Represent chess board states
- **Moves**: Represent chess moves with full context
- **Game Models**: Represent complete chess games with moves and annotations
- **Chess Logic**: Move generation, validation, and notation

## Core Classes

### Position

`Position` represents an immutable chess position, including:
- Piece placement on the board
- Side to move
- Castling rights
- En passant file
- Chess960 support

Key capabilities:
- Generate all legal moves from a position
- Check if a position is check, checkmate, or stalemate
- Apply moves to create new positions
- Zobrist hashing for position comparison

### Move

`Move` represents a complete chess move with enough information to:
- Generate Standard Algebraic Notation (SAN) and Long Algebraic Notation (LAN)
- Determine if a move is a capture, check, or checkmate
- Handle promotions, castling, and en passant
- Support Chess960 castling

Moves are created from a source position and contain the from/to squares and promotion piece (if applicable).

### GameModel

`GameModel` combines a `GameHeaderModel` and `GameMovesModel` to represent a complete chess game:
- **GameHeaderModel**: Contains metadata like players, tournament, date, result, ECO code
- **GameMovesModel**: Contains the game tree with moves, variations, and annotations

### GameMovesModel

`GameMovesModel` represents the move tree of a chess game:
- Tree structure where each `Node` represents a position
- Supports variations (alternative move sequences)
- Each node can have annotations (comments, symbols, etc.)
- Provides methods to navigate, modify, and traverse the game tree

Key operations:
- Add moves and variations
- Promote variations to main line
- Delete moves or variations
- Traverse the tree depth-first
- Convert to SAN notation

### NavigableGameModel

`NavigableGameModel` extends `GameModel` with navigation capabilities:
- Cursor-based navigation through the game
- Undo/redo functionality
- Timeline-based change tracking
- Event listeners for model changes

## Supporting Classes

### Piece, Stone, Player

- **`Piece`**: Enum representing piece types (PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING)
- **`Stone`**: Represents a colored piece (e.g., WHITE_PAWN, BLACK_KING)
- **`Player`**: Enum for WHITE, BLACK, or NOBODY

### Chess and Chess960

- **`Chess`**: Utility class with constants and helper methods for chess operations
- **`Chess960`**: Support for Fischer Random Chess (Chess960) starting positions

### Annotations

The `se.yarin.chess.annotations` subpackage provides annotation support:
- **`Annotation`**: Base class for all annotations
- **`NAGAnnotation`**: Numeric Annotation Glyphs (e.g., !, ?, !?)
- **`TextBeforeMoveAnnotation`**: Comments before a move
- **`TextAfterMoveAnnotation`**: Comments after a move
- **`Annotations`**: Collection of annotations at a position

### Other Utilities

- **`Date`**: Represents chess dates (year, month, day) with support for partial dates
- **`Eco`**: Opening classification codes (e.g., "B90" for Sicilian Najdorf)
- **`GameResult`**: Enum for game outcomes (WHITE_WINS, BLACK_WINS, DRAW, NOT_FINISHED)
- **`NAG`**: Numeric Annotation Glyph constants
- **`ShortMove`**: Compact move representation (from/to squares only)

## Usage Examples

### Creating a Position

```java
Position startPos = Position.start(); // Standard starting position
Position customPos = new Position(board, whiteKingSqi, blackKingSqi,
                                  Player.WHITE, castlesMask, enPassantCol, chess960sp);
```

### Making Moves

```java
Position pos = Position.start();
Move move = new Move(pos, Chess.coorToSqi(4, 1), Chess.coorToSqi(4, 3)); // e4
Position newPos = pos.doMove(move);
```

### Working with Game Models

```java
GameModel game = new GameModel();
GameMovesModel moves = game.moves();
GameMovesModel.Node root = moves.root();

// Add moves
root.addMove(new Move(root.position(), Chess.coorToSqi(4, 1), Chess.coorToSqi(4, 3))); // e4
root.addMove(new Move(root.position(), Chess.coorToSqi(4, 6), Chess.coorToSqi(4, 4))); // e5

// Add annotation
root.addAnnotation(new TextBeforeMoveAnnotation("King's Pawn Opening"));
```

### Generating Legal Moves

```java
Position pos = Position.start();
List<Move> legalMoves = pos.generateAllLegalMoves();
for (Move move : legalMoves) {
    System.out.println(move.toSAN());
}
```

## Design Principles

- **Immutability**: Positions and moves are immutable; operations return new instances
- **Type Safety**: Strong typing prevents common chess programming errors
- **Completeness**: All information needed for notation and validation is included
- **Performance**: Efficient move generation and position hashing
- **Extensibility**: Annotation system allows custom annotation types

