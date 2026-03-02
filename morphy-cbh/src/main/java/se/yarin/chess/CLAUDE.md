# se.yarin.chess

Format-independent chess logic. This package has no dependency on the database layer.

## Key Classes

- **Position** - Immutable board state using 64-element Stone array with Zobrist hashing (128-bit, lo/hi). `doMove()` returns a new Position. Caches legal moves and check status. Supports Chess960.
- **Move** - Complete move representation (from/to squares, promotion, castling). Caches SAN notation.
- **GameModel** - Container holding `GameHeaderModel` + `GameMovesModel`. Provides `replaceAll()` and `reset()`.
- **GameMovesModel** - Mutable move tree with node-based structure supporting variations.
- **NavigableGameModel** - Extends GameModel with cursor navigation for interactive use.
- **Stone, Piece, Player** - Enums for chess primitives.
- **Eco** - ECO classification codes.
- **NAG** - Numeric Annotation Glyphs with `NAGType` categories.
- **Date** - Chess date representation (year/month/day, any component may be unknown).

## Sub-packages

- **annotations/** - Annotation types attached to moves: `CommentaryAfterMoveAnnotation`, `CommentaryBeforeMoveAnnotation`, `NAGAnnotation`, etc.
- **timeline/** - Event-based undo/redo system: `AddMoveEvent`, `DeleteVariationEvent`, etc. Implements game editing history.
- **pgn/** - PGN parser and exporter: `PgnLexer` (lexer), `PgnParser` (parser), `PgnGameBuilder` (AST to GameModel).

## Design Decisions

- Uses Stone arrays (not bitboards) for board representation - simpler, sufficient for this use case.
- Position immutability enforced: `doMove()` always creates new instance.
- GameMovesModel is mutable (tree editing) while Position/Move are immutable.
