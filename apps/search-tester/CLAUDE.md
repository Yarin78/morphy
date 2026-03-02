# search-tester

React web app for debugging and testing the morphy-service game search API. Developer-focused tool for crafting queries, exploring entities, and visualizing query execution plans.

## Tech Stack

- **React 19** with TypeScript
- **Vite** for build/dev server
- **dagre** for query plan graph layout

## Dev Setup

```bash
npm install
npm run dev          # Vite dev server on localhost:5173, proxies /api to localhost:8080
npm run build        # Production build
npm run lint         # ESLint
```

Requires morphy-service running on port 8080. Vite dev proxy configured in `vite.config.ts`.

## Structure

```
src/
  App.tsx                  # Main state orchestration
  SearchPanel.tsx          # Database selector, entity type switcher, filter input, saved searches
  ResultsSection.tsx       # Pagination and results container
  ResultsTable.tsx         # Dynamic table with sortable columns per entity type
  QueryPlanVisualiser.tsx  # Dagre graph of query execution plans
  CollapsiblePanel.tsx     # Reusable collapsible UI panel
  entityConfig.ts          # Entity metadata: columns, sort options, fetch functions
  savedSearchTypes.ts      # Search persistence types
  api/
    client.ts              # API client with search/list/fetch functions for all 7 entity types
    types.ts               # DTOs matching morphy-service API
  hooks/
    useLocalStorage.ts     # localStorage persistence hook
```

## Features

- Search across 7 entity types: Games, Players, Tournaments, Annotators, Sources, Teams, GameTags
- Filter expression DSL (e.g., `result:1-0 AND rating:2600.. AND player.name:Carlsen`)
- Query plan visualization with cost estimates and execution metrics
- Save/load searches via localStorage
- Sortable columns, configurable column visibility, pagination
- Debug panel showing raw request/response JSON
