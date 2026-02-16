# Search Tester

A React app for debugging and testing the morphy-service game search API.

## Prerequisites

- morphy-service running on `http://localhost:8080`
- Node.js 18+

## Run

```bash
# From repo root
cd apps/search-tester
npm install
npm run dev
```

The app runs on `http://localhost:5173` and proxies `/api` requests to the morphy-service.

## Usage

1. Select a database from the dropdown (e.g. `world-ch` when using the test database)
2. Use the filter query language or typed parameters to build your search
3. Click **Search** to execute
4. View results and the debug panel for raw request/response

## Filter Query Language

Examples (from morphy-service docs):

- `result:1-0` — White wins
- `rating:2600..2800,mode=any` — Either player 2600–2800
- `player.name:Carlsen,position=white` — White player name contains "Carlsen"
- `result:1-0 AND eco:B90` — Combined filters
