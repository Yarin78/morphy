# se.yarin.morphy.entities

Entity model and indexed access for all entity types in a ChessBase database.

## Entity Types

All entities extend `Entity` (abstract, `@Value.Immutable`) with `id()`, `count()`, `firstGameId()`:

- **Player** - `lastName`, `firstName`. Supports full name parsing ("Lastname, Firstname" format). Comparable for sorted indexes.
- **Tournament** - Tournament metadata (title, date, place, type, category, rounds, etc.)
- **Annotator** - Game annotator name.
- **Source** - Publication source with title, publisher, date, quality.
- **Team** - Team name and metadata.
- **GameTag** - Arbitrary game tags/labels.

## Index Classes

- **EntityIndex\<T\>** - Abstract generic base for entity indexes with storage, context, metrics, and version tracking.
- **PlayerIndex, TournamentIndex, SourceIndex, AnnotatorIndex, TeamIndex, GameTagIndex** - Concrete implementations.
- **EntityIndexReadTransaction / EntityIndexWriteTransaction** - Transaction wrappers for ACID access to entity data.
- **EntityNode** - Storage container for serialized entity data in the index tree.
- **NodePath** - Tree path representation for navigating entity hierarchies.

## Iteration

- **EntityBatchIterator** - Batch iteration over entities.
- **OrderedEntityAscendingIterator / OrderedEntityDescendingIterator** - Sorted iteration.

## Filters (filters/ sub-package)

40+ filter implementations for entity queries. All implement `EntityFilter` interface. Key filters:
- `PlayerNameFilter`, `PlayerFederationFilter`
- `TournamentTitleFilter`, `TournamentPlaceFilter`, `TournamentDateFilter`, `TournamentTypeFilter`
- `SourceTitleFilter`, `SourceQualityFilter`
- `CombinedFilter` for composing multiple filters.
