# Morphy CBH Library Documentation

Morphy is a Java library for working with ChessBase databases (CBH format). It allows you to read and write games to the database in isolated transactions and make complex database queries.

## Documentation Overview

### For Library Users

- **[User Guide](USER-GUIDE.md)**: Comprehensive guide for using the Morphy library in your applications. Covers database operations, working with games and entities, querying, and the chess core library. *Start here if you're integrating Morphy into your project.*

### For Library Developers

- **[Developer Guide](DEVELOPER-GUIDE.md)**: Guide for contributors to the Morphy library. Covers development setup, codebase structure, testing, and how to add new features.

- **[Architecture](ARCHITECTURE.md)**: System architecture documentation. Explains the layered design, component interactions, data flow, concurrency model, and key design decisions.

### Package Documentation

Detailed documentation for each package:

- **[`se.yarin.chess`](chess-package.md)**: Core chess functionality for representing positions, moves, and games. This package is independent of any database format and can be used as a standalone chess library.

- **[`se.yarin.morphy`](morphy-package.md)**: Modern API for working with ChessBase databases. Provides transaction-based, type-safe interfaces for reading and writing chess games and entities.

### File Format Specification

- **[CBH Format](cbh-format/README.md)**: Reverse-engineered specification of the ChessBase database format, including game headers, moves, annotations, and entity indexes.

---

## Quick Start

```java
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.games.Game;

try (Database db = Database.open(new File("games.cbh"))) {
    try (DatabaseReadTransaction txn = db.beginReadTransaction()) {
        Game game = txn.getGame(1);
        System.out.println(game.white().fullName() + " vs " + game.black().fullName());
    }
}
```

See the [User Guide](USER-GUIDE.md) for complete examples.

---

## Basic Concepts

The following sections provide a quick overview of key concepts. For detailed explanations and code examples, see the [User Guide](USER-GUIDE.md).

### Opening a database

Use the factory methods in the Database object to open or create a ChessBase database.
By default the database opens in read-write mode, but it can also be opened in read-only mode.

A database may have to be upgraded before being opened in read-write mode, or have to be repaired if there are inconsistencies.

### Working with a database

Most database operations happens inside a transaction. Transactions are created explicitly
using the DatabaseReadTransaction or DatabaseWriteTransaction constructor.
Creating a transaction acquires a lock, so it's important to close the transactions
when the operation is done. Transactions should typically be short lived to avoid blocking.
Use the _try-with-resources_ pattern when working with transactions.

There are some convience functions in the Database object for common operations. These will
create a transaction, apply the operation, and then close the transaction.

**Reading:** Read transactions are used when you want to search or iterate over games or entities. The transaction ensures that the database doesn't change while the iteration is ongoing.

**Writing:** Write transactions batch multiple write operations. The writes are not committed to the database until an explicit commit operation. The transaction stays open after commit, allowing further write operations. If you enable auto-commit, every write operation is immediately flushed to disk.

**Concurrency:**

Multiple read transactions can be open at the same time.
A single write transaction can be open simultaneously with the read transactions.
The changes made to the write transaction are isolated until commit. The commit can
only be executed when there are no ongoing read transactions.

The write transaction is not a pure ACID transaction; if something goes wrong when
committing it to the database (e.g. IO failure), the database may end up in an inconsistent state.

### Working with entities

Games are the first class citizens in a ChessBase database.
Each game refer to entities stored in entity indexes, such as Players,
Tournaments, Annotators etc. These can be thought of as second class
citizens. An entity can't exist without a game referencing to it;
this would be considered a database error. Therefore you should never add entities explicitly.

There are cases though when you may have to update the metadata of some entity.
Or search or iterate through large number of entities. These type of operations
require that you work with EntityIndex transactions. They work similarly to Database transactions,
and share the same concurrency mechanism.

Make sure to use the _try-with-resources_ pattern to ensure the transactions are closed!

**Reading:** Create a new EntityIndexTransaction using `beginReadTransaction()`. Use the `iterate()` or `stream()` methods to scan entities by id-order or by their default sorting order (e.g., by last name for players).

**Writing:** You should rarely write directly in an Entity transaction as it can easily make the database inconsistent. Prefer using the `updateEntityById` methods in the `DatabaseWriteTransaction` instead.
