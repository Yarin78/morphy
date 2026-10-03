# Test Databases

This directory contains sample ChessBase databases used for development and testing of the Morphy service.

## world-ch

The **World Chess Championships** database contains historical games from World Championship matches (approximately 500 games). This database is used:

- By the morphy-service for local development and testing
- As a real database you can interact with when running the service locally

### Important Notes

1. **Separate from test resources**: This is a copy of the database found in `morphy-cbh/src/test/resources/se/yarin/morphy/database/World-ch/`

2. **Different purposes**:
   - **Test resources** (`morphy-cbh/src/test/resources`): Frozen database for unit tests that need stable content
   - **This directory**: Working database that can be modified and expanded for development

3. **Feel free to modify**: You can add more games, update data, or make changes to this database. Changes here won't affect the unit tests in morphy-cbh.

## wch2

The same World Championship database, converted by ChessBase to the **v2**
format (the `.2cbh` family). It is the database the v2 specification in
[../format/v2](../format/v2) was worked out against: because the game ids match
the v1 copy one for one, a field could be confirmed by decoding both and
requiring them to agree.

It is read by the Java v2 implementation in [../morphy-cb2](../morphy-cb2), whose
tests compare it game by game with the v1 copy, and by the Python tooling in
[../morphy-py](../morphy-py). `databases.json` registers it with the service,
read-only.

## annotations

53 games from Mega Database 2026, in the **v2** format, picked to have at least
three games with every kind of annotation: text before and after moves (also in
other languages than English), symbols (also before the move), squares, arrows
(also in uncommon colors), clocks, time spent, time control, evaluations (also
mate, and with a search depth: games 51-53), critical positions, medals, pawn
structure, piece path, variation color, video stream time, web links and game
quotations. Mega has no correspondence annotations, and no clocks on a move
rather than the game; training annotations were not looked for.

It was made with `PickAnnotatedGames` in morphy-tools, for testing how the
frontend shows annotations.

## scratch

Not in the repository. Small hand-made databases — `probe`, `reveng1`, `1tour`,
`2tour`, `empty` — built in ChessBase to answer one question each, and cited
throughout the v2 documentation. `sync.sh` fetches them from the Windows machine
they were made on.

## Configuration

The `databases.json` file maps database IDs to their file paths. The morphy-service loads this configuration on startup (configured in `application.properties`).

Example:
```json
{
  "world-ch": {
    "displayName": "World Chess Championships",
    "path": "test-databases/world-ch/World-ch.cbh"
  }
}
```

You can add more databases to this file as needed.
