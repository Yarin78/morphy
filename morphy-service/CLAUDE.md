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
- **Database config**: Loaded from `test-databases/databases.json` at startup
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

### Entities (Players, Tournaments, Annotators, Sources, Teams, GameTags)
Each entity type has: list, get by ID, count, search, update endpoints under `/api/databases/{id}/{entity-type}/`.

### Metadata
- `GET /api/health` - Health check
- `GET /api/filters/{entity-type}` - Available filter fields per entity type
