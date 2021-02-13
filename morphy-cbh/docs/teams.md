# Teams

Team entities are stored in the .cbe file; see [Entities](entities.md) for the initial structure of this file.
The size of a team entity record is always `63`.

All integers are stored in Little Endian unless otherwise specified.

| Offset | Bytes | Description
| --- | --- | ---
| 0 | 45 | Title
| 45 | 4 | Team number
| 47 | 1 | Bit 0: Season (if set, year is year/year+1, otherwise just year)
| 48 | 2 | Year
| 50 | 1 | [Nation](types.md#nation)
| 51 | 4 | Number of references to this team. If a team plays against itself in a game, this is counted twice.
| 55 | 4 | The id of the first game in the database with this team.
