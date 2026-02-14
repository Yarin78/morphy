# Game Search Interface Design Proposal

## Overview

This document proposes a comprehensive search interface for the GamesService that replaces the simple `getGames()` method with a powerful, flexible filtering system supporting:
- Direct game property filtering
- Associated entity property filtering
- Special handling for dual entities (players, teams)
- Entity ID filtering
- Both query language and typed parameter approaches

## Current State

**Existing Infrastructure:**
- Robust `GameFilter` framework with composition via `CombinedGameFilter`
- 10+ specialized filter implementations (DateRangeFilter, RatingRangeFilter, PlayerFilter, etc.)
- High-level `ItemQuery` framework for common query patterns
- Entity transactions for accessing players, tournaments, teams, sources, annotators, game tags
- Efficient binary filtering on serialized GameHeader bytes

**Limitations:**
- Current REST API only exposes basic pagination (cursor, limit)
- No filtering exposed at service/controller layer
- Rich underlying query capabilities not accessible via HTTP

## Proposed Design

### 1. Query Language Approach

#### Option A: Simple Key-Value Filter Syntax

```
# Basic game properties
result:1-0
date:2020-01-01..2020-12-31
eco:E97
round:5

# Rating filters with modes
rating:2600..2800,mode=any          # Either player 2600-2800
rating:2600..2800,mode=both         # Both players 2600-2800
rating:2600..2800,mode=white        # White player only
rating:2600..2800,mode=black        # Black player only
rating:2600..2800,mode=average      # Average rating
rating:100..,mode=difference        # Rating difference

# Player filters
player:123,position=white           # White player has ID 123
player:456,position=black           # Black player has ID 456
player:789,position=any             # Either player has ID 789
player:101,position=both            # Both players (same ID - rare but possible?)
player.name:Carlsen,position=white  # White player name contains "Carlsen"

# Team filters (similar to players)
team:10,position=white
team:20,position=black
team:30,position=any
team.title:Barcelona

# Tournament filters
tournament:50                       # Tournament ID
tournament.name:Candidates
tournament.year:2020
tournament.site:London
tournament.category:15..20
tournament.type:swiss
tournament.timeControl:classical

# Other entity filters
annotator:75
annotator.name:Kasparov
source:100
source.title:ChessBase Magazine
source.quality:high
gametag:5
gametag.title:French Defense

# Multiple filters (AND logic)
result:1-0 AND rating:2700..,mode=any AND tournament.year:2020
```

**Pros:**
- Human-readable and writable
- Familiar to users of search engines
- Easy to parse and validate
- Supports both entity IDs and property-based lookups

**Cons:**
- Limited boolean logic (only AND, no OR/NOT without complexity)
- May become verbose for complex queries

#### Option B: JSON Filter Specification

```json
{
  "and": [
    { "game.result": "1-0" },
    { "game.date": { "from": "2020-01-01", "to": "2020-12-31" } },
    { "game.eco": "E97" },
    { "player": {
        "ids": [123, 456],
        "position": "any"
      }
    },
    { "rating": {
        "min": 2600,
        "max": 2800,
        "mode": "average"
      }
    },
    { "tournament": {
        "name": { "contains": "Candidates" },
        "year": 2020,
        "category": { "min": 15, "max": 20 }
      }
    }
  ]
}
```

**Pros:**
- Structured and type-safe
- Supports complex boolean logic (AND/OR/NOT)
- Easy to generate programmatically
- Clear hierarchy and composition

**Cons:**
- Not human-friendly for manual URL construction
- Requires JSON encoding in query parameters
- More verbose than key-value syntax

#### Option C: GraphQL-Style Syntax

```graphql
{
  games(
    where: {
      result: WHITE_WINS
      date: { gte: "2020-01-01", lte: "2020-12-31" }
      rating: { min: 2600, max: 2800, mode: AVERAGE }
      player: { ids: [123, 456], position: ANY }
      tournament: {
        name: { contains: "Candidates" }
        year: 2020
      }
    }
  )
}
```

