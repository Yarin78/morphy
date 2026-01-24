---
name: morphy-tools
description: Run development tools from the se.yarin.morphy.tools package using Maven exec:java.
---

# morphy-tools

Run development tools from the `se.yarin.morphy.tools` package.

## Usage

```
/morphy-tools <tool-name> [arguments...]
```

## Arguments

- `tool-name`: Either a file path like `morphy-tools/src/main/java/se/yarin/morphy/tools/TestPgnRoundTrip.java` or just the class name like `TestPgnRoundTrip`
- `arguments`: Any additional arguments to pass to the tool

## Available Tools

- `TestPgnRoundTrip` - Test PGN round-tripping on a database
- `ExploreCbmFile` - Explore CBM file contents
- `FileSearch` - Search files
- `QueryTest` - Test database queries
- `RipDbHeaders` - Extract database headers
- `GenerateTestDatabase` - Generate test databases

## Instructions

When this skill is invoked:

1. Parse the tool name from the first argument:
   - If it's a file path containing `.java`, extract the class name from it (e.g., `TestPgnRoundTrip.java` -> `TestPgnRoundTrip`)
   - Otherwise, use the argument directly as the class name

2. Run the tool using Maven exec:java from the project root:
   ```bash
   mvn -pl morphy-tools exec:java -Dexec.mainClass="se.yarin.morphy.tools.<ClassName>" -Dexec.args="<remaining-arguments>"
   ```
   Important: Make sure the binary isn't outdated! Re-build if necessary.

3. If there are no additional arguments, omit the `-Dexec.args` part.

4. Show the output to the user.

## Examples

```
/morphy-tools TestPgnRoundTrip /path/to/database.cbh
/morphy-tools TestPgnRoundTrip /path/to/database.cbh 42
```
