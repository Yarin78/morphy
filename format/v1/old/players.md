# Players

Player entities are stored in the .cbp file; see [Entities](entities.md) for the initial structure of this file.
The size of a player entity record is always `58`.

All integers are stored in Little Endian unless otherwise specified.

| Offset | Bytes | Description
| --- | --- | ---
| 0 | 30 | Last name
| 30 | 20 | First name
| 50 | 4 | Number of references to this player. In games were a player plays against themselves, this is counted twice.
| 54 | 4 | The id of the first game in the database with this player.

All other player information is fetched from the Player Encyclopedia.