**Pros:**
- Familiar to GraphQL users
- Expressive and readable
- Industry-standard pattern

**Cons:**
- Requires GraphQL parser/validator
- Overkill if not using GraphQL elsewhere
- More complex implementation

### 2. REST API Typed Parameters Approach

For common filtering scenarios, provide dedicated query parameters:

```
GET /api/v1/databases/{databaseId}/games?
  result=1-0&
  dateFrom=2020-01-01&
  dateTo=2020-12-31&
  ecoCode=E97&
  ratingMin=2600&
  ratingMax=2800&
  ratingMode=average&
  playerId=123&
  playerPosition=white&
  tournamentId=50&
  annotatorId=75&
  sourceId=100&
  gameTagId=5&
  limit=50&
  cursor=1000
```

**Advantages:**
- Simple for common use cases
- No encoding/parsing needed
- Easy to use from browser or curl
- Self-documenting via OpenAPI/Swagger

**Limitations:**
- Cannot express complex filters (multiple players, OR logic)
- Parameter explosion for all possibilities
- Name collisions (e.g., tournament.year vs game.year)

### 3. Hybrid Approach (RECOMMENDED)

Combine both approaches:

1. **Typed Parameters** for common simple filters (80% use case)
2. **Query Language** for complex filters (20% use case)

```
# Simple common case - typed parameters
GET /api/v1/databases/{id}/games?result=1-0&ratingMin=2600&playerId=123

# Complex case - query language
GET /api/v1/databases/{id}/games?
  filter=result:1-0 AND rating:2600..,mode=any AND tournament.year:2020&
  limit=50
```

Or using JSON for complex queries:

```
POST /api/v1/databases/{id}/games/search
Content-Type: application/json

{
  "filter": {
    "and": [
      { "game.result": "1-0" },
      { "player": { "ids": [123, 456], "position": "any" } },
      { "tournament": { "year": 2020, "category": { "min": 15 } } }
    ]
  },
  "limit": 50,
  "cursor": "1000"
}
```

## Proposed API Structure

### Request Model

```java
public record GameSearchRequest(
    // Pagination
    Integer cursor,
    Integer limit,

    // Content control
    Boolean includeMoves,
    Boolean includeText,

    // Simple typed filters (optional)
    String result,
    LocalDate dateFrom,
    LocalDate dateTo,
    String ecoCode,
    Integer round,
    Integer ratingMin,
    Integer ratingMax,
    RatingMode ratingMode,
    Integer playerId,
    PlayerPosition playerPosition,
    Integer tournamentId,
    Integer annotatorId,
    Integer sourceId,
    Integer teamId,
    TeamPosition teamPosition,
    Integer gameTagId,

    // Complex filter (optional - mutually exclusive with typed filters?)
    String filter,           // Query language string
    FilterSpec filterSpec    // JSON filter specification
) {
    public enum RatingMode {
        ANY, BOTH, WHITE, BLACK, AVERAGE, DIFFERENCE
    }

    public enum PlayerPosition {
        WHITE, BLACK, ANY, BOTH
    }

    public enum TeamPosition {
        WHITE, BLACK, ANY
    }
}
```

### Response Model (extends existing)

```java
public record GameSearchResponse(
    List<GameDto> games,
    Integer count,
    String nextCursor,
    Boolean hasMore,
    SearchMetadata metadata    // NEW
) {}

public record SearchMetadata(
    Integer totalMatched,      // Total games matching filter (expensive to compute)
    String appliedFilter,      // Canonical representation of applied filters
    Long executionTimeMs       // Query execution time
) {}
```

### Service Layer

