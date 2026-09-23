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

Morphy is a Java 21 library and CLI for reading/writing ChessBase databases (.cbh binary format). It uses Maven 3.6+ with these modules:

- **morphy-api**: Vendor-neutral layer shared by every format — the format-independent chess core (`se.yarin.chess`), the neutral DTO records (`se.yarin.morphy.model`), and the `Database` facade interface (`se.yarin.morphy.api`)
- **morphy-cbh**: The ChessBase v1 (`.cbh`) reader/writer and database API; its `DatabaseCbh` implements the `Database` facade directly and owns the v1↔DTO converters (`se.yarin.morphy.convert`)
- **morphy-cb2**: The ChessBase v2 (`.2cbh`) format — currently a stub, `Database2Cbh`, implementing the facade
- **morphy-cli**: Command-line interface using Picocli
- **morphy-tools**: Development utilities
- **morphy-service**: Spring Boot backend exposing the DTOs over HTTP for the NodeJS frontend

Module dependencies point downward only: everything depends on **morphy-api**, and the v1/v2 modules never depend on each other. A `DatabaseProvider` SPI + `Databases.open` factory dispatch to the right format by file extension.

This is an internal project! There is no need to keep things around for backward compatibility, unless explicitly told to do so.

## Architecture

Layered design:

1. **Chess Core** (`se.yarin.chess`, in morphy-api): Format-independent chess logic (Position, Move, GameModel)
2. **Neutral model** (`se.yarin.morphy.model` + `se.yarin.morphy.api`, in morphy-api): immutable DTO records and the `Database` facade — the vendor-neutral interface used by the CLI, the service (and its NodeJS frontend), and every format
3. **Database API** (`se.yarin.morphy`, in morphy-cbh): Transaction-based v1 access (Database, transactions, entities, queries)
4. **Storage Layer** (`se.yarin.morphy.storage`): Low-level file I/O abstraction

Key patterns:
- **Immutability**: All entities use `@Value.Immutable` from Immutables library. Generated classes have `Immutable` prefix.
- **Transactions**: All database access happens through explicit read/write transactions with try-with-resources.
- **Position immutability**: `Position.doMove()` returns a new Position, never modifies the existing one.

## Key Entry Points

- `Database` (morphy-api) - Vendor-neutral facade interface; open any format via `Databases.open(file)`
- `GameDto` (`se.yarin.morphy.model`) - The neutral game representation crossing the facade (moves as PGN)
- `DatabaseCbh.java` (morphy-cbh) - The v1 engine; implements the `Database` facade directly (also exposes v1-specific indexes/transactions/queries)
- `DatabaseReadTransaction` / `DatabaseWriteTransaction` - All v1 database operations
- `Position.java` - Immutable board state with Zobrist hashing
- `GameModel.java` - Complete game (header + move tree); an internal decode/encode structure, no longer the neutral interface

## Coding Conventions

- **Google Java Format** for all code
- **Java 21 features**: records, switch expressions, pattern matching
- **Null safety**: Use `@NotNull`/`@Nullable` from JetBrains Annotations
- **Resource management**: Always use try-with-resources for AutoCloseable
- **Imports**: Always use import statements for classes rather than fully-qualified names in code (e.g., write `GameQueryBuilder` with an import, not `se.yarin.morphy.queries.filter.GameQueryBuilder` inline)

## ChessBase File Format

A database consists of multiple files with same base name:
- **Mandatory**: `.cbh` (headers), `.cbg` (moves), `.cba` (annotations), `.cbp` (players), `.cbt` (tournaments), `.cbc` (annotators), `.cbs` (sources)
- **Optional**: `.cbj` (extended headers), `.cbe` (teams), `.cbl` (tags)

## Documentation

Library documentation in `morphy-cbh/docs/`:
- `ARCHITECTURE.md` - System design
- `USER-GUIDE.md` - Library usage
- `DEVELOPER-GUIDE.md` - Contributing guide

File format documentation in `format/`, one directory per format:
- `format/v1/` - the `.cbh` family, which this library implements
- `format/v1/notes/` - the evidence behind the v1 specification (not in the repository, see below)
- `format/v2/` - the `.2cbh` family, the reference to implement against
- `format/v2/notes/` - the evidence behind the v2 specification (not in the repository)
- `format/CLAUDE.md` - **read this before editing anything under `format/`**; it also says where the notes are

## Python tooling

`morphy-py/` holds a Python reader for the v2 format: the `cb2` package, plus
the `inspect` and `hexit` command line tools. It is separate from the Maven
build. Sample databases live in `test-databases/`.
