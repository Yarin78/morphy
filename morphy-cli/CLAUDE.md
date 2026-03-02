# morphy-cli

Command-line interface for ChessBase database operations, built with Picocli.

## Build & Run

```bash
mvn package -pl morphy-cli              # Build JAR with dependencies
java -jar morphy-cli/target/morphy-cli-*.jar <command> [options]
```

## Commands

**Root command**: `ChessBaseCommand` with subcommands:

### `games` - Query and export games
- Filter games with expression DSL: `"result:1-0 AND player.name:Carlsen AND date:2020.."`
- Export to `.cbh` (ChessBase) or `.pgn` with `-o`
- PGN options: `--pgn-headers`, `--pgn-annotations`, `--pgn-comment-language`
- `--columns` for custom column display (50+ available columns, supports `+/-` prefixes)
- `--stats` for aggregate statistics instead of listing
- `--limit N` to cap output (default: 50 for stdout)
- `--id N` to get a specific game by ID
- Debug: `--raw-col-cbh`, `--raw-col-cbj`, `--raw-cbh` for binary inspection

### `players` - Query player entities
- Filter with expression DSL: `"name:Carlsen"`
- `--limit N` (default: 20), `--count-all`, `--hex`

### `tournaments` - Query tournament entities
- Filter: `"name:Candidates AND type:swiss AND date:2020.."`
- `--columns`, `--sorted`, `--raw-col`, `--raw`

### `check` - Validate database integrity
- Checks entity consistency, sort order, game headers, moves, annotations
- Selective checks with `--no-players`, `--no-games`, `--no-load-games`, etc.

## Structure

- **commands/** - Picocli command implementations
- **columns/** - 40+ output column definitions for table formatting
- **games/** - Game processing utilities
- **queries/** - Query execution adapter (`QueryAdapter` bridges QueryOperator with CLI output)
- **tournaments/** - Tournament processing utilities

## Common Options

All commands accept:
- `<database>` - CBH file, folder (with `-R` for recursive), or `.txt` file list
- `-v` - Verbose logging (use twice for debug)
- `--iostats` - Show I/O performance instrumentation

## Base Class

`BaseCommand` provides shared functionality: file/folder input handling, database stream processing, global options.
