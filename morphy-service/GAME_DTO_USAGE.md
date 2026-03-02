# Game DTO Usage Guide

## Overview

The Game DTO structure represents chess games in API responses. GameDto is the primary DTO, with nested entity DTOs (PlayerDto, TournamentDto, SourceDto, AnnotatorDto, GameTagDto, TeamDto) from their respective service packages. All DTOs use `@JsonInclude(NON_NULL)` so null fields are omitted from JSON output.

See the DTO record definitions for exact field listings — they are self-documenting.

## GameDtoConverter

The converter has three overloads:

- `toDto(Game game)` — header only, no moves/text/extra details
- `toDto(Game game, includeMoves, includeText, includeTournamentDetails, includeSourceDetails, includeTeamDetails)` — full control
- `toDto(Game game, ..., debugRawData)` — adds raw binary data from database files

The `includeTournamentDetails`, `includeSourceDetails`, and `includeTeamDetails` flags control whether full entity data is fetched or just the ID and name. When listing many games, these are typically `false` for performance. For single-game lookups, they're typically `true`.

### Usage

```java
// Header only (batch listing)
gameDtoConverter.toDto(game);

// Single game with moves and full entity details
gameDtoConverter.toDto(game, true, false, true, true, true);
```

## Endpoint Defaults

The `GET /{gameId}` endpoint defaults `includeMoves` to **true** (single game — show moves by default).
The `GET /` list endpoint defaults `includeMoves` to **false** (batch — skip moves for performance).
Both default `includeText` to **false**.

## JSON Shape

Null fields are omitted. Dates support partial values (month=0, day=0 means unknown). A minimal response looks like:

```json
{
  "id": 123,
  "whitePlayer": { "id": 456, "lastName": "Kasparov", "firstName": "Garry" },
  "whiteElo": 2851,
  "blackPlayer": { "id": 789, "lastName": "Karpov", "firstName": "Anatoly" },
  "blackElo": 2750,
  "result": "WIN_WHITE",
  "date": { "year": 1985, "month": 11, "day": 9 },
  "eco": "E97",
  "round": 24,
  "tournament": { "id": 101, "title": "World Championship" }
}
```

With full entity details enabled, nested DTOs expand (e.g., tournament gains `place`, `nation`, `startDate`, `endDate`, `type`, `rounds`, `category`, etc.).

## Notes

- The converter handles all entity resolution (fetching full entity objects from IDs)
- Error handling is built in (returns null for missing/invalid data)
- The converter is thread-safe and used as a Spring singleton
