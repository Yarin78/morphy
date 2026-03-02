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

- **morphy-cbh**: Core library with chess logic and database API
- **morphy-cli**: Command-line interface using Picocli
- **morphy-tools**: Development utilities
- **morphy-service**: Spring Boot REST API
- **apps/search-tester**: React debug UI for the search API

This is an internal project! There is no need to keep things around for backward compatibility, unless explicitly told to do so.

## Coding Conventions

- **Google Java Format** for all code
- **Java 21 features**: records, switch expressions, pattern matching
- **Null safety**: Use `@NotNull`/`@Nullable` from JetBrains Annotations
- **Resource management**: Always use try-with-resources for AutoCloseable
- **Imports**: Always use import statements for classes rather than fully-qualified names in code (e.g., write `GameQueryBuilder` with an import, not `se.yarin.morphy.queries.filter.GameQueryBuilder` inline)
- **Immutability**: All entities use `@Value.Immutable` from Immutables library. Generated classes have `Immutable` prefix.
- **Transactions**: All database access happens through explicit read/write transactions with try-with-resources.

## Documentation

- Each module and key package has its own `CLAUDE.md` with detailed guidance
- Detailed docs in `morphy-cbh/docs/` (architecture, user guide, developer guide, file format spec)