```java
@Service
public class GamesService {

    /**
     * Search games with flexible filtering
     */
    public GameSearchResponse searchGames(
        String databaseId,
        GameSearchRequest request
    ) {
        // 1. Build GameFilter from request
        GameFilter filter = buildGameFilter(request);

        // 2. Execute query with pagination
        List<GameDto> games = databaseService.withReadTransaction(
            databaseId,
            txn -> txn.stream(request.cursor(), null, filter)
                     .limit(request.limit() + 1)
                     .map(game -> gameDtoConverter.toDto(
                         game,
                         request.includeMoves(),
                         request.includeText(),
                         false, false, false))
                     .collect(Collectors.toList())
        );

        // 3. Build response with pagination
        return buildResponse(games, request);
    }

    private GameFilter buildGameFilter(GameSearchRequest request) {
        List<GameFilter> filters = new ArrayList<>();

        // Always exclude guiding texts by default
        filters.add(new IsGameFilter());

        // Add typed parameter filters
        if (request.result() != null) {
            filters.add(new ResultsFilter(parseResult(request.result())));
        }
        if (request.dateFrom() != null || request.dateTo() != null) {
            filters.add(new DateRangeFilter(request.dateFrom(), request.dateTo()));
        }
        if (request.ratingMin() != null || request.ratingMax() != null) {
            filters.add(new RatingRangeFilter(
                request.ratingMin(),
                request.ratingMax(),
                mapRatingMode(request.ratingMode())
            ));
        }
        if (request.playerId() != null) {
            filters.add(new PlayerFilter(
                List.of(request.playerId()),
                mapPlayerPosition(request.playerPosition())
            ));
        }
        // ... similar for tournament, annotator, source, team, gameTag

        // Add query language filter if present
        if (request.filter() != null) {
            filters.add(parseQueryLanguage(request.filter()));
        }

        // Add JSON filter spec if present
        if (request.filterSpec() != null) {
            filters.add(parseFilterSpec(request.filterSpec()));
        }

        // Combine all filters with AND logic
        return CombinedGameFilter.combine(filters);
    }
}
```

### REST Controller

```java
@RestController
@RequestMapping("/api/v1/databases/{databaseId}/games")
public class GamesController {

    @GetMapping
    public ResponseEntity<GameSearchResponse> searchGames(
        @PathVariable String databaseId,
        @ModelAttribute GameSearchRequest request
    ) {
        return ResponseEntity.ok(gamesService.searchGames(databaseId, request));
    }

    @PostMapping("/search")
    public ResponseEntity<GameSearchResponse> searchGamesPost(
        @PathVariable String databaseId,
        @RequestBody GameSearchRequest request
    ) {
        return ResponseEntity.ok(gamesService.searchGames(databaseId, request));
    }
}
```

## Entity Property Filtering

### Pattern 1: Fetch Entity IDs First, Then Filter Games

```java
// Example: Find games in tournaments with "Candidates" in the name
public GameSearchResponse searchGamesByTournamentName(
    String databaseId,
    String tournamentNamePattern
) {
    return databaseService.withReadTransaction(databaseId, txn -> {
        // 1. Query tournament index for matching tournaments
        List<Integer> tournamentIds =
            txn.tournamentTransaction().iterable().stream()
               .filter(t -> t.title().contains(tournamentNamePattern))
               .map(Tournament::id)
               .collect(Collectors.toList());

        // 2. Filter games by those tournament IDs
        GameFilter filter = new TournamentFilter(tournamentIds);

        // 3. Execute game query
        List<GameDto> games = txn.stream(null, null, filter)
            .limit(limit + 1)
            .map(game -> gameDtoConverter.toDto(game, ...))
            .collect(Collectors.toList());

        return buildResponse(games, ...);
    });
}
```

**Pros:**
- Leverages existing entity index structures
- Efficient for entity lookups
- Can use entity-specific indexes

**Cons:**
- Requires two-phase query (entity lookup, then game filter)
- May load many entity IDs into memory

### Pattern 2: Join-Style Filtering (Load Full Entities)

