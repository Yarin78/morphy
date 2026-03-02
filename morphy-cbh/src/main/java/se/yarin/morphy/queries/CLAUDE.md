# se.yarin.morphy.queries

Query engine with cost-based optimization for searching games and entities.
For details, see `morphy-cbh/docs/QUERY-ENGINE.md`

## Query Construction

- **GameQuery** - Immutable query object with gameFilters, entityJoins, sortOrder, limit. Main entry point for game searches.
- **EntityQuery / SourceQuery / EntitySourceQuery / GameSourceQuery** - Query variants for different entity types.
- **GameQueryBuilder** - Fluent builder that parses filter expression strings into GameQuery objects. Maps 30+ filter fields (result, date, rating, eco, medals, moves, etc.) to GameFilter instances. Handles entity shorthand (e.g., `player` -> `player.name`).
- **GameEntityJoin / GameEntityJoinCondition** - Join specifications between games and entities.

## Filter Builders (filter/ sub-package)

- **PlayerQueryBuilder, TournamentQueryBuilder, SourceQueryBuilder, AnnotatorQueryBuilder, GameTagQueryBuilder** - Parse filter expressions for each entity type. All extend `AbstractEntityQueryBuilder`.

## Query Planning

- **QueryPlanner** - Cost-based optimizer that samples games/entities to estimate selectivity and choose optimal join strategy (hash join vs loop join vs merge join).
- **QueryContext** - Execution context for query planning and running.
- **QueryCost / OperatorCost** - Cost estimation models.
- **IntBucketDistribution / StringDistribution** - Statistical distributions used for selectivity estimation.

## Query Operators (operations/ sub-package)

30+ operator implementations forming the query execution plan:
- **Scans**: `GameTableScan`, `EntityTableScan`
- **Lookups**: `GameLookup`, `EntityLookup`, `GameMovesInfoLookup`, `TournamentExtraLookup`
- **Joins**: `GameEntityHashJoin`, `GameEntityLoopJoin`, `HashJoin`, `MergeJoin`
- **Filtering**: Entity-specific filter operators
- **Other**: `Distinct`, `Limit`, `Sort`, `Manual`
- **Base**: `QueryOperator` with `QueryData` result type

## Visualization (visualisation/ sub-package)

- **QueryPlanGraphBuilder** - Builds graph representation of query plans.
- **QueryDescriptionFormatter / QueryResultFormatter** - Formats query info for display.
- **QueryVisualiser** - Generates visual output of query execution plans (used by search-tester app).

## Design

- Cost-based optimization with database sampling (2500 sample batches, 20 items per batch).
- Multiple join strategies selected based on estimated costs.
- Supports both simple filter-only queries and complex multi-entity join queries.
