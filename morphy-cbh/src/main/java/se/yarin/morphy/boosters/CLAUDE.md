# se.yarin.morphy.boosters

Cross-reference indexes ("search boosters") that map between games and entities for fast lookups. These correspond to the `.cit`, `.cib`, `.cit2`, `.cib2` files in the ChessBase format.

## Key Classes

- **GameEntityIndex** - Main facade. Manages two ItemStorages: CIT (game-entity index) and CIB (index blocks). Handles PRIMARY_TYPES (Player, Tournament, Team, Source, Annotator) and SECONDARY_TYPES (GameTag).
- **GameEvents** - Encodes which pieces moved in a game into a compact 52-byte bitset representation. Analyzes `GameMovesModel` to extract piece movement events. Stored in `.cbb` files.
- **GameEventStorage** - Persistent storage for GameEvents data.
- **IndexHeader / IndexItem** - Header and entry for the game-entity index table.
- **IndexBlockHeader / IndexBlockItem** - Hierarchical block-based indexing structure for efficient range queries.
- **IndexBlockSerializer / IndexSerializer** - Binary serialization for index data.

## Design

- Separates primary types (stored in `.cit`/`.cib`) from secondary types (stored in `.cit2`/`.cib2`).
- Block-based hierarchical indexing enables efficient range scans when searching for games by entity.
- All index items are immutable (generated `Immutable*` classes).
