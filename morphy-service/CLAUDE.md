# morphy-service

Spring Boot REST API that wraps the morphy-cbh library, exposing ChessBase database operations over HTTP.

## Build & Run

```bash
cd morphy-service
mvn spring-boot:run        # Start on port 8080
mvn test                   # Run tests
```

## Tech Stack

- **Spring Boot 3.4.1** with embedded Tomcat
- **Java 21**
- Configuration in `application.properties`

## Configuration

- **Port**: 8080
- **Database config**: Loaded from `test-databases/databases.json` at startup. Each entry has a `displayName` and a `path` (the format follows from its extension), and optionally `readOnly` and `createIfMissing`. Databases open on first access; a missing one is created empty only with `createIfMissing`, and otherwise fails to open
- **Position indexes**: Defined in `test-databases/position-indexes.json` (and the git-ignored `position-indexes.local.json` next to it; property `app.position-indexes.config`), in parallel to the databases. Each entry, keyed by its id, has a short `name` (its pill in the Games pane), a `database` id, optionally a `filter` of the games it holds (in the game search's language, e.g. `"tournament.time:normal rating:2300..,mode=both"`) and optionally a `path` (default: next to the database, `<name>.<id>.positions`). A database can have several; an index of several databases is meant for later
- **Freshness check**: 600,000ms (10 min) - reopens stale database connections
- **Allowed paths**: Configurable for security when registering/creating databases

## Architecture

Standard 3-tier Spring MVC:
1. **Controllers** (`@RestController`) - HTTP request handlers
2. **Services** (`@Service`) - Business logic, transaction management, DTO conversion
3. **DatabaseService** - Database lifecycle: lazy opening, freshness checks, connection pooling

All database access through explicit transactions:
```java
databaseService.withReadTransaction(databaseId, txn -> { ... })
databaseService.withWriteTransaction(databaseId, txn -> { ... })
```

See @GAME_DTO_USAGE.md for DTO conversion details.

## API Endpoints

### Database Management
- `GET /api/databases` - List all databases
- `GET /api/databases/{id}` - Get database details
- `POST /api/databases` - Create new database
- `POST /api/databases/register` - Register existing database
- `POST /api/databases/{id}/refresh` - Force reload
- `DELETE /api/databases/{id}` - Unregister (files preserved)

### Games (`/api/databases/{id}/games`)
- `GET /` - List games (cursor-based pagination)
- `GET /{gameId}` - Get single game (with `?includeMoves=true`)
- `GET /count` - Total game count
- `GET|POST /search` - Advanced search with filter DSL, sorting, pagination, debug query plans
- `POST /` - Add game
- `PUT /{gameId}` - Replace game

### Position indexes (`/api/position-indexes`)
- `GET /` - The defined indexes, each with its status: `ready`, `missing`, `stale` (its database changed since it was built, or it was built with another filter), `building` (with the build's progress) or `failed`
- `GET /{id}` - One index and its status
- `GET /{id}/search?fen=&sortBy=&offset=&limit=&includeMoves=` - A page of the games of the index that reached a position, and with the first page a summary: the games, their results, and the moves played from it with their statistics (`PositionsService`, on the morphy-positions index, kept open once used). The response names the database the games are of. Sorted by `id`, `playedDate`, `playedYear`, `whiteElo`, `blackElo`, `eloAvg` or `eloMax`; others are refused. 400 for an unknown index, 409 when it's missing or out of date
- `POST /{id}/build` - Builds the index in the background (202, with its status); builds run one at a time, in the order asked for. The temporary bucket files go next to the index directory

### Entities (Players, Tournaments, Annotators, Sources, Teams, GameTags)
Each entity type has: list, get by ID, count, search, update endpoints under `/api/databases/{id}/{entity-type}/`.

### Logs
- `GET /api/logs?after={seq}` - The recent log events (kept in memory, the last 5000) after the one numbered `seq`: those logged while handling the caller's requests, and those of no request (startup, databases opening)

Every request may send `X-Request-Id` and `X-Session-Id`; `RequestIdFilter` puts them in the MDC, so everything logged while handling the request carries them, and `/api/logs` returns only the caller's session's events. A request without an id gets one, returned in the `X-Request-Id` response header. Exceptions are left to `GlobalExceptionHandler`, which logs them (with the ids) and answers `{"error": "..."}` with the message and its causes; controllers don't catch them themselves.

### Metadata
- `GET /api/health` - Health check
- `GET /api/filters/{entity-type}` - Available filter fields per entity type
