# morphy-tools

Development and exploration utilities for working with ChessBase databases and the morphy library. These are internal ad-hoc tools, not production code.

## Running Tools

```bash
mvn exec:java -pl morphy-tools -Dexec.mainClass="se.yarin.morphy.tools.<ToolName>" -Dexec.args="<args>"
```

## Active Tools

- **GenerateTestDatabase** - Creates a test database with games featuring various annotation types. Used for validating round-trip consistency.
- **TestPgnRoundTrip** - Validates PGN export/import by converting CBH games to PGN and back, comparing results.
- **QueryTest** - Tests query planning and optimization. Generates HTML visualizations of query plans and execution metrics.
- **InspectUnknownFields** - Scans databases for games with non-zero unknown fields in extended headers. Useful for reverse-engineering the file format.
- **ExploreCbmFile** - Analyzes ChessBase media (CBM) file structure and manifest data.
- **FileSearch** - Binary file analysis for finding byte sequences and encryption keys in ChessBase executables.
- **RipDbHeaders** - Backs up file headers from CBH/CBG files for comparative analysis.
- **IOCPUPerformanceTest / FileChannelPerformanceTest** - Performance benchmarking utilities.

## Archived Tools

Legacy/experimental tools in `archived/` subdirectory (excluded from compilation).