```java
// During game iteration, load and check full entity
List<GameDto> games = txn.stream(null, null)
    .filter(game -> {
        Tournament t = game.tournament();
        return t != null &&
               t.title().contains(namePattern) &&
               t.category() >= minCategory;
    })
    .limit(limit)
    .map(...)
    .collect(Collectors.toList());
```

**Pros:**
- Simple and flexible
- Can filter on any entity property

**Cons:**
- Loads full entities for each game (more I/O)
- Scans all games (no early filtering)
- Poor performance for large databases

## Player/Team Position Handling

### Existing Implementation: GameEntityJoinCondition

**Great news!** PlayerFilter and TeamFilter already support position-based filtering using the `GameEntityJoinCondition` enum:

```java
public enum GameEntityJoinCondition {
    ANY,      // Matches if entity is white OR black
    BOTH,     // Matches if entity is BOTH white AND black
    WHITE,    // Matches only white position
    BLACK,    // Matches only black position
    WINNER,   // Matches the entity that won (regardless of color)
    LOSER     // Matches the entity that lost (regardless of color)
}
```

**Current Status:**
- **PlayerFilter**: Fully supports all positions including WINNER/LOSER
- **TeamFilter**: Supports WHITE/BLACK/ANY/BOTH but currently rejects WINNER/LOSER

**Implementation needed:**
1. Enable WINNER/LOSER support in TeamFilter (requires accessing GameResult)
2. Optionally create API-level enums (PlayerPosition, TeamPosition) for clearer REST API documentation that map to GameEntityJoinCondition internally

### Example Usage

```java
// Find games where Carlsen (id=123) won (regardless of color)
new PlayerFilter(123, GameEntityJoinCondition.WINNER);

// Find games where Carlsen played as White
new PlayerFilter(123, GameEntityJoinCondition.WHITE);

// Find games where Barcelona (teamId=789) played as either White or Black
new TeamFilter(789, GameEntityJoinCondition.ANY);
```

## Sorting

Current API returns games in ID order. Should sorting be supported?

**Options:**
1. **ID order only** (current) - Simple, predictable, cursor-based pagination works
2. **Limited sorting** - By date, rating, round - Requires different pagination strategy
3. **Full sorting** - Any field - Complex, may not work with cursor pagination

**Recommendation:** Start with ID order only, add sorting in v2 if needed.

## Performance Considerations

1. **Index Usage:**
   - Entity ID filters can use binary search on sorted game headers
   - Date/rating/result filters scan but are efficient at binary level
   - Complex entity property filters require two-phase queries

2. **Pagination:**
   - Cursor-based (current) is efficient for ID-ordered results
   - Offset-based pagination is expensive for large result sets

3. **Filter Limits:**
   - Should we limit the number of combined filters? (e.g., max 10)
   - Should we limit entity ID list sizes? (e.g., max 100 IDs)
   - Should we timeout long-running queries?

4. **Caching:**
   - Entity lookups (tournament name → IDs) could be cached
   - Filter compilation could be memoized

## Migration Strategy

1. **Phase 1:** Keep existing `getGames()` for backward compatibility
2. **Phase 2:** Introduce new `searchGames()` with typed parameters only
3. **Phase 3:** Add query language support
4. **Phase 4:** Deprecate old `getGames()` (if desired)

## Open Questions

### 1. Query Language Preference
Which query language style do you prefer?
- **A:** Simple key-value syntax (`result:1-0 AND rating:2600..`)
- **B:** JSON specification (structured but verbose)
- **C:** GraphQL-style (requires parser)
- **D:** Skip query language entirely, typed parameters only

### 2. Boolean Logic Support
Should the query language support OR and NOT operators?
- **Simple:** AND only (easier to implement, covers 90% of cases)
- **Full:** AND/OR/NOT with parentheses (complex but powerful)

