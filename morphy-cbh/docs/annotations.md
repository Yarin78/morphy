# Annotations

Game annotations are stored in the .cba file in a similar way as the <a href="moves.md">moves data</a>.

## CBA file format

The file starts with a short <a href="#cba_header">header</a> containing metadata about the file itself.
The size of the header varies and is given in the first word.

### <a name="cba_header">CBA file header</a>

The header of the .cba file is the same as the [header in the .cbg file](moves.md#cbg_header).

| Offset | Bytes | Description
| --- | --- | ---
| 0   | 2   | Length of this header. Typically `26`, but in very old databases (created in CB6) it can be `10`.
| 2   | 4   | Total file size
| 6   | 4   | Number of unused bytes due to "holes" between games (number of bytes that would be saved when compacting the database)
| 10  | 8   | Total file size (64-bit version)
| 18  | 8   | Number of unused bytes due to "holes" between games (64-bit version)
