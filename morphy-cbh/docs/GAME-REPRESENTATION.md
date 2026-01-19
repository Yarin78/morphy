# Game Representation and Data Flow

This document explains how chess games are represented in Morphy across three distinct abstraction layers, how data flows between these layers, and the architectural decisions behind this design.

## Table of Contents

1. [Overview](#overview)
2. [The Three-Layer Architecture](#the-three-layer-architecture)
3. [Storage Layer: Game Class](#storage-layer-game-class)
4. [Logic Layer: GameModel](#logic-layer-gamemodel)
5. [Frontend Layer: PGN Format](#frontend-layer-pgn-format)
6. [The Bridge: GameAdapter](#the-bridge-gameadapter)
7. [PGN Conversion](#pgn-conversion)
8. [Annotation Architecture](#annotation-architecture)
9. [Data Flow Examples](#data-flow-examples)
10. [Design Rationale](#design-rationale)

---

## Overview

### What is a Chess Game?

A chess game in Morphy consists of three fundamental components:

1. **Metadata** - Player names, ratings, event details, date, result, opening classification
2. **Move sequence** - The main game line plus any alternative variations
3. **Annotations** - Move evaluations (!?, ??), text comments, computer analysis, graphical elements (arrows, colored squares), embedded media

### Why Multiple Layers?

Morphy separates game representation into three distinct layers to balance competing concerns:

| Layer | Purpose | Optimized For |
|-------|---------|---------------|
| **Storage** | Persistent database storage | Disk efficiency, ChessBase compatibility |
| **Logic** | Application business logic | Developer ergonomics, type safety, mutability |
| **Frontend** | User interface and exchange | Standardization (PGN), interoperability |

This separation allows each layer to use the most appropriate representation without compromising the others.

---

## The Three-Layer Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Storage Layer                            │
│                                                             │
│  Game, GameHeader, ExtendedGameHeader                       │
│  • References to binary blobs in .cbg/.cba files            │
│  • Entity IDs (player, tournament, annotator references)    │
│  • Optimized for disk storage and ChessBase format          │
│                                                             │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ GameAdapter.getGameModel()
                       │ GameAdapter.setGameData()
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    Logic Layer                              │
│                                                             │
│  GameModel { GameHeaderModel, GameMovesModel }              │
│  • Fully resolved entities (Player, Tournament objects)     │
│  • Mutable models with change listeners                     │
│  • Tree structure with typed annotations                    │
│  • Optimized for application logic                          │
│                                                             │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ PgnExporter.exportGame()
                       │ PgnParser.parseGame()
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    Frontend Layer                           │
│                                                             │
│  PGN Format (String) → Chess.js library                     │
│  • Standard Portable Game Notation                          │
│  • Text-based, human-readable                               │
│  • Optimized for interchange and UI display                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Storage Layer: Game Class

**Package:** `se.yarin.morphy.games`
**Key Classes:** `Game`, `GameHeader`, `ExtendedGameHeader`

### Purpose

The `Game` class represents a chess game as stored in a ChessBase database. It's a lightweight reference object that provides access to game data without loading everything into memory.

### Structure

The storage layer splits game data across multiple files:

| File | Extension | Contents |
|------|-----------|----------|
| Game headers | `.cbh` | Fixed-size metadata records (32 bytes each) |
| Extended headers | `.cbj` | Additional metadata (teams, timestamps, final material) |
| Moves | `.cbg` | Variable-length encoded move sequences with variations |
| Annotations | `.cba` | Encoded annotations (comments, symbols, graphics, etc.) |

### Game Class Responsibilities

```java
public class Game {
    private final GameHeader header;              // Core metadata
    private final ExtendedGameHeader extHeader;   // Extended metadata

    // Entity resolution (lazy-loaded)
    public Player white();
    public Player black();
    public Tournament tournament();
    public Annotator annotator();

    // Binary data access
    public ByteBuffer getMovesBlob();
    public ByteBuffer getAnnotationsBlob();

    // Convert to logic layer
    public GameModel getModel();
}
```

### Special Case: Guiding Texts

Not all "games" are chess games. ChessBase databases can store **guiding texts** (narrative documents) that use the same storage structures but contain formatted text instead of moves. The `Game` class represents both:

```java
if (game.isGuidingText()) {
    TextModel text = game.getTextModel();  // Not a chess game
} else {
    GameModel model = game.getModel();     // Chess game
}
```

---

## Logic Layer: GameModel

**Package:** `se.yarin.chess`
**Key Classes:** `GameModel`, `GameHeaderModel`, `GameMovesModel`

### Purpose

The logic layer provides a **developer-friendly, type-safe, mutable** representation for working with chess games in application code. This is the primary API for reading, modifying, and analyzing games.

### GameModel Structure

```java
public class GameModel {
    private final GameHeaderModel header;   // Metadata
    private final GameMovesModel moves;     // Move tree

    // Models are final references but internally mutable
    public GameHeaderModel header() { return header; }
    public GameMovesModel moves() { return moves; }
}
```

The two models are **independent and final** to ensure listener registration remains valid.

### GameHeaderModel: Type-Safe Metadata

Instead of raw field access, `GameHeaderModel` provides **strongly-typed getters and setters**:

```java
// Player information
public String white();
public void setWhite(String name);
public int whiteElo();
public void setWhiteElo(int elo);
public String whiteTeam();

// Event information
public String event();
public void setEvent(String eventName);
public Date date();
public String eventSite();
public Nation eventCountry();
public TournamentType eventType();
public TournamentTimeControl eventTimeControl();

// Result and classification
public GameResult result();
public ECO eco();
public NAG lineEvaluation();  // For analysis lines

// Source information
public String annotator();
public String sourceTitle();
```

### Custom Fields

Beyond standard PGN fields, the header supports **arbitrary custom fields**:

```java
headerModel.setField("CustomTag", "value");
String value = headerModel.getField("CustomTag");
```

This mechanism also stores internal IDs for database operations:

```java
// Internal field names (not user-visible)
"DATABASE_ID", "WHITE_ID", "BLACK_ID", "EVENT_ID",
"ANNOTATOR_ID", "SOURCE_ID", "WHITE_TEAM_ID",
"BLACK_TEAM_ID", "GAME_TAG_ID"
```

### GameMovesModel: The Move Tree

Chess games aren't linear sequences—they're **trees** that include variations and alternative lines. The `GameMovesModel` represents this structure:

```
Root (starting position)
  │
  ├─ 1.e4 (main line)
  │   │
  │   ├─ 1...e5 (main continuation)
  │   │   │
  │   │   ├─ 2.Nf3 (main line)
  │   │   │
  │   │   └─ 2.Bc4 (variation)
  │   │
  │   └─ 1...c5 (Sicilian variation)
  │
  └─ 1.d4 (alternative first move)
```

### Node Structure

Each position in the tree is represented by a `Node`:

```java
public class Node {
    private Position position;           // Board state at this point
    private Move lastMove;              // Move that led here (null for root)
    private List<Node> children;        // Continuations (first = main line)
    private Annotations annotations;    // Comments, symbols, graphics, etc.

    public boolean isMainLine();        // First child of parent
    public Node parent();               // Navigate up the tree
}
```

### Key Operations

```java
GameMovesModel moves = gameModel.moves();

// Traverse all positions
List<Node> allNodes = moves.getAllNodes();

// Count moves
int halfMoves = moves.countPly(includeVariations: true);
int mainLineOnly = moves.countPly(includeVariations: false);

// Count annotations
int totalAnnotations = moves.countAnnotations();

// Setup positions (non-standard starting position)
if (moves.isSetupPosition()) {
    Position start = moves.root().position();
    int startMoveNumber = moves.getStartMoveNumber();
}

// Bulk operations
moves.deleteAllAnnotations();
moves.deleteAllVariations();
```

### Mutability and Listeners

Models are **mutable** and support **change notification**:

```java
headerModel.addListener(new GameHeaderModelChangeListener() {
    @Override
    public void changed(String fieldName) {
        // React to changes
    }
});

// Modifications trigger listener
headerModel.setWhite("Carlsen, Magnus");  // Listener notified
```

**Important:** After modifying the tree structure (adding/removing nodes), existing `Node` references may become invalid.

---

## Frontend Layer: PGN Format

**Standard:** [Portable Game Notation (PGN)](https://en.wikipedia.org/wiki/Portable_Game_Notation)

### Purpose

The frontend layer uses **PGN**, a text-based standard for representing chess games. PGN serves two purposes:

1. **Interchange format** - Data exchange with other chess software
2. **UI representation** - Frontend library (`@jackstenglein/chess`) works with PGN

### PGN Structure

```pgn
[Event "World Championship"]
[Site "New York, NY USA"]
[Date "1995.11.10"]
[Round "16"]
[White "Kasparov, Garry"]
[Black "Anand, Viswanathan"]
[Result "1-0"]
[WhiteElo "2795"]
[BlackElo "2725"]

1.e4 e5 2.Nf3 Nc6 3.Bb5 {The Ruy Lopez opening} 3...a6
4.Ba4 Nf6 5.O-O Be7 6.Re1 b5 7.Bb3 d6 8.c3 O-O
9.h3 Nb8 {! A modern retreat} 10.d4 Nbd7 1-0
```

### Challenges with PGN

PGN is **string-based and lossy**, creating conversion challenges:

1. **Type ambiguity** - Everything is text; parsing required to recover types
2. **Information loss** - Many ChessBase features have no PGN equivalent (graphical annotations, media, detailed computer analysis)
3. **Round-trip fidelity** - Converting GameModel → PGN → GameModel may lose data

### Frontend Library (Planned)

The frontend will use **`@jackstenglein/chess`**, an enhanced version of the popular `chess.js` library that adds:

- Variation support
- Common annotation types (!, ?, !!, ??, !?, ?!)
- Better PGN handling

---

## The Bridge: GameAdapter

**Package:** `se.yarin.morphy.games`
**Class:** `GameAdapter`

### Purpose

`GameAdapter` is the **bidirectional bridge** between storage and logic layers. It handles the complex conversions required to move between compact binary storage and convenient mutable models.

### Reading: Storage → Logic

```java
GameModel gameModel = gameAdapter.getGameModel(game);
```

**Process:**

1. **Extract header metadata**
   ```java
   GameHeaderModel headerModel = new GameHeaderModel();

   // Resolve entity references to actual objects
   Player white = game.white();
   headerModel.setWhite(white.fullName());
   headerModel.setWhiteElo(game.whiteElo());

   Tournament tournament = game.tournament();
   headerModel.setEvent(tournament.title());
   headerModel.setEventSite(tournament.place());

   // Store internal IDs as custom fields
   headerModel.setField("WHITE_ID", String.valueOf(game.whitePlayerId()));
   headerModel.setField("EVENT_ID", String.valueOf(game.tournamentId()));
   ```

2. **Deserialize moves from binary blob**
   ```java
   ByteBuffer movesBlob = game.getMovesBlob();
   GameMovesModel movesModel = moveRepository.getMoves(movesBlob, gameId);
   ```

3. **Deserialize annotations and attach to nodes**
   ```java
   ByteBuffer annotationsBlob = game.getAnnotationsBlob();
   annotationRepository.attachAnnotations(movesModel, annotationsBlob);
   ```

4. **Combine and return**
   ```java
   return new GameModel(headerModel, movesModel);
   ```

### Writing: Logic → Storage

```java
gameAdapter.setGameData(headerBuilder, extHeaderBuilder, gameModel);
```

**Process:**

1. **Extract metadata**
   ```java
   header.setPlayedDate(headerModel.date());
   header.setResult(headerModel.result());
   header.setWhiteElo(headerModel.whiteElo());
   header.setBlackElo(headerModel.blackElo());
   header.setEco(headerModel.eco());
   ```

2. **Compute annotation statistics**
   ```java
   AnnotationStatistics stats = new AnnotationStatistics();

   for (Node node : gameModel.moves().getAllNodes()) {
       for (Annotation ann : node.annotations()) {
           if (ann instanceof StatisticalAnnotation) {
               ((StatisticalAnnotation) ann).updateStatistics(stats);
           }
       }
   }

   // Set magnitude fields for UI filtering
   header.setCommentariesMagnitude(stats.getCommentariesMagnitude());
   header.setSymbolsMagnitude(stats.getSymbolsMagnitude());
   header.setGraphicalSquaresMagnitude(stats.getGraphicalSquaresMagnitude());
   ```

3. **Set flags based on game content**
   ```java
   Set<GameHeaderFlags> flags = EnumSet.noneOf(GameHeaderFlags.class);

   if (hasVariations) flags.add(GameHeaderFlags.VARIATIONS);
   if (hasSetupPosition) flags.add(GameHeaderFlags.SETUP_POSITION);
   if (hasComments) flags.add(GameHeaderFlags.COMMENTARY);
   if (hasSymbols) flags.add(GameHeaderFlags.SYMBOLS);
   if (hasGraphics) flags.add(GameHeaderFlags.GRAPHICAL_SQUARES);

   header.setFlags(flags);
   ```

**Note:** Entity resolution (finding or creating Player, Tournament, etc.) happens in the transaction layer, not in `GameAdapter`.

---

## PGN Conversion

### Parsing: PGN → GameModel

**Class:** `se.yarin.chess.pgn.PgnParser`

The parser converts PGN text to `GameModel` in several steps:
1. Parse headers (Seven Tag Roster + optional tags)
2. Handle setup positions (FEN if present)
3. Parse move text including variations `( ... )`, comments `{ ... }`, and NAGs `!`, `?`, `$1`, etc.
4. Create annotations: comments become `CommentaryAnnotation`, NAGs become `NAGAnnotation`
5. Preserve square bracket notation (`[%csl ...]`, `[%cal ...]`) for graphical annotations

### Exporting: GameModel → PGN

**Class:** `se.yarin.chess.pgn.PgnExporter`

The exporter supports configurable formatting via `PgnFormatOptions`:
- Line wrapping (default 79 characters)
- Optional vs. required headers
- Include/exclude variations, comments, NAGs
- Symbol format (! vs $1)
- Predefined options: `DEFAULT`, `COMPACT`, `DEFAULT_WITHOUT_PLYCOUNT`

Export process:
1. Write headers (Seven Tag Roster + optional headers like ECO, Elo)
2. Write setup position if non-standard (SetUp + FEN tags)
3. Export move tree recursively with proper formatting:
   - Before-move comments `{ ... }`
   - Move with number if needed
   - After-move annotations (NAGs and comments)
   - Main line continuation (first child)
   - Variations enclosed in parentheses `( ... )`
4. Apply line wrapping at configured length

---

## Annotation Architecture

Annotations are the most complex aspect of the game representation system. Morphy uses a **dual-layer architecture** to balance PGN compatibility with ChessBase's rich feature set.

### Why Two Annotation Systems?

| Concern | Solution |
|---------|----------|
| PGN interoperability | Generic annotations (`NAGAnnotation`, `CommentaryAnnotation`) |
| ChessBase features | Storage annotations (graphics, media, computer eval) |
| Round-trip fidelity | Both extend common `Annotation` base |

### Generic Annotations (Logic Layer)

**Package:** `se.yarin.chess.annotations`

Simple, PGN-compatible annotations:
- **NAGAnnotation** - Numeric Annotation Glyphs (!, ?, !!, ??, +=, etc.)
- **CommentaryBeforeMoveAnnotation** - Text before a move
- **CommentaryAfterMoveAnnotation** - Text after a move

These annotations have a priority system that controls export order in PGN format.

### Storage Annotations (Storage Layer)

**Package:** `se.yarin.morphy.games.annotations`

ChessBase-specific annotations with additional features:
- **SymbolAnnotation** - Compact storage for up to 3 NAGs per move
- **TextBeforeMoveAnnotation/TextAfterMoveAnnotation** - Text with language metadata
- **ComputerEvaluationAnnotation** - Engine analysis (centipawn evaluation, depth)
- **GraphicalSquaresAnnotation/GraphicalArrowsAnnotation** - Visual board markup (green, yellow, red)
- **TimeSpentAnnotation/WhiteClockAnnotation/BlackClockAnnotation** - Time information
- **Media annotations** - Pictures, sounds, videos, web links, piece paths
- **Training annotations** - Critical positions, training questions, pawn structures
- **GameQuotationAnnotation** - References to other games

Each storage annotation has a unique type code (e.g., 0x03, 0x04) and serializer for binary encoding. Unrecognized annotations are preserved as `UnknownAnnotation` for round-trip fidelity.

### Annotation Statistics

The `AnnotationStatistics` class (in `se.yarin.morphy.games.annotations`) aggregates annotation data for game headers, calculating magnitude values used for filtering and display in the database.

### Annotation Conversion: Bridging Generic and Storage Annotations

**Package:** `se.yarin.morphy.games.annotations`
**Class:** `AnnotationConverter`

#### Purpose

The `AnnotationConverter` provides **bidirectional conversion** between generic annotations (used by PGN and the logic layer) and storage annotations (used by the database). This allows seamless round-tripping of games through PGN while preserving ChessBase-specific features.

#### Conversion Direction 1: Generic → Storage

When saving games imported from PGN, generic annotations are automatically converted to storage format using an `AnnotationTransformer`:

```java
// Configure PgnParser with automatic conversion to storage annotations
PgnParser parser = new PgnParser(AnnotationConverter::convertNodeToStorageAnnotations);
GameModel model = parser.parseGame(pgnString);

// Conversions performed at each node:
NAGAnnotation → SymbolAnnotation (with NAG consolidation)
CommentaryAfterMoveAnnotation → TextAfterMoveAnnotation
CommentaryBeforeMoveAnnotation → TextBeforeMoveAnnotation
Graphical encoding → GraphicalSquaresAnnotation/GraphicalArrowsAnnotation
```

**NAG Consolidation:** ChessBase stores up to 3 NAGs per move (move comment, line evaluation, move prefix) in a single `SymbolAnnotation`. The converter groups multiple `NAGAnnotation` objects by type and creates a consolidated annotation.

**Graphical Annotation Parsing:** Text comments containing PGN square bracket notation are parsed:
- `[%csl Ga4,Rb5]` → `GraphicalSquaresAnnotation` (colored squares)
- `[%cal Ge2e4,Rh1h8]` → `GraphicalArrowsAnnotation` (colored arrows)
- Color codes: `G`=Green, `R`=Red, `Y`=Yellow

#### Conversion Direction 2: Storage → Generic

When exporting games to PGN, storage annotations are automatically converted to generic format using an `AnnotationTransformer`:

```java
// Configure PgnExporter with automatic conversion to generic annotations
PgnExporter exporter = new PgnExporter(
    PgnFormatOptions.DEFAULT,
    AnnotationConverter::convertNodeToGenericAnnotations
);
String pgn = exporter.exportGame(gameModel);

// Conversions performed at each node:
SymbolAnnotation → NAGAnnotation (one per NAG type)
TextAfterMoveAnnotation → CommentaryAfterMoveAnnotation (with graphical encoding)
TextBeforeMoveAnnotation → CommentaryBeforeMoveAnnotation
GraphicalSquaresAnnotation → [%csl ...] encoding in text
GraphicalArrowsAnnotation → [%cal ...] encoding in text
```

**Graphical Annotation Encoding:** Graphical annotations are embedded into text comments using PGN square bracket notation, making them exportable to PGN format and compatible with tools like SCID and ChessBase.

#### Automatic Integration

Conversion happens **automatically** through the `AnnotationTransformer` mechanism:

**PGN Import Flow:**
```java
// Parser applies transformer as it builds the game tree
PgnParser parser = new PgnParser(AnnotationConverter::convertNodeToStorageAnnotations);
GameModel model = parser.parseGame(pgnString);
// model now has storage annotations, ready for database save
```

**PGN Export Flow:**
```java
// Exporter applies transformer as it exports each node
PgnExporter exporter = new PgnExporter(options, AnnotationConverter::convertNodeToGenericAnnotations);
String pgn = exporter.exportGame(gameModel);
// PGN contains generic annotations with graphical encoding
```

This ensures that games round-trip correctly between PGN and database formats without losing annotations.

---

## Data Flow Examples

### Loading and Displaying a Game

```
Database (.cbh/.cbg/.cba files)
    ↓ DatabaseReadTransaction.getGame()
Game (storage layer)
    ↓ Game.getModel() via GameAdapter
GameModel (logic layer)
    ↓ PgnExporter.exportGame()
PGN String
    ↓
Frontend
```

**Key classes:** `Database`, `DatabaseReadTransaction`, `Game`, `GameAdapter`, `PgnExporter`

### Importing a PGN Game

```
PGN String
    ↓ PgnParser.parseGame() (with AnnotationTransformer)
GameModel (with storage annotations)
    ↓ DatabaseWriteTransaction.addGame()
    ↓ GameAdapter.setGameData()
Game (storage layer)
    ↓ commit()
Database (.cbh/.cbg/.cba files)
```

**Key classes:** `PgnParser`, `AnnotationConverter`, `DatabaseWriteTransaction`, `GameAdapter`

---

## See Also

- [Architecture Overview](ARCHITECTURE.md) - Overall system design
- [Database Reference](DATABASE-REFERENCE.md) - Entity types and game headers
- [User Guide](USER-GUIDE.md) - How to use the Morphy library
- [Developer Guide](DEVELOPER-GUIDE.md) - Contributing to Morphy
- [CBH Format Documentation](cbh-format/README.md) - Binary file format specification