### 3. Entity Property Filtering Strategy
How should entity property filtering work?
- **Two-phase:** Fetch entity IDs first, then filter games (efficient but limited)
- **Join-style:** Load entities during game scan (flexible but slower)
- **Hybrid:** Two-phase for indexed properties, join-style for others

### 4. Wildcard/Regex Support
Should text filters support wildcards or regex?
- `player.name:*Carlsen*` (wildcard)
- `player.name:/.*[Cc]arlsen.*/` (regex)
- Simple substring matching only

### 5. Case Sensitivity
Should text matching be case-sensitive or case-insensitive?
- Case-insensitive by default (more user-friendly)
- Case-sensitive by default (more precise)
- Configurable via modifier (e.g., `player.name:carlsen,ignoreCase=true`)

### 6. Partial Date Matching
Should partial dates be supported?
- `date:2020` → all games in 2020
- `date:2020-03` → all games in March 2020
- Exact dates only

### 7. Sorting Priority
Is sorting important for v1, or can it wait?
- High priority - need it for v1
- Low priority - ID order is fine for now
- Not needed - cursor pagination is enough

### 8. Aggregations/Statistics
Should the API support aggregations?
- `count(result)` → distribution of results (1-0: 450, 0-1: 380, ½-½: 170)
- `avg(rating)` → average rating in result set
- `histogram(date, interval=year)` → games per year
- No aggregations - just return matching games

### 9. Performance Limits
What limits should be enforced?
- Max filters per query: ?
- Max entity IDs in a filter: ?
- Query timeout: ?
- Max results per page: 1000 (current) or different?

### 10. Filter Validation
How should invalid filters be handled?
- Return 400 Bad Request with detailed error message
- Ignore invalid parts and return partial results
- Return 200 with warning in metadata

### 11. Backward Compatibility
Should the old `getGames()` be:
- Kept indefinitely (both APIs coexist)
- Deprecated with migration timeline
- Replaced immediately (breaking change)

### 12. Default Filters
Should any filters be applied by default?
- Exclude guiding texts (current behavior)
- Exclude deleted games
- User-configurable defaults

## Example Use Cases

To validate the design, here are some real-world queries:

### Use Case 1: My Games
"Find all games by Magnus Carlsen as White in 2024"
```
GET /games?playerId=123&playerPosition=white&dateFrom=2024-01-01&dateTo=2024-12-31
```

### Use Case 2: High-Level Candidates
"Find all games from the 2024 Candidates tournament with rating above 2750"
```
GET /games?tournamentId=456&ratingMin=2750&ratingMode=average
```

### Use Case 3: Opening Research
"Find all games in the Najdorf (ECO=B90-B99) where White won"
```
GET /games?filter=eco:B9* AND result:1-0
```

### Use Case 4: Team Tournament Games
"Find games where Barcelona played as either white or black"
```
GET /games?teamId=789&teamPosition=any
```

### Use Case 5: Annotator Quality
"Find games annotated by Kasparov from high-quality sources"
```
POST /games/search
{
  "filter": {
    "and": [
      { "annotator": { "ids": [101] } },
      { "source": { "quality": "HIGH" } }
    ]
  }
}
```

### Use Case 6: Rating Milestone
"Find games where the rating difference was at least 300 points (upsets)"
```
GET /games?ratingMin=300&ratingMode=difference
```

---

## Next Steps

1. **Clarify requirements** via the open questions above
2. **Choose query language approach** (or skip for v1)
3. **Design filter parser/builder** for chosen approach
4. **Extend existing filter classes** (PlayerFilter, TeamFilter with position support)
5. **Implement GameSearchRequest/Response** models
6. **Update service and controller** layers
7. **Add comprehensive tests** for filter combinations
8. **Document API** with OpenAPI/Swagger examples
9. **Performance testing** with large databases

---

**Feedback requested on:**
- Overall design direction
- Query language preference
- Open questions listed above
- Any missing use cases or requirements
