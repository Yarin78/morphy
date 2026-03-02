# se.yarin.util

Low-level I/O, parsing, and caching utilities.

## I/O Utilities

- **BlobChannel** - Interface for sequential/random access to blob files. Factory methods for creating instances. Abstracts file reading/writing.
- **BlobChannelImpl / PagedBlobChannel** - Implementations with paging support.
- **ByteBufferBitReader / ByteBufferBitWriter** - Bit-level I/O for compact binary encoding (used by move serialization).
- **ByteBufferUtil** - ByteBuffer manipulation helpers (endianness, alignment, string encoding).

## Caching

- **SimpleLRUCache\<K,V\>** - LRU cache using LinkedHashMap in access-order mode. Tracks hits/misses with debug logging.

## Expression Parser (parser/ sub-package)

Recursive descent parser for the filter expression DSL used by queries:
- **Scanner** - Lexical analysis (tokenization).
- **Parser** - Recursive descent parser (logical_or -> logical_and -> equality -> comparison -> term -> factor -> unary -> primary).
- **Token / TokenType** - Token representation.
- **Expr** - AST node types.
- **Interpreter** - AST evaluation.
- **AstPrinter** - AST visualization for debugging.

## Other

- **Collections** - Collection utilities (permutation generation).
