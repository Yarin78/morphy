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
9. [Complete Data Flow Examples](#complete-data-flow-examples)
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

### Offset Management

The `Game` class handles a subtle complexity: **32-bit offset overflow** in large databases.

- `GameHeader` stores 32-bit offsets (legacy format)
- `ExtendedGameHeader` stores 64-bit offsets (newer format)
- For databases > 4GB, the extended header offset is authoritative

```java
public long getMovesOffset() {
    // Use extended header if available and different
    if (extendedHeader != null &&
        extendedHeader.movesOffset() != header.movesOffset()) {
        return extendedHeader.movesOffset();
    }
    return header.movesOffset() & 0xFFFFFFFFL;  // Unsigned 32-bit
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

```java
GameModel game = PgnParser.parseGame(pgnString);
```

**Process:**

1. **Parse headers**
   ```java
   GameHeaderModel header = new GameHeaderModel();

   // Required Seven Tag Roster
   header.setEvent(tags.get("Event"));
   header.setSite(tags.get("Site"));
   header.setDate(Date.parse(tags.get("Date")));
   header.setRound(tags.get("Round"));
   header.setWhite(tags.get("White"));
   header.setBlack(tags.get("Black"));
   header.setResult(GameResult.parse(tags.get("Result")));

   // Optional tags
   if (tags.containsKey("WhiteElo")) {
       header.setWhiteElo(Integer.parseInt(tags.get("WhiteElo")));
   }

   // Custom tags stored as fields
   for (String customTag : otherTags) {
       header.setField(customTag, tags.get(customTag));
   }
   ```

2. **Handle setup positions**
   ```java
   if (tags.get("SetUp").equals("1") && tags.containsKey("FEN")) {
       Position startPos = Position.fromFEN(tags.get("FEN"));
       movesModel = new GameMovesModel(startPos);
   }
   ```

3. **Parse move text**
   ```java
   // Main line: 1.e4 e5 2.Nf3 Nc6
   // Variations: ( 2.Bc4 Bc5 )
   // Comments: { This is the main line }
   // NAGs: $1 for ! (good move)

   PgnGameBuilder builder = new PgnGameBuilder(movesModel);
   for (Token token : lexer) {
       switch (token.type) {
           case MOVE -> builder.addMove(token.value);
           case COMMENT -> builder.addComment(token.value);
           case NAG -> builder.addNAG(token.value);
           case VARIATION_START -> builder.startVariation();
           case VARIATION_END -> builder.endVariation();
       }
   }
   ```

4. **Attach annotations**

   Comments become `CommentaryBeforeMoveAnnotation` or `CommentaryAfterMoveAnnotation`.
   NAGs become `NAGAnnotation` objects.

### Exporting: GameModel → PGN

**Class:** `se.yarin.chess.pgn.PgnExporter`

```java
PgnFormatOptions options = PgnFormatOptions.DEFAULT;
String pgn = PgnExporter.exportGame(gameModel, options);
```

**Configuration Options:**

```java
record PgnFormatOptions(
    int maxLineLength,              // Line wrapping (default: 79)
    boolean includeOptionalHeaders, // ECO, Elo, Source, etc.
    boolean includePlyCount,        // PlyCount tag
    boolean exportVariations,       // Include variations in output
    boolean exportComments,         // Include { comments }
    boolean exportNAGs,             // Include ! ? !! etc.
    boolean useSymbolsForNAGs,      // Use ! vs $1
    String lineEnding               // \n or \r\n
) {}

// Predefined options
PgnFormatOptions.DEFAULT                    // Full export
PgnFormatOptions.DEFAULT_WITHOUT_PLYCOUNT   // Omit PlyCount tag
PgnFormatOptions.COMPACT                    // Seven Tag Roster only
```

**Export Process:**

1. **Write headers**
   ```pgn
   [Event "World Championship"]
   [Site "London ENG"]
   [Date "2018.11.24"]
   [Round "12"]
   [White "Carlsen, Magnus"]
   [Black "Caruana, Fabiano"]
   [Result "1/2-1/2"]
   [WhiteElo "2835"]
   [BlackElo "2832"]
   [ECO "B33"]
   ```

2. **Write setup position (if applicable)**
   ```pgn
   [SetUp "1"]
   [FEN "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2"]
   ```

3. **Export move tree with formatting**
   ```java
   StringBuilder output = new StringBuilder();
   int lineLength = 0;

   void exportNode(Node node, boolean inVariation) {
       // Before-move comments
       for (Annotation ann : node.annotations().sorted()) {
           if (ann instanceof CommentaryBeforeMoveAnnotation) {
               append("{ " + ann.getComment() + " }");
           }
       }

       // Move with number
       if (needsMoveNumber(node)) {
           append(node.lastMove().moveNumber() + ".");
       }
       append(node.lastMove().toSAN());

       // Annotations after move (NAGs, comments)
       for (Annotation ann : node.annotations().sorted()) {
           if (ann instanceof NAGAnnotation nag) {
               append(options.useSymbolsForNAGs
                   ? nag.symbol()   // "!"
                   : nag.numeric()); // "$1"
           }
           if (ann instanceof CommentaryAfterMoveAnnotation) {
               append("{ " + ann.getComment() + " }");
           }
       }

       // Main line continuation (first child)
       if (!node.children().isEmpty()) {
           exportNode(node.children().get(0), inVariation);
       }

       // Variations (remaining children)
       for (int i = 1; i < node.children().size(); i++) {
           append("( ");
           exportNode(node.children().get(i), true);
           append(") ");
       }
   }
   ```

4. **Line wrapping**
   ```java
   void append(String text) {
       if (lineLength + text.length() > maxLineLength) {
           output.append("\n");
           lineLength = 0;
       }
       output.append(text);
       lineLength += text.length();
   }
   ```

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

```java
// Base class
public abstract class Annotation {
    public abstract int priority();  // Sort order for formatting
    public String format(String moveText, boolean ascii) {
        return moveText;  // Default: no decoration
    }
}

// Move evaluation symbols
NAGAnnotation(NAG.GOOD_MOVE)              // !
NAGAnnotation(NAG.POOR_MOVE)              // ?
NAGAnnotation(NAG.VERY_GOOD_MOVE)         // !!
NAGAnnotation(NAG.VERY_POOR_MOVE)         // ??
NAGAnnotation(NAG.SPECULATIVE_MOVE)       // !?
NAGAnnotation(NAG.DUBIOUS_MOVE)           // ?!

// Position evaluation
NAGAnnotation(NAG.WHITE_MODERATE_ADVANTAGE)   // +=
NAGAnnotation(NAG.BLACK_MODERATE_ADVANTAGE)   // =+
NAGAnnotation(NAG.WHITE_DECISIVE_ADVANTAGE)   // +-
NAGAnnotation(NAG.BLACK_DECISIVE_ADVANTAGE)   // -+

// Text comments
CommentaryBeforeMoveAnnotation("The critical moment")
CommentaryAfterMoveAnnotation("A serious mistake")
```

**Priority System** (controls export order):

| Priority | Annotation Type | Example |
|----------|----------------|---------|
| 1 | Before-move commentary | `{ comment } move` |
| 98 | Line evaluation | `+=` |
| 99 | Move prefix | `□` (only move) |
| 100 | Move comment | `!` (good move) |
| 0 | After-move commentary | `move { comment }` |

### Storage Annotations (Storage Layer)

**Package:** `se.yarin.morphy.games.annotations`

Rich, ChessBase-specific annotations that extend generic annotations:

#### 1. Text Annotations

```java
TextBeforeMoveAnnotation(language, text, unknownByte)
TextAfterMoveAnnotation(language, text, unknownByte)
```

Unlike generic commentary, these store language metadata.

#### 2. Symbol Annotation (Compact)

```java
SymbolAnnotation(
    NAG moveComment,      // !, ?, !!, ??, !?, ?!
    NAG movePrefix,       // □ (only move), ∆ (better move)
    NAG lineEvaluation    // +=, =+, +-, -+, =
)
```

**Key advantage:** Stores up to **three NAGs in one annotation** (vs. three separate `NAGAnnotation` objects).

#### 3. Computer Analysis

```java
ComputerEvaluationAnnotation(
    int centipawns,       // +50 = +0.50 advantage
    int evalType,         // 0 = normal, 1 = mate in N
    Integer depth         // Search depth (optional)
)

// Examples:
new ComputerEvaluationAnnotation(50, 0, 20)    // +0.50, depth 20
new ComputerEvaluationAnnotation(3, 1, null)   // Mate in 3
```

#### 4. Graphical Annotations

```java
GraphicalSquaresAnnotation(
    Map<Stone, GraphicalAnnotationColor> squares
)

GraphicalArrowsAnnotation(
    Map<Arrow, GraphicalAnnotationColor> arrows
)

// Available colors
enum GraphicalAnnotationColor {
    GREEN, YELLOW, RED
}

// Example: Mark e4 and d5 green, draw green arrow e2→e4
Map<Stone, Color> squares = Map.of(
    Chess.E4, GREEN,
    Chess.D5, GREEN
);
Map<Arrow, Color> arrows = Map.of(
    new Arrow(Chess.E2, Chess.E4), GREEN
);
```

#### 5. Time and Clock

```java
TimeSpentAnnotation(Duration timeSpent)
WhiteClockAnnotation(Duration remaining)
BlackClockAnnotation(Duration remaining)
```

#### 6. Media Annotations

```java
PictureAnnotation(String filePath, String description)
SoundAnnotation(String filePath)
VideoAnnotation(String filePath)
WebLinkAnnotation(String url, String title)
PiecePathAnnotation(List<Square> path)  // Piece movement visualization
```

#### 7. Training and Metadata

```java
CriticalPositionAnnotation()              // Mark key position
TrainingAnnotation(...)                   // Training questions
PawnStructureAnnotation(...)              // Pawn structure classification
GameQuotationAnnotation(...)              // Reference to another game
CorrespondenceMoveAnnotation(...)         // Correspondence chess metadata
```

### Annotation Serialization

Each storage annotation has an associated **type code** and **serializer**:

```java
interface AnnotationSerializer<T extends Annotation> {
    void serialize(ByteBuffer buffer, T annotation);
    T deserialize(ByteBuffer buffer, int length);
}

// Binary format:
// [1 byte]  type code (0x03, 0x04, 0x21, etc.)
// [2 bytes] total length (including 6-byte header)
// [3 bytes] reserved
// [N bytes] payload

// Type code registry
0x03 -> SymbolAnnotation.Serializer
0x04 -> GraphicalSquaresAnnotation.Serializer
0x05 -> GraphicalArrowsAnnotation.Serializer
0x21 -> ComputerEvaluationAnnotation.Serializer
0x81 -> TextAfterMoveAnnotation.Serializer
0x82 -> TextBeforeMoveAnnotation.Serializer
// ... etc.
```

### Unknown Annotation Handling

Morphy gracefully handles unrecognized annotations:

```java
// Preserve unknown types for round-trip fidelity
UnknownAnnotation(int typeCode, byte[] rawData)

// Capture parse errors without failing
InvalidAnnotation(int typeCode, Exception error)
```

### Annotation Statistics

The `AnnotationStatistics` class aggregates annotation metadata for game headers:

```java
class AnnotationStatistics {
    Set<Medal> medals;                  // Best game, novelty, etc.
    Set<GameHeaderFlags> flags;         // VARIATIONS, COMMENTARY, etc.
    int commentariesLength;
    int noSymbols;
    int noGraphicalSquares;
    int noGraphicalArrows;
    int noTraining;
    int noTimeSpent;

    // Magnitude calculations for UI filtering
    int getCommentariesMagnitude() {
        if (commentariesLength == 0) return 0;
        if (commentariesLength <= 200) return 1;
        return 2;
    }

    int getSymbolsMagnitude() {
        if (noSymbols == 0) return 0;
        if (noSymbols < 10) return 1;
        return 2;
    }
}
```

These statistics populate game header fields used for filtering and display.

---

## Complete Data Flow Examples

### Example 1: Loading and Displaying a Game

```java
// 1. Open database and start transaction
try (Database db = Database.open(new File("database.cbh"))) {
    try (DatabaseReadTransaction txn = db.beginReadTransaction()) {

        // 2. Get storage-layer game reference
        Game game = txn.getGame(1);

        // 3. Convert to logic layer (GameAdapter runs internally)
        GameModel model = game.getModel();

        // 4. Access metadata
        GameHeaderModel header = model.header();
        System.out.println(header.white() + " vs " + header.black());
        System.out.println("Event: " + header.event());
        System.out.println("Date: " + header.date());

        // 5. Export to PGN for frontend
        String pgn = PgnExporter.exportGame(model);

        // 6. Send to frontend (hypothetical)
        sendToFrontend(pgn);
    }
}
```

**Data flow:**
```
Database (.cbh/.cbg/.cba files)
    ↓ [DatabaseReadTransaction.getGame(1)]
Game (storage layer reference)
    ↓ [Game.getModel() → GameAdapter.getGameModel()]
GameModel (logic layer, mutable)
    ↓ [PgnExporter.exportGame()]
PGN String (frontend representation)
    ↓ [network/IPC]
Frontend (Chess.js library)
```

### Example 2: Importing a PGN Game

```java
// 1. Receive PGN from frontend or file
String pgn = """
    [Event "Casual Game"]
    [White "Doe, John"]
    [Black "Smith, Jane"]
    [Result "1-0"]

    1.e4 e5 2.Nf3 Nc6 3.Bb5 1-0
    """;

// 2. Parse to logic layer
GameModel model = PgnParser.parseGame(pgn);

// 3. Open database for writing
try (Database db = Database.open(new File("database.cbh"))) {
    try (DatabaseWriteTransaction txn = db.beginWriteTransaction()) {

        // 4. Add game (GameAdapter converts to storage layer)
        int gameId = txn.addGame(model);

        // 5. Commit to disk
        txn.commit();

        System.out.println("Game saved with ID: " + gameId);
    }
}
```

**Data flow:**
```
PGN String (from frontend/file)
    ↓ [PgnParser.parseGame()]
GameModel (logic layer, generic annotations)
    ↓ [DatabaseWriteTransaction.addGame() → GameAdapter.setGameData()]
Game (storage layer, entity resolution, move encoding)
    ↓ [commit()]
Database (.cbh/.cbg/.cba files)
```

### Example 3: Modifying a Game

```java
try (Database db = Database.open(new File("database.cbh"))) {
    try (DatabaseWriteTransaction txn = db.beginWriteTransaction()) {

        // 1. Load game
        Game game = txn.getGame(1);
        GameModel model = game.getModel();

        // 2. Modify header
        model.header().setWhiteElo(2850);
        model.header().setAnnotator("Kasparov, Garry");

        // 3. Add annotation to first move
        Node firstMove = model.moves().root().children().get(0);
        firstMove.annotations().add(
            new NAGAnnotation(NAG.GOOD_MOVE)
        );
        firstMove.annotations().add(
            new CommentaryAfterMoveAnnotation("The best move")
        );

        // 4. Replace game in database
        txn.replaceGame(game.id(), model);

        // 5. Commit
        txn.commit();
    }
}
```

### Example 4: Round-Trip with Annotations

```java
// Storage → Logic → PGN → Logic → Storage

// 1. Load from database (has rich annotations)
Game game = txn.getGame(1);
GameModel model1 = game.getModel();  // Has SymbolAnnotation, GraphicalSquaresAnnotation

// 2. Export to PGN
String pgn = PgnExporter.exportGame(model1);
// Graphical annotations lost (no PGN representation)
// SymbolAnnotation converted to individual ! ? symbols

// 3. Parse back from PGN
GameModel model2 = PgnParser.parseGame(pgn);
// Now has NAGAnnotation objects instead of SymbolAnnotation
// Graphical annotations gone

// 4. Save back to database
// TODO: Convert NAGAnnotation → SymbolAnnotation (not yet implemented)
txn.addGame(model2);
```

**Conversion matrix:**

| Annotation | Storage → PGN | PGN → Logic | Logic → Storage |
|------------|---------------|-------------|-----------------|
| SymbolAnnotation | ✅ → `!`, `?` | ❌ | ✅ |
| NAGAnnotation | ✅ → `!`, `?` | ✅ | ⚠️ TODO |
| ComputerEvaluationAnnotation | ⚠️ → comment | ❌ | ✅ |
| GraphicalSquaresAnnotation | ❌ lost | ❌ | ✅ |
| TextAfterMoveAnnotation | ✅ → `{ text }` | ✅ → Commentary | ⚠️ TODO |

---

## Design Rationale

### Why Not a Single Representation?

**Option 1: Use storage format everywhere**
- ❌ Requires binary serialization for every operation
- ❌ Hard to work with (offsets, entity IDs instead of objects)
- ❌ Mutating would require immediate disk writes

**Option 2: Use logic model everywhere**
- ❌ Inefficient on disk (larger files)
- ❌ Breaks ChessBase compatibility
- ❌ Loses optimization (compact move encoding)

**Option 3: Use PGN everywhere**
- ❌ Loses ChessBase-specific features (graphics, media, detailed analysis)
- ❌ String parsing overhead
- ❌ Ambiguity in representation

**✅ Three-layer architecture:**
- Each layer optimized for its purpose
- Clear boundaries and conversion points
- Flexibility to evolve layers independently

### Why Mutable Models?

The logic layer uses **mutable objects** despite modern preferences for immutability:

```java
// Mutable approach (current)
GameHeaderModel header = model.header();
header.setWhite("Carlsen, Magnus");
header.setWhiteElo(2850);

// vs. Immutable approach (alternative)
GameHeaderModel updated = ImmutableGameHeaderModel.builder()
    .from(model.header())
    .white("Carlsen, Magnus")
    .whiteElo(2850)
    .build();
```

**Rationale:**
1. **UI binding** - Forms and editors benefit from mutation with listeners
2. **Incremental changes** - Chess GUI often makes many small edits
3. **Tree structure** - Immutable trees are complex (persistent data structures)
4. **Performance** - Avoid copying entire game trees for small changes

**Trade-off:** Users must manage when to persist changes.

### Why Separate Annotation Hierarchies?

**Alternative: Single hierarchy with PGN-only annotations**
- ❌ Loses computer evaluation, graphics, media
- ❌ Can't round-trip ChessBase databases

**Alternative: Single hierarchy with storage annotations only**
- ❌ Complex serialization for simple PGN parsing
- ❌ PGN library would need ChessBase format knowledge

**✅ Dual hierarchy:**
- PGN parsing creates generic annotations
- Database loading creates storage annotations
- Both extend common `Annotation` base for polymorphism
- TODO: Convert generic → storage when saving PGN to database

### Entity Resolution Strategy

Entities (players, tournaments) are resolved **during adapter conversion**:

```java
// Storage layer: ID references
game.whitePlayerId() → 42

// Logic layer: Resolved objects
model.header().white() → "Carlsen, Magnus"
```

**Why not store entity objects in GameModel?**
- ✅ GameModel is self-contained (no database dependency)
- ✅ Enables creating games without a database
- ✅ Simpler serialization to PGN

**Why not store IDs in GameModel?**
- ✅ Type-safe (String names vs int IDs)
- ✅ User-friendly (developers work with names)
- ❌ Requires re-resolution when saving (fuzzy matching)

**Compromise:** Store IDs as custom fields for round-trip fidelity.

---

## See Also

- [Architecture Overview](ARCHITECTURE.md) - Overall system design
- [Database Reference](DATABASE-REFERENCE.md) - Entity types and game headers
- [User Guide](USER-GUIDE.md) - How to use the Morphy library
- [Developer Guide](DEVELOPER-GUIDE.md) - Contributing to Morphy
- [CBH Format Documentation](cbh-format/README.md) - Binary file format specification
