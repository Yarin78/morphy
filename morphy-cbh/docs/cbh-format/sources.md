# Sources

Source entities are stored in the .cbs file; see [Entities](entities.md) for the initial structure of this file.
The size of a source entity record is always `59`.

All integers are stored in Little Endian unless otherwise specified.

| Offset | Bytes | Description
| --- | --- | ---
| 0 | 25 | Title
| 25 | 16 | Publisher
| 41 | 4 | Publication ([date](types.md#date))
| 45 | 4 | [Date](types.md#date)
| 49 | 1 | Version
| 50 | 1 | Estimation of the [quality](#source_quality) of the data (moves, results, names, spellings)
| 51 | 4 | Number of references to this source.
| 55 | 4 | The id of the first game in the database with this source.

## <a name="source_quality">Source Quality</a>

| Value | Description
| --- | ---
| 0 | Unset
| 1 | High
| 2 | Medium
| 3 | Low

