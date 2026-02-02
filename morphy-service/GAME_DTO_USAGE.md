# Game Dto Usage Guide

## Overview

The Game Dto structure provides a clean, structured way to represent chess games in API responses. It replaces the direct exposure of `GameHeaderModel` with a purpose-built Dto hierarchy.

## Dto Structure

### Main Dto
- **GameDto**: The primary Dto containing all game information

### Supporting Dtos
- **EventDetailsDto**: Tournament/event details (name, site, dates, category, etc.)
- **SourceDetailsDto**: Source information (publisher, title, date)
- **AnnotatorDetailsDto**: Annotator information
- **GameTagDetailsDto**: Game tag information
- **TeamDetailsDto**: Team information for both white and black
- **GameMovesDto**: Game moves (currently PGN format, extensible for future formats)
- **GameTextDto**: Text commentary associated with the game

## Usage Example

### Basic Conversion (Header Only)

```java
@Service
public class GamesService {
    private final GameDtoConverter gameDtoConverter;

    public GamesService(GameDtoConverter gameDtoConverter) {
        this.gameDtoConverter = gameDtoConverter;
    }

    public GameDto getGameHeader(String databaseId, int gameId) {
        return databaseService.withReadTransaction(databaseId, txn -> {
            Game game = txn.getGame(gameId);
            return gameDtoConverter.toDto(game); // No moves, no text
        });
    }
}
```

### Full Game with Moves

```java
public GameDto getFullGame(String databaseId, int gameId) {
    return databaseService.withReadTransaction(databaseId, txn -> {
        Game game = txn.getGame(gameId);
        return gameDtoConverter.toDto(game, true, false); // Include moves, no text
    });
}
```

### Game with Text Commentary

```java
public GameDto getGameWithText(String databaseId, int gameId) {
    return databaseService.withReadTransaction(databaseId, txn -> {
        Game game = txn.getGame(gameId);
        return gameDtoConverter.toDto(game, true, true); // Include moves and text
    });
}
```

### Batch Conversion

```java
public List<GameDto> getGameHeaders(String databaseId, int limit) {
    return databaseService.withReadTransaction(databaseId, txn ->
        txn.stream(null, null)
            .limit(limit)
            .map(gameDtoConverter::toDto)
            .collect(Collectors.toList())
    );
}
```

## JSON Structure Examples

### Minimal Game (Header Only)

```json
{
  "id": 123,
  "whiteId": 456,
  "white": "Kasparov, Garry",
  "whiteElo": 2851,
  "blackId": 789,
  "black": "Karpov, Anatoly",
  "blackElo": 2750,
  "result": "WIN_WHITE",
  "date": {
    "year": 1985,
    "month": 11,
    "day": 9
  },
  "eco": "E97",
  "round": 24,
  "event": {
    "id": 101,
    "name": "World Championship"
  }
}
```

### Full Game with All Details

```json
{
  "id": 123,
  "whiteId": 456,
  "white": "Kasparov, Garry",
  "whiteElo": 2851,
  "blackId": 789,
  "black": "Karpov, Anatoly",
  "blackElo": 2750,
  "teams": {
    "whiteTeamId": 10,
    "whiteTeam": "Team USSR",
    "blackTeamId": 10,
    "blackTeam": "Team USSR"
  },
  "result": "WIN_WHITE",
  "date": {
    "year": 1985,
    "month": 11,
    "day": 9
  },
  "eco": "E97",
  "round": 24,
  "lineEvaluation": "GOOD_MOVE",
  "event": {
    "id": 101,
    "name": "World Championship",
    "site": "Moscow",
    "country": "URS",
    "startDate": {
      "year": 1985,
      "month": 9,
      "day": 1
    },
    "endDate": {
      "year": 1985,
      "month": 11,
      "day": 9
    },
    "type": "Match",
    "rounds": 48
  },
  "source": {
    "id": 50,
    "name": "ChessBase",
    "title": "World Championship Database"
  },
  "annotator": {
    "id": 42,
    "name": "Kasparov, Garry"
  },
  "moves": {
    "pgn": "1. d4 Nf6 2. c4 g6 3. Nc3 Bg7..."
  }
}
```

### Game with Partial Date

When only partial date information is available:

```json
{
  "id": 456,
  "white": "Morphy, Paul",
  "black": "Amateur",
  "date": {
    "year": 1858,
    "month": 0,
    "day": 0
  },
  "event": {
    "id": 200,
    "name": "Paris Blindfold Exhibition"
  }
}
```

## Key Features

### Nullable Fields
All fields except `id` are nullable, allowing for sparse data representation.

### Entity IDs
Entity IDs (whiteId, blackId, etc.) are included both in the main Dto and within nested detail Dtos:
- Top-level IDs for players (whiteId, blackId) for convenience
- IDs within nested Dtos (event.id, source.id, annotator.id, gameTag.id)

### Partial Dates
Uses `se.yarin.chess.Date` which supports partial dates (year-only, year-month-only).

### Extensibility
- GameMovesDto can be extended to support other formats (JSON, algebraic notation, etc.)
- GameTagDetailsDto can be extended to include other language titles

## Integration Points

### Update Existing Endpoints

Replace `GameHeaderResponse` and `GameResponse` with `GameDto`:

**Before:**
```java
public record GameResponse(int gameId, GameHeaderModel header, String moves, String movesFormat) {}
```

**After:**
```java
// Just return GameDto directly
public GameDto getGame(String databaseId, int gameId) {
    return gameDtoConverter.toDto(game, true, false);
}
```

### Controller Example

```java
@RestController
@RequestMapping("/api/v1/databases/{databaseId}/games")
public class GamesController {
    private final GamesService gamesService;
    private final GameDtoConverter gameDtoConverter;

    @GetMapping("/{gameId}")
    public GameDto getGame(
        @PathVariable String databaseId,
        @PathVariable int gameId,
        @RequestParam(defaultValue = "false") boolean includeMoves,
        @RequestParam(defaultValue = "false") boolean includeText) {

        return gamesService.getGame(databaseId, gameId, includeMoves, includeText);
    }
}
```

## Notes

- The converter handles all entity resolution (fetching full entity objects from IDs)
- Error handling is built into the converter (returns null for missing/invalid data)
- The converter is thread-safe and can be used as a Spring singleton
