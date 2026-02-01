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
