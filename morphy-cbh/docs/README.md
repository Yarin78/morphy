# Using the Morphy CBH library

This document describes basic usage of the Morphy CBH library. For more details, see the API documentation.

## Opening a database

Use the factory methods in the Database object to open or create a ChessBase database.
By default the database opens in read-write mode, but it can also be opened in read-only mode.

A database may have to be upgraded before being opened in read-write mode, or have to be repaired if there are inconsistencies.

## Working with a database

Most database operations happens inside a transaction. Transactions are created explicitly
using the DatabaseReadTransaction or DatabaseWriteTransaction constructor.
Creating a transaction acquires a lock, so it's important to close the transactions
when the operation is done. Transactions should typically be short lived to avoid blocking.
Use the _try-with-resources_ pattern when working with transactions.

There are some convience functions in the Database object for common operations. These will
create a transaction, apply the operation, and then close the transaction.

### Reading

Read transactions are used when you want to search or iterate over games or entities.
The transaction ensures that the database doesn't change while the iteration is ongoing.

### Writing

Write transactions batches multiple write operations. The writes are not committed to the database
until an explicit commit operation to the transaction has been done.
The transaction still stays open after the commit, allowing further write operations to take place.

If you enable auto-commit in a write transaction, every write operation will be immediately flushed to disk.
If you're adding a large number of games to a database, it's much more efficient
to accumulate larger changes before committing.

### Concurrency and transactions

Multiple read transactions can be open at the same time.
A single write transaction can be open simultaneously with the read transactions.
The changes made to the write transaction are isolated until commit. The commit can
only be executed when there are no ongoing read transactions.

The write transaction is not a pure ACID transaction; if something goes wrong when
committing it to the database (e.g. IO failure), the database may end up in an inconsistent state.

## Working with entities

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

### Reading

Create a new EntityIndexTransaction using beginReadTransaction.
Use the iterate() or stream() methods to scan entities. You can either scan them
by id-order or by their default sorting order (e.g. by last name for the player entity type).

### Writing

You should very rarely write directly in an Entity transaction as it
can easily make the database end up in an inconsistent state.
Prefer using the updateEntityById methods in the DatabaseWriteTransaction instead.
