# se.yarin.morphy.games

Game header storage, move/annotation serialization, and ChessBase annotation handling.

## Core Classes

- **GameHeader** - Immutable game header with entity IDs (whitePlayerId, blackPlayerId, tournamentId, annotatorId, sourceId), result, date, ECO, and file offsets (movesOffset, annotationOffset).
- **GameHeaderIndex** - Stores all game headers using ItemStorage. Supports file-based and in-memory modes. Corresponds to `.cbh` files.
- **MoveRepository** - Manages `.cbg` move file using BlobStorage. Handles move encoding mode.
- **AnnotationRepository** - Manages `.cba` annotation file using BlobStorage.
- **ExtendedGameHeader / ExtendedGameHeaderStorage** - Extended attributes from `.cbj` files (ratings, additional metadata).
- **TopGamesStorage** - Tracks top-rated games from `.flags` files for quick access.
- **Medal, EndgameType, RatingType, FinalMaterial** - Enum and value types for game classification.

## Moves (moves/ sub-package)

Move encoding/decoding for ChessBase binary format:
- **MoveSerializer** - Main entry point for serializing/deserializing move trees.
- **CompactMoveEncoder** - Space-efficient encoding used in most databases.
- **SimpleMoveEncoder** - Simpler encoding format.
- **GameQuotationMoveEncoder** - Encoding for embedded game quotations.

## Annotations (annotations/ sub-package)

30+ annotation types representing ChessBase's rich annotation system:
- **AnnotationSerializer** - Binary serialization for all annotation types.
- **AnnotationConverter** - Bidirectional PGN <-> ChessBase annotation conversion.
- **PgnCodecRegistry** - Registry of PGN codecs for annotation types.
- Types include: `TextAfterMoveAnnotation`, `TextBeforeMoveAnnotation`, `ComputerEvaluationAnnotation`, `GraphicalSquaresAnnotation`, `GraphicalArrowsAnnotation`, `WhiteClockAnnotation`, `BlackClockAnnotation`, `MedalAnnotation`, `SymbolAnnotation`, `TrainingAnnotation`, `CriticalPositionAnnotation`, `WebLinkAnnotation`, `GameQuotationAnnotation`, and more.
- `RawAnnotation` and `InvalidAnnotation` for unknown/malformed types.

## Filters (filters/ sub-package)

20+ game filters implementing `GameFilter` interface: `DateRangeFilter`, `EcoFilter`, `RatingRangeFilter`, `MovesRangeFilter`, `MedalFilter`, `ResultFilter`, etc.

## Documentation

- Game representation across layers: `morphy-cbh/docs/GAME-REPRESENTATION.md`
- Annotation text encoding spec: `morphy-cbh/docs/ANNOTATION-TEXT-ENCODING.md`
- Game header field reference: `morphy-cbh/docs/DATABASE-REFERENCE.md`
- File format specs: `morphy-cbh/docs/cbh-format/games.md`, `moves.md`, `annotations.md`
