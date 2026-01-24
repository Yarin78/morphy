# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
mvn clean install          # Build all modules
mvn test                   # Run all tests
mvn test -pl morphy-cbh    # Run tests for core library only
mvn test -Dtest=ClassName  # Run single test class
mvn package -pl morphy-cli # Build CLI JAR with dependencies
```

## Project Overview

Morphy is a Java 21 library and CLI for reading/writing ChessBase databases (.cbh binary format). It uses Maven 3.6+ with three modules:

- **morphy-cbh**: Core library with chess logic and database API
- **morphy-cli**: Command-line interface using Picocli
- **morphy-tools**: Development utilities

## Architecture

Three-layer design:

1. **Chess Core** (`se.yarin.chess`): Format-independent chess logic (Position, Move, GameModel)
2. **Database API** (`se.yarin.morphy`): Transaction-based database access (Database, transactions, entities, queries)
3. **Storage Layer** (`se.yarin.morphy.storage`): Low-level file I/O abstraction

Key patterns:
- **Immutability**: All entities use `@Value.Immutable` from Immutables library. Generated classes have `Immutable` prefix.
- **Transactions**: All database access happens through explicit read/write transactions with try-with-resources.
- **Position immutability**: `Position.doMove()` returns a new Position, never modifies the existing one.

## Key Entry Points

- `Database.java` - Main facade for opening/creating databases
- `DatabaseReadTransaction` / `DatabaseWriteTransaction` - All database operations
- `Position.java` - Immutable board state with Zobrist hashing
- `GameModel.java` - Complete game (header + move tree)

## Coding Conventions

- **Google Java Format** for all code
- **Java 21 features**: records, switch expressions, pattern matching
- **Null safety**: Use `@NotNull`/`@Nullable` from JetBrains Annotations
- **Resource management**: Always use try-with-resources for AutoCloseable
- No Lombok (recently removed)

## ChessBase File Format

A database consists of multiple files with same base name:
- **Mandatory**: `.cbh` (headers), `.cbg` (moves), `.cba` (annotations), `.cbp` (players), `.cbt` (tournaments), `.cbc` (annotators), `.cbs` (sources)
- **Optional**: `.cbj` (extended headers), `.cbe` (teams), `.cbl` (tags)

## Documentation

Detailed documentation in `morphy-cbh/docs/`:
- `ARCHITECTURE.md` - System design
- `USER-GUIDE.md` - Library usage
- `DEVELOPER-GUIDE.md` - Contributing guide
- `cbh-format/` - Reverse-engineered file format specification
