# The ChessBase file formats

Reverse-engineered documentation of the two on-disk formats ChessBase uses.
Neither is published; both were worked out by reading databases and by watching
what ChessBase does to them.

| | Files | Introduced | Documentation |
|---|---|---|---|
| **v1** | `.cbh` `.cbg` `.cba` `.cbp` `.cbt` `.cbc` `.cbs` `.cbe` `.cbl` | ChessBase 6 | [v1](v1) |
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

**v2** is complete enough to implement a reader and a writer, and the Python
implementation in [morphy-py](../morphy-py) follows it.

**v1** is older documentation, written before the v2 work and in a different
style: specification and notes are mixed together, and it has no `UNKNOWNS.md`.
It awaits a rewrite into the shape above. Some of what the v2 work established
almost certainly applies to v1 as well — the entity sort order follows the
Windows string comparison rather than a byte comparison, for one — so parts of
it should be treated with suspicion until they are checked. The Java library in
[morphy-cbh](../morphy-cbh) implements v1.
