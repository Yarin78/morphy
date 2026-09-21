# The ChessBase file formats

Reverse-engineered documentation of the two on-disk formats ChessBase uses.
Neither is published; both were worked out by reading databases and by watching
what ChessBase does to them.

| | Files | Introduced | Documentation |
|---|---|---|---|
| **v1** | `.cbh` `.cbj` `.cbg` `.cba` `.cbp` `.cbt` `.cbc` `.cbs` `.cbe` `.cbl` | ChessBase 6 | [v1/spec](v1/spec) |
| **v2** | `.2cbh` `.2cbg` `.2cba` `.2lid` `.2lgd` `.2lcd` | ChessBase 17 | [v2/spec](v2/spec) |

A database is a set of files sharing one base name, and the two formats are
unrelated on disk: v2 is not an extension of v1 but a new design, with different
record layouts, a different move encoding and a different byte order. What
carries over is the shape of the data — the same entities, the same annotation
types, the same square numbering.

## Specification and notes

Each format is documented twice, for two different readers.

- **`spec/`** states what the format is. It is the reference to implement
  against, and it says **unknown** where something is not understood.
- **`notes/`** says how it came to be known: which database showed what, what
  was tried, what is still open. The evidence behind the specification.

Files come in pairs: `spec/2-moves.md` and `notes/2-moves.md` cover the same
file, and `spec/UNKNOWNS.md` gathers every **unknown** in one list.

[CLAUDE.md](CLAUDE.md) sets out how to write in each.

## State

Both formats are documented completely enough to implement a reader and a
writer. The Python implementation in [morphy-py](../morphy-py) follows v2, and the
Java library in [morphy-cbh](../morphy-cbh) implements v1.

The two are documented alike, and share the figure toolkit [figkit.py](figkit.py).
The v1 specification was rewritten from the older documents, which are kept in
[v1/old](v1/old) until nothing in them is missing from the new ones. Where the
two disagree, [v1/spec](v1/spec) is right: the differences are gathered in
[v1/notes](v1/notes).

Some of what was learnt about v2 does not carry over. In particular v1 sorts
entities by raw bytes, and only v2 uses the Windows string comparison.
