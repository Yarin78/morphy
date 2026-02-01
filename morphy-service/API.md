# Morphy Service REST API

## Base URL
`http://localhost:8080/api`

## Getting Started

When running the service locally, a test database is automatically available:

**world-ch** - World Chess Championships database with historical championship games
- Database ID: `world-ch`
- Location: `test-databases/world-ch/`
- Content: ~500 World Championship games

Try it:
```bash
# Get database info
curl http://localhost:8080/api/databases/world-ch

# Get first 10 games
curl http://localhost:8080/api/databases/world-ch/games?limit=10

# Get total game count
curl http://localhost:8080/api/databases/world-ch/games/count

# Get a specific game (with moves)
curl http://localhost:8080/api/databases/world-ch/games/1
```

---

## Database Management API

### List all databases
```
GET /databases
```

**Response:**
```json
{
  "databases": [
    {
      "id": "megabase2024",
      "displayName": "Mega Database 2024",
      "path": "/path/to/database.cbh"
    }
  ]
}
```

### Get database details
```
GET /databases/{databaseId}
```

**Response:**
```json
{
  "id": "megabase2024",
  "displayName": "Mega Database 2024",
  "path": "/path/to/database.cbh"
}
```

### Refresh database
```
POST /databases/{databaseId}/refresh
```
Forces the database to reload from disk. Returns 204 No Content on success.

---

## Games API

### List games (with cursor-based pagination)
```
GET /databases/{databaseId}/games?cursor={cursor}&limit={limit}
```

**Query Parameters:**
- `cursor` (optional): Game ID to start from. Omit for the first page.
- `limit` (optional, default 100, max 1000): Number of games to return

**Response:**
```json
{
  "games": [
    {
      "gameId": 1,
      "header": {
        "white": "Carlsen, Magnus",
        "black": "Nepomniachtchi, Ian",
        "result": "WHITE_WINS",
        "date": "2021-12-10",
        "event": "World Championship",
        "eventSite": "Dubai",
        "eco": "C42",
        "whiteElo": 2855,
        "blackElo": 2782,
        ...
      }
    },
    ...
  ],
  "count": 100,
  "nextCursor": "101",
  "hasMore": true
}
```

**Pagination Example:**
1. First request: `GET /databases/megabase/games?limit=100`
2. Next page: `GET /databases/megabase/games?cursor=101&limit=100`
3. Continue using `nextCursor` until `hasMore` is `false`

### Get game by ID
```
GET /databases/{databaseId}/games/{gameId}?format={format}
```

**Query Parameters:**
- `format` (optional, default "pgn"): Format for the moves section
  - `"pgn"`: PGN notation (currently the only supported format)

**Response:**
```json
{
  "gameId": 123,
  "header": {
    "white": "Carlsen, Magnus",
    "black": "Nepomniachtchi, Ian",
    "result": "WHITE_WINS",
    "date": "2021-12-10",
    "event": "World Championship",
    ...
  },
  "moves": "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 ...",
  "movesFormat": "pgn"
}
```

**Notes:**
- The `header` field contains the full `GameHeaderModel` as a structured object
- The `moves` field contains only the moves in PGN format (no headers)
- The `movesFormat` field indicates the format of the moves
- This design avoids parsing issues and keeps headers structured
- Future formats (e.g., JSON move tree) can be added via the `format` parameter

### Get game count
```
GET /databases/{databaseId}/games/count
```

**Response:**
```json
{
  "count": 8500000
}
```

---

## Design Decisions

### GameHeaderModel as DTO
✅ **Used directly** - `GameHeaderModel` works well as a DTO:
- Simple POJO with standard getters
- All fields are basic types (String, Integer, Date, enums)
- Jackson serializes it automatically to JSON

### GameMovesModel Representation
📝 **Converted to PGN** - The moves tree is complex, so we:
- Use `PgnExporter.exportMovesOnly()` to get just the moves
- Return moves as a string in PGN notation
- Keep headers separate as structured JSON (no duplication)
- Support future formats via the `format` query parameter

### Cursor-Based Pagination
✅ **Simple and efficient**:
- Cursor is just the game ID to start from
- Games are naturally ordered by ID in the database
- No offset calculation needed
- Consistent results even if games are added during pagination
- The `nextCursor` value is provided in the response

---

## Future Enhancements

### Additional Formats
The `format` parameter can be extended to support:
- `format=json`: Structured JSON representation of the move tree
- `format=san`: Move list in SAN notation only
- `format=full-pgn`: Complete PGN with headers

### Content Negotiation
Alternatively, use Accept headers:
- `Accept: application/x-chess-pgn` → PGN format
- `Accept: application/json` → JSON format
