# Annotators

Annotator entities are stored in the .cbc file; see [Entities](entities.md) for the initial structure of this file.
The size of an annotator entity record is always `53`.

All integers are stored in Little Endian unless otherwise specified.

| Offset | Bytes | Description
| --- | --- | ---
| 0 | 45 | Full name of the annotator
| 45 | 4 | Number of references to this annotator.
| 49 | 4 | The id of the first game in the database with this annotator.
