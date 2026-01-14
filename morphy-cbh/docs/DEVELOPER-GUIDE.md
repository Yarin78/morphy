# Morphy Developer Guide

This guide is for developers contributing to the Morphy library itself. It covers the codebase structure, development workflow, design patterns, and conventions used throughout the project.

## Table of Contents

1. [Development Setup](#development-setup)
2. [Project Structure](#project-structure)
3. [Package Overview](#package-overview)
4. [Key Design Patterns](#key-design-patterns)
5. [Working with the Codebase](#working-with-the-codebase)
6. [Adding New Features](#adding-new-features)
7. [Testing](#testing)
8. [Code Style and Conventions](#code-style-and-conventions)
9. [Common Development Tasks](#common-development-tasks)

---

## Development Setup

### Prerequisites

- **Java 11+** (Java 17 recommended)
- **Maven 3.6+**
- **Git**

### Building the Project

```bash
# Clone the repository
git clone <repository-url>
cd morphy

# Build the project
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run only tests
mvn test
```

### IDE Setup

The project uses:
- **Lombok** for boilerplate reduction
- **Immutables** for value objects

Ensure your IDE has annotation processing enabled:

**IntelliJ IDEA:**
1. Install Lombok plugin
2. Enable annotation processing: Settings → Build → Compiler → Annotation Processors → Enable

**Eclipse:**
1. Install Lombok: Run `java -jar lombok.jar`
2. Enable annotation processing in project properties

### Key Dependencies

| Dependency | Purpose |
|------------|---------|
| Lombok | Reduce boilerplate (`@Getter`, `@Slf4j`, etc.) |
| Immutables | Generate immutable value objects |
| JUnit 5 | Testing framework |
| Mockito | Mocking in tests |
| Log4j2 | Logging |
| Jetbrains Annotations | Nullability annotations |

---

## Project Structure

```
morphy/
├── morphy-cbh/                    # Main library module
│   ├── src/main/java/
│   │   ├── se/yarin/chess/        # Chess core (format-independent)
│   │   ├── se/yarin/morphy/       # Database API (transaction-based)
│   │   ├── se/yarin/asflib/       # ChessBase script support
│   │   └── se/yarin/util/         # Shared utilities
│   ├── src/test/java/             # Test classes
│   ├── src/test/resources/        # Test databases and fixtures
│   └── docs/                      # Documentation
├── morphy-cli/                    # Command-line interface
├── morphy-tools/                  # Development/debug tools
└── pom.xml                        # Parent POM
```

---

## Package Overview

### `se.yarin.chess` - Chess Core

Format-independent chess functionality. No dependencies on database code.

```
se.yarin.chess/
├── Position.java              # Immutable board state
├── Move.java                  # Chess move with context
├── GameModel.java             # Complete game representation
├── GameHeaderModel.java       # Game metadata (players, result, etc.)
├── GameMovesModel.java        # Game tree with variations
├── NavigableGameModel.java    # Game with cursor navigation
├── Chess.java                 # Chess constants and utilities
├── Chess960.java              # Fischer Random support
├── annotations/               # Annotation types
│   ├── Annotation.java
│   ├── Annotations.java
│   ├── TextAfterMoveAnnotation.java
│   └── ...
└── (supporting classes)
```

**Key Invariants:**
- `Position` is immutable - `doMove()` returns a new Position
- `GameMovesModel` is a tree structure (not a list)
- The chess package has no database dependencies

### `se.yarin.morphy` - Modern API

Transaction-based, type-safe database operations.

```
se.yarin.morphy/
├── Database.java                   # Main entry point
├── DatabaseContext.java            # Shared context for database components
├── DatabaseReadTransaction.java    # Read-only transaction
├── DatabaseWriteTransaction.java   # Write transaction
├── entities/                       # Entity types and indexes
│   ├── Entity.java                 # Base entity interface
│   ├── Player.java                 # Player entity
│   ├── Tournament.java             # Tournament entity
│   ├── EntityIndex.java            # Entity index base
│   ├── PlayerIndex.java            # Player index
│   └── ...
├── games/                          # Game storage
│   ├── Game.java                   # Game reference
│   ├── GameHeader.java             # Core game metadata
│   ├── ExtendedGameHeader.java     # Extended metadata
│   ├── GameHeaderIndex.java        # Game header storage
│   ├── GameAdapter.java            # Database ↔ GameModel conversion
│   └── moves/                      # Move encoding
│       ├── MoveEncoder.java
│       ├── CompactMoveEncoder.java
│       └── ...
├── queries/                        # Query system
│   ├── GameQuery.java
│   ├── GameFilter.java
│   ├── PlayerFilter.java
│   └── ...
├── storage/                        # Low-level storage
│   ├── ItemStorage.java
│   ├── FileItemStorage.java
│   ├── BlobStorage.java
│   └── ...
├── boosters/                       # Search performance indexes
└── text/                           # Text/guiding text support
```

**Key Design:**
- Transactions provide isolation and consistency
- Entities are immutable (using Immutables library)
- Index structures use AVL-like binary trees

---

## Key Design Patterns

### 1. Immutable Entities

Entities use the Immutables library for type-safe immutability:

```java
@Value.Immutable
public interface Player extends Entity {
    String firstName();
    String lastName();
    @Nullable Nation nation();
    // ...

    default String fullName() {
        return firstName() + " " + lastName();
    }
}

// Usage - create new instances for modifications
Player updated = ImmutablePlayer.builder()
    .from(existing)
    .nation(Nation.fromIOC("USA"))
    .build();
```

### 2. Transaction Pattern

All database operations occur within transactions:

```java
// Read transaction - multiple can be open concurrently
try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
    // Read operations
}

// Write transaction - exclusive access
try (DatabaseWriteTransaction txn = db.beginWriteTransaction()) {
    // Write operations
    txn.commit();
}
```

**Transaction Lifecycle:**
1. `begin*Transaction()` acquires lock
2. Operations are isolated
3. `commit()` flushes changes (write only)
4. `close()` releases lock

### 3. Entity Index Structure

Entity indexes use a binary tree with:
- Fast lookup by ID (O(1) via array)
- Ordered iteration by natural sort (O(log n) traversal)
- Support for prefix searches

### 4. Storage Abstraction

Storage is abstracted through interfaces:

```java
public interface ItemStorage<T> {
    T getItem(int id);
    void putItem(int id, T item);
    int count();
    // ...
}
```

Implementations:
- `FileItemStorage` - Disk-backed
- `InMemoryItemStorage` - Memory-only
- `ItemStorageFilter` - Filtered view

### 5. Move Encoding

Moves are encoded compactly for storage:

```java
public interface MoveEncoder {
    void encode(Move move, ByteBuffer buffer);
    Move decode(Position position, ByteBuffer buffer);
}
```

`CompactMoveEncoder` uses variable-length encoding for efficiency.

---

## Working with the Codebase

### Finding Key Entry Points

| To Work On | Start Here |
|------------|------------|
| Database operations | `se.yarin.morphy.Database` |
| Reading/writing games | `DatabaseReadTransaction`, `DatabaseWriteTransaction` |
| Game representation | `se.yarin.chess.GameModel` |
| Position logic | `se.yarin.chess.Position` |
| Entity management | `se.yarin.morphy.entities.*Index` |
| File format | `se.yarin.morphy.storage.*` |
| Move encoding | `se.yarin.morphy.games.moves.*` |
| Annotations | `se.yarin.chess.annotations.*` |

### Understanding the Data Flow

**Reading a game:**
```
Database.beginReadTransaction()
    → DatabaseReadTransaction.getGame(id)
    → GameHeaderIndex.get(id) → GameHeader
    → Game.getModel()
        → MoveRepository.getMovesBlob()
        → MoveSerializer.deserialize()
        → AnnotationRepository.getAnnotationsBlob()
        → AnnotationSerializer.deserialize()
    → GameModel (returned to caller)
```

**Writing a game:**
```
Database.beginWriteTransaction()
    → DatabaseWriteTransaction.addGame(GameModel)
    → GameAdapter.encode(GameModel)
        → Resolve entities (create if needed)
        → Encode moves → MoveRepository
        → Encode annotations → AnnotationRepository
    → GameHeaderIndex.add(header)
    → commit() → flush all changes
```

### Debugging Tips

1. **Enable debug logging:**
   ```java
   // Set in log4j2.xml or programmatically
   Logger logger = LogManager.getLogger(Database.class);
   ```

2. **Use in-memory databases for testing:**
   ```java
   Database db = Database.openInMemory(new File("test.cbh"));
   ```

3. **Inspect raw data:**
   ```java
   Game game = txn.getGame(id);
   ByteBuffer moves = game.getMovesBlob();
   ByteBuffer annotations = game.getAnnotationsBlob();
   ```

---

## Adding New Features

### Adding a New Entity Type

1. **Define the entity interface:**
   ```java
   // se/yarin/morphy/entities/NewEntity.java
   @Value.Immutable
   public interface NewEntity extends Entity {
       String name();
       // ... other properties
   }
   ```

2. **Create the index class:**
   ```java
   // se/yarin/morphy/entities/NewEntityIndex.java
   public class NewEntityIndex extends EntityIndex<NewEntity> {
       // Implement storage and serialization
   }
   ```

3. **Add to Database:**
   ```java
   // In Database.java
   private final NewEntityIndex newEntityIndex;

   public NewEntityIndex newEntityIndex() {
       return newEntityIndex;
   }
   ```

4. **Add transaction support:**
   ```java
   // In DatabaseReadTransaction.java
   public NewEntity getNewEntity(int id) { ... }
   public EntityIndexReadTransaction<NewEntity> newEntityTransaction() { ... }
   ```

5. **Write tests**

### Adding a New Query Filter

1. **Create filter class:**
   ```java
   // se/yarin/morphy/queries/filters/NewFilter.java
   public class NewFilter implements GameFilter {
       @Override
       public boolean matches(Game game) {
           // Filter logic
       }

       @Override
       public boolean matchesSerialized(GameHeader header) {
           // Fast path using only header data
       }
   }
   ```

2. **Add tests**

### Adding a New Annotation Type

1. **Define annotation class:**
   ```java
   // se/yarin/chess/annotations/NewAnnotation.java
   public class NewAnnotation extends Annotation {
       private final String data;

       public NewAnnotation(String data) {
           this.data = data;
       }

       public String getData() {
           return data;
       }
   }
   ```

2. **Add serialization in `AnnotationSerializer`**

---

## Testing

### Test Structure

```
src/test/java/
├── se/yarin/chess/           # Chess core tests
├── se/yarin/morphy/          # Database API tests
└── se/yarin/util/            # Utility tests

src/test/resources/
├── databases/                # Test ChessBase databases
└── pgn/                      # Test PGN files
```

### Writing Tests

```java
@Test
void shouldReadGameFromDatabase() throws Exception {
    // Use test resources
    File testDb = new File(getClass()
        .getResource("/databases/test.cbh").toURI());

    try (Database db = Database.open(testDb)) {
        try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
            Game game = txn.getGame(1);
            assertThat(game.white().lastName()).isEqualTo("Kasparov");
        }
    }
}

@Test
void shouldAddGameToDatabase() throws Exception {
    // Use temporary database
    File tempDb = Files.createTempFile("test", ".cbh").toFile();
    tempDb.deleteOnExit();

    try (Database db = Database.create(tempDb)) {
        try (DatabaseWriteTransaction txn = db.beginWriteTransaction()) {
            GameModel game = createTestGame();
            int id = txn.addGame(game);
            txn.commit();

            assertThat(id).isEqualTo(1);
        }
    }
}
```

### Running Tests

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=DatabaseTest

# Specific test method
mvn test -Dtest=DatabaseTest#shouldReadGameFromDatabase

# With coverage
mvn test jacoco:report
```

### Test Categories

- **Unit tests**: Test individual classes in isolation
- **Integration tests**: Test database operations with real files
- **Format tests**: Verify compatibility with ChessBase files

---

## Code Style and Conventions

### General Conventions

1. **Immutability preferred**: Use immutable objects where possible
2. **Null safety**: Use `@Nullable` and `@NotNull` annotations
3. **Resource management**: Always use try-with-resources
4. **Logging**: Use Slf4j with `@Slf4j` annotation

### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Interface | No prefix | `Entity`, `GameFilter` |
| Implementation | Descriptive suffix | `FileItemStorage`, `CompactMoveEncoder` |
| Immutable impl | `Immutable` prefix (generated) | `ImmutablePlayer` |
| Test class | `Test` suffix | `DatabaseTest` |
| Constants | UPPER_SNAKE_CASE | `MAX_GAMES` |

### Code Organization

```java
public class Example {
    // Static constants
    private static final int MAX_SIZE = 100;

    // Instance fields (prefer final)
    private final Database database;
    private final Logger log = LoggerFactory.getLogger(Example.class);

    // Constructor
    public Example(Database database) {
        this.database = database;
    }

    // Public methods
    public void doSomething() { }

    // Package-private methods
    void internalMethod() { }

    // Private methods
    private void helper() { }
}
```

### Documentation

- **Public API**: Document all public classes and methods
- **Internal code**: Document non-obvious logic
- **Complex algorithms**: Explain the approach

```java
/**
 * Encodes chess moves into a compact binary format.
 *
 * <p>The encoding uses variable-length bytes to minimize storage:
 * <ul>
 *   <li>Common moves: 1-2 bytes</li>
 *   <li>Promotions: 2 bytes</li>
 *   <li>Special moves (castling, en passant): 2 bytes</li>
 * </ul>
 */
public class CompactMoveEncoder implements MoveEncoder {
```

---

## Common Development Tasks

### Updating the CBH Format Support

When ChessBase releases a new version:

1. **Analyze new format** using hex dumps and comparison
2. **Update format docs** in `docs/cbh-format/`
3. **Implement changes** in storage/serialization classes
4. **Add version detection** if format changed
5. **Test with new databases**

### Adding Database Validation

```java
// In se/yarin/morphy/validation/
public class NewValidator implements Validator {
    @Override
    public List<ValidationError> validate(Database db) {
        List<ValidationError> errors = new ArrayList<>();
        // Validation logic
        return errors;
    }
}
```

### Performance Profiling

1. Use JMH for microbenchmarks
2. Profile with VisualVM or JProfiler
3. Focus on hot paths: move encoding, game iteration

### Releasing

1. Update version in `pom.xml`
2. Update CHANGELOG
3. Run full test suite
4. Build and verify: `mvn clean verify`
5. Tag release in git

---

## Further Reading

- [Architecture Overview](ARCHITECTURE.md) - System design
- [User Guide](USER-GUIDE.md) - Library usage
- [CBH Format Specification](cbh-format/README.md) - File format details
