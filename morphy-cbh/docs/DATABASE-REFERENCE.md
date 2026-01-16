# Database Reference

This document provides a user-friendly reference for the data stored in a ChessBase database. For the detailed binary file format specification, see the [cbh-format documentation](cbh-format/README.md).

## Table of Contents

1. [Game Header](#game-header)
2. [Entities Overview](#entities-overview)
3. [Player](#player)
4. [Tournament](#tournament)
5. [Annotator](#annotator)
6. [Source](#source)
7. [Team](#team)
8. [Game Tag](#game-tag)
9. [Common Types](#common-types)

---

## Game Header

Every game in the database has a header containing metadata about the game. The header is split between the main header (`.cbh` file) and extended header (`.cbj` file).

### Main Header Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | int | Unique game identifier (starts at 1) |
| `deleted` | boolean | Whether the game is marked as deleted |
| `guidingText` | boolean | True if this is a text document rather than a game |
| `whitePlayerId` | int | Reference to the White player |
| `blackPlayerId` | int | Reference to the Black player |
| `tournamentId` | int | Reference to the tournament |
| `annotatorId` | int | Reference to the annotator |
| `sourceId` | int | Reference to the source |
| `playedDate` | [Date](#date) | When the game was played |
| `result` | [GameResult](#game-result) | Result of the game |
| `round` | int | Round number (0 = not set) |
| `subRound` | int | Sub-round number (0 = not set) |
| `whiteElo` | int | White player's rating (0 = not set) |
| `blackElo` | int | Black player's rating (0 = not set) |
| `eco` | [Eco](#eco) | ECO opening classification |
| `chess960StartPosition` | int | Chess960 position number (-1 for standard chess) |
| `lineEvaluation` | [NAG](#nag) | Evaluation symbol (only if result is `LINE`) |
| `medals` | Set<[Medal](#medals)> | Medals awarded to the game |
| `flags` | Set<[Flag](#annotation-flags)> | Annotation type flags |

### Extended Header Fields

| Field | Type | Description |
|-------|------|-------------|
| `whiteTeamId` | int | Reference to White's team (-1 if none) |
| `blackTeamId` | int | Reference to Black's team (-1 if none) |
| `whiteRatingType` | [RatingType](#rating-type) | Details about White's rating |
| `blackRatingType` | [RatingType](#rating-type) | Details about Black's rating |
| `finalMaterial` | FinalMaterial | Material count at end of game |
| `endgameInfo` | EndgameInfo | Types of endgames encountered |
| `version` | int | Incremented each time game is saved |
| `gameCreationTimestamp` | long | When the game was originally created |
| `gameUpdateTimestamp` | long | When the game was last saved |
| `gameTagId` | int | Reference to game tag (-1 if none) |

### Example: Accessing Game Header

The `Game` class provides a unified facade over both the main and extended header fields.

```java
try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
    Game game = txn.getGame(1);

    // Main header fields (from .cbh file)
    System.out.println("Date: " + game.playedDate());
    System.out.println("Result: " + game.result());
    System.out.println("White Elo: " + game.whiteElo());
    System.out.println("ECO: " + game.eco());
    System.out.println("Round: " + game.round());

    // Extended header fields (from .cbj file)
    System.out.println("Created: " + game.creationTime().getTime());
    System.out.println("Last modified: " + game.lastChangedTime().getTime());
    System.out.println("Version: " + game.gameVersion());

    // Rating type details
    RatingType whiteRating = game.whiteRatingType();
    System.out.println("White rating type: " + whiteRating.ratingType());

    // Team information (returns null if no team)
    Team whiteTeam = game.whiteTeam();
    if (whiteTeam != null) {
        System.out.println("White team: " + whiteTeam.title());
    }

    // For fields not exposed on Game, access the underlying headers directly
    GameHeader header = game.header();
    ExtendedGameHeader extHeader = game.extendedHeader();

    // Check medals
    if (header.medals().contains(Medal.BEST_GAME)) {
        System.out.println("This is a best game!");
    }

    // Check annotation types
    if (header.flags().contains(GameHeaderFlags.VARIATIONS)) {
        System.out.println("Game contains variations");
    }

    // Endgame information
    EndgameInfo endgameInfo = extHeader.endgameInfo();
    if (endgameInfo != null) {
        System.out.println("Endgame types: " + endgameInfo);
    }
}
```

For detailed binary format, see [games.md](cbh-format/games.md).

---

## Entities Overview

Entities are reusable objects referenced by games. All entities share these common fields:

| Field | Type | Description |
|-------|------|-------------|
| `id` | int | Unique identifier (-1 if not yet persisted) |
| `count` | int | Number of games referencing this entity |
| `firstGameId` | int | ID of the first game that references this entity |

Entities can only exist if at least one game references them. When no games reference an entity, it is automatically deleted.

For the general structure of entity index files, see [entities.md](cbh-format/entities.md).

---

## Player

Players are stored in the `.cbp` file.

### Fields

| Field | Type | Max Length | Description |
|-------|------|------------|-------------|
| `lastName` | String | 30 chars | Player's surname |
| `firstName` | String | 20 chars | Player's first name |

Additional player information (nationality, birth date, titles, etc.) comes from the Player Encyclopedia, which is a separate database.

### Derived Fields

| Field | Type | Description |
|-------|------|-------------|
| `fullName()` | String | Combined "LastName, FirstName" |

### Example: Working with Players

```java
try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
    // Get player from a game
    Game game = txn.getGame(1);
    Player white = game.white();

    System.out.println("Name: " + white.fullName());
    System.out.println("Games in database: " + white.count());

    // Iterate all players (sorted by last name)
    for (Player player : txn.playerTransaction().iterable()) {
        if (player.count() > 100) {
            System.out.println(player.fullName() + ": " + player.count() + " games");
        }
    }
}
```

For detailed binary format, see [players.md](cbh-format/players.md).

---

## Tournament

Tournaments are stored in the `.cbt` file, with extended data in the `.cbtt` file.

### Main Fields

| Field | Type | Max Length | Description |
|-------|------|------------|-------------|
| `title` | String | 40 chars | Tournament name |
| `place` | String | 30 chars | Location/city |
| `date` | [Date](#date) | - | Start date |
| `nation` | [Nation](#nation) | - | Country where held |
| `category` | int | - | Category (0-99, typically I-X in Roman numerals) |
| `rounds` | int | - | Number of rounds |
| `type` | [TournamentType](#tournament-type) | - | Pairing system |
| `timeControl` | [TournamentTimeControl](#tournament-time-control) | - | Time control |
| `complete` | boolean | - | All games from tournament are in database |
| `teamTournament` | boolean | - | Is a team event |
| `threePointsWin` | boolean | - | 3 points for a win (soccer-style scoring) |
| `boardPoints` | boolean | - | Uses board points |

### Extended Fields (from .cbtt)

| Field | Type | Description |
|-------|------|-------------|
| `endDate` | [Date](#date) | Tournament end date |
| `latitude` | double | Location latitude |
| `longitude` | double | Location longitude |
| `tiebreakRules` | List<[TiebreakRule](#tiebreak-rules)> | Up to 4 tiebreak rules |

### Example: Working with Tournaments

```java
try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
    Game game = txn.getGame(1);
    Tournament tournament = game.tournament();

    // Main tournament fields (from .cbt file)
    System.out.println("Event: " + tournament.title());
    System.out.println("Place: " + tournament.place());
    System.out.println("Start date: " + tournament.date());
    System.out.println("Type: " + tournament.type());
    System.out.println("Time Control: " + tournament.timeControl());
    System.out.println("Nation: " + tournament.nation().getName());

    if (tournament.category() > 0) {
        System.out.println("Category: " + tournament.getCategoryRoman());
    }

    if (tournament.teamTournament()) {
        System.out.println("This is a team event");
    }

    // Extended tournament fields (from .cbtt file)
    TournamentExtra extra = game.tournamentExtra();

    // End date
    if (!extra.endDate().isUnset()) {
        System.out.println("End date: " + extra.endDate());
    }

    // Location coordinates
    if (extra.latitude() != 0 || extra.longitude() != 0) {
        System.out.println("Location: " + extra.latitude() + ", " + extra.longitude());
    }

    // Tiebreak rules
    List<TiebreakRule> tiebreaks = extra.tiebreakRules();
    if (!tiebreaks.isEmpty()) {
        System.out.println("Tiebreaks: " + tiebreaks);
    }

    // Find all rapid tournaments
    for (Tournament t : txn.tournamentTransaction().iterable()) {
        if (t.timeControl() == TournamentTimeControl.RAPID) {
            System.out.println(t.title() + " (" + t.date().year() + ")");
        }
    }
}
```

For detailed binary format, see [tournaments.md](cbh-format/tournaments.md).

---

## Annotator

Annotators are stored in the `.cbc` file.

### Fields

| Field | Type | Max Length | Description |
|-------|------|------------|-------------|
| `name` | String | 45 chars | Full name of the annotator |

### Example: Working with Annotators

```java
try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
    Game game = txn.getGame(1);
    Annotator annotator = game.annotator();

    if (!annotator.name().isEmpty()) {
        System.out.println("Annotated by: " + annotator.name());
    }

    // Find prolific annotators
    for (Annotator a : txn.annotatorTransaction().iterable()) {
        if (a.count() > 50) {
            System.out.println(a.name() + ": " + a.count() + " games");
        }
    }
}
```

For detailed binary format, see [annotators.md](cbh-format/annotators.md).

---

## Source

Sources (publications) are stored in the `.cbs` file.

### Fields

| Field | Type | Max Length | Description |
|-------|------|------------|-------------|
| `title` | String | 25 chars | Source title |
| `publisher` | String | 16 chars | Publisher name |
| `publication` | [Date](#date) | - | Publication date |
| `date` | [Date](#date) | - | Source date |
| `version` | int | - | Version number |
| `quality` | [SourceQuality](#source-quality) | - | Data quality estimation |

### Example: Working with Sources

```java
try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
    Game game = txn.getGame(1);
    Source source = game.source();

    System.out.println("Source: " + source.title());
    System.out.println("Publisher: " + source.publisher());
    System.out.println("Quality: " + source.quality());
}
```

For detailed binary format, see [sources.md](cbh-format/sources.md).

---

## Team

Teams are stored in the `.cbe` file. Used for team tournaments like the Olympiad or national leagues.

### Fields

| Field | Type | Max Length | Description |
|-------|------|------------|-------------|
| `title` | String | 45 chars | Team name |
| `teamNumber` | int | - | Team number |
| `year` | int | - | Year associated with the team |
| `season` | boolean | - | If true: year/year+1 season format |
| `nation` | [Nation](#nation) | - | Team's nation |

### Example: Working with Teams

```java
try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
    Game game = txn.getGame(1);

    Team whiteTeam = game.whiteTeam();
    Team blackTeam = game.blackTeam();

    if (whiteTeam != null) {
        System.out.println("White team: " + whiteTeam.title());
        if (whiteTeam.season()) {
            System.out.println("Season: " + whiteTeam.year() + "/" + (whiteTeam.year() + 1));
        } else {
            System.out.println("Year: " + whiteTeam.year());
        }
    }
}
```

For detailed binary format, see [teams.md](cbh-format/teams.md).

---

## Game Tag

Game tags (also called game titles) are stored in the `.cbl` file. They provide custom titles for games in multiple languages.

### Fields

| Field | Type | Max Length | Description |
|-------|------|------------|-------------|
| `englishTitle` | String | 199 chars | Title in English |
| `germanTitle` | String | 199 chars | Title in German |
| `frenchTitle` | String | 199 chars | Title in French |
| `spanishTitle` | String | 199 chars | Title in Spanish |
| `italianTitle` | String | 199 chars | Title in Italian |
| `dutchTitle` | String | 199 chars | Title in Dutch |
| `slovenianTitle` | String | 199 chars | Title in Slovenian |

### Example: Working with Game Tags

```java
try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
    Game game = txn.getGame(1);
    GameTag tag = game.gameTag();

    if (tag != null) {
        System.out.println("Title: " + tag.englishTitle());
    }
}
```

For detailed binary format, see [game_tags.md](cbh-format/game_tags.md).

---

## Common Types

### Date

Dates may be incomplete (only year, or year and month specified).

| Field | Type | Description |
|-------|------|-------------|
| `year` | int | Year (0 = unspecified) |
| `month` | int | Month 1-12 (0 = unspecified) |
| `day` | int | Day 1-31 (0 = unspecified) |

Example: `Date(2024, 1, 15)` for January 15, 2024.

### Game Result

| Value | Description |
|-------|-------------|
| `WHITE_WINS` | 1-0 |
| `DRAW` | 1/2-1/2 |
| `BLACK_WINS` | 0-1 |
| `LINE` | Analysis line (no actual result) |
| `WHITE_WINS_ON_FORFEIT` | +/- (forfeit) |
| `BOTH_LOST` | -/- |
| `BLACK_WINS_ON_FORFEIT` | -/+ (forfeit) |
| `NOT_FINISHED` | * (game not finished) |

### Eco

ECO opening classification (A00-E99) with optional sub-codes.

```java
Eco eco = header.eco();
System.out.println(eco.toString());     // e.g., "B97"
System.out.println(eco.toSubString());  // e.g., "B97/08" (if subcode set)
```

### NAG

Numeric Annotation Glyphs - standard chess annotation symbols.

| Value | Symbol | Meaning |
|-------|--------|---------|
| `GOOD_MOVE` | ! | Good move |
| `POOR_MOVE` | ? | Poor move |
| `VERY_GOOD_MOVE` | !! | Brilliant move |
| `VERY_POOR_MOVE` | ?? | Blunder |
| `SPECULATIVE_MOVE` | !? | Interesting move |
| `DUBIOUS_MOVE` | ?! | Dubious move |
| `WHITE_DECISIVE_ADVANTAGE` | +- | White is winning |
| `BLACK_DECISIVE_ADVANTAGE` | -+ | Black is winning |
| ... | ... | (many more) |

See the [Wikipedia PGN page](https://en.wikipedia.org/wiki/Portable_Game_Notation#Standard_NAGs) for a complete list of NAG's.

### Medals

Medals highlight notable aspects of a game.

| Medal | Description |
|-------|-------------|
| `BEST_GAME` | Outstanding game |
| `DECIDED_TOURNAMENT` | Game that decided the tournament |
| `MODEL_GAME` | Model game for opening plan |
| `NOVELTY` | Contains a theoretical novelty |
| `PAWN_STRUCTURE` | Instructive pawn structure |
| `STRATEGY` | Instructive strategic play |
| `TACTICS` | Instructive tactical play |
| `WITH_ATTACK` | Features an attack |
| `SACRIFICE` | Contains a sacrifice |
| `DEFENSE` | Instructive defensive play |
| `MATERIAL` | Instructive material handling |
| `PIECE_PLAY` | Instructive piece play |
| `ENDGAME` | Instructive endgame |
| `TACTICAL_BLUNDER` | Contains a tactical blunder |
| `STRATEGICAL_BLUNDER` | Contains a strategic blunder |
| `USER` | User-defined medal |

### Annotation Flags

Flags indicating what types of annotations a game contains.

| Flag | Description |
|------|-------------|
| `STARTING_POSITION` | Game doesn't start from initial position |
| `VARIATIONS` | Contains move variations |
| `COMMENTARY` | Contains text commentary |
| `SYMBOLS` | Contains NAG symbols |
| `GRAPHICAL_SQUARES` | Contains colored squares |
| `GRAPHICAL_ARROWS` | Contains arrows |
| `TIME_SPENT` | Contains time per move data |
| `TRAINING` | Contains training annotations |
| `EMBEDDED_AUDIO` | Contains audio |
| `EMBEDDED_PICTURE` | Contains pictures |
| `EMBEDDED_VIDEO` | Contains video |
| `CRITICAL_POSITION` | Marks critical positions |
| `WEB_LINK` | Contains web links |

### Nation

Country codes based on IOC format.

```java
Nation nation = tournament.nation();
System.out.println(nation.getName());    // "Sweden"
System.out.println(nation.getIocCode()); // "SWE"
```

### Tournament Type

| Value | Description |
|-------|-------------|
| `NONE` | Not specified |
| `SINGLE_GAME` | Single game |
| `MATCH` | Match |
| `ROUND_ROBIN` | Round robin tournament |
| `SWISS_SYSTEM` | Swiss system |
| `TEAM` | Team tournament |
| `KNOCK_OUT` | Knockout format |
| `SIMUL` | Simultaneous exhibition |
| `SCHEVENINGEN_SYSTEM` | Scheveningen system |

### Tournament Time Control

| Value | Description |
|-------|-------------|
| `NORMAL` | Classical time control |
| `BLITZ` | Blitz |
| `RAPID` | Rapid |
| `CORRESPONDENCE` | Correspondence chess |

### Source Quality

| Value | Description |
|-------|-------------|
| `UNSET` | Not specified |
| `HIGH` | High quality data |
| `MEDIUM` | Medium quality data |
| `LOW` | Low quality data |

### Rating Type

Describes the type of rating (FIDE, national, etc.) and time control.

| Field | Type | Description |
|-------|------|-------------|
| `name` | String | Rating system name (e.g., "FIDE", "USCF") |
| `nation` | Nation | Nation for national ratings |
| `isInternational` | boolean | True for international ratings |
| `timeControl` | TournamentTimeControl | Time control for this rating |

### Tiebreak Rules

Available tiebreak rules for tournaments.

**Swiss System:**
- `RATING_BUCHHOLZ` - Buchholz based on rating
- `FEINE_BUCHHOLZ` - Fine Buchholz
- `MEDIAN_BUCHHOLZ` - Median Buchholz
- `FORTSCHRITT` - Progress score
- `SONNENBORNBERGER` - Sonneborn-Berger
- `MEDIAN2_BUCHHOLZ` - Median-2 Buchholz
- `CUT1_BUCHHOLZ` - Buchholz Cut 1
- `CUT2_BUCHHOLZ` - Buchholz Cut 2

**Round Robin:**
- `SONNEBORNBERGER` - Sonneborn-Berger
- `NUM_WINS` - Number of wins
- `NUM_BLACK_WINS` - Number of wins with Black
- `NUM_BLACK_GAMES` - Number of games with Black
- `POINT_GROUP` - Point group
- `KOYA` - Koya system

---

## See Also

- [User Guide](USER-GUIDE.md) - How to use the Morphy library
- [CBH Format Documentation](cbh-format/README.md) - Detailed binary file format specification
