# Tournaments

Tournament entities are stored in the .cbt file; see [Entities](entities.md) for the initial structure of this file.
The size of a tournament entity record is always `90`.

All integers are stored in Little Endian unless otherwise specified.

| Offset | Bytes | Description
| --- | --- | ---
|  0 | 40 | The title of the tournament
| 40 | 30 | The name of the location
| 70 |  4 | The start <a href="types.md#date">date</a> of the tournament
| 74 |  1 | <a href="#tournament_type">Tournament type</a>; pairing system and time controls 
| 75 |  1 | Bit 0: set if team event; other bits unused.
| 76 |  1 | The country where the tournament was held, see <a href="types.md#nation">Nation</a>.
| 77 |  1 | Always `0`. Possibly the nation field is 16 bit.
| 78 |  1 | Category (0-99)
| 79 |  1 | <a href="#tournament_flags">Tournament flags</a>
| 80 |  1 | Number of rounds in the tournament
| 81 |  1 | Always `0`.
| 82 |  4 | Number of games in the database referencing this tournament
| 86 |  4 | The id of the first game in the database referencing this tournament

It's unknown where the latitude and longitude of the tournament location is stored.

## <a name="tournament_type">Tournament type</a>

A single byte used to describe both the type of pairing system and the time control in the tournament.

The lower 5 bits are used for the pairing system:

| Value | Description
| --- | ---
| 0 | None
| 1 | Single game ("game")
| 2 | Match
| 3 | Round robin ("tourn")
| 4 | Swiss
| 5 | Team
| 6 | Knock Out ("k.o.")
| 7 | Simultaneous exhibition ("simul")
| 8 | Scheveningen system ("schev")

Bit 5, 6 and 7 are used for time control. Even though multiple of these bits can be set, 
it doesn't seem to be the case in practice.
If none of the bits are set, classical time control is used.

| Bit | Time control
| --- | ---
| 5 | Blitz
| 6 | Rapid
| 7 | Correspondence

## <a name="tournament_flags">Tournament flags</a>

A single byte describing some additional information about the tournament.

| Bit | Description
| --- | ---
| 0 | Legacy complete flag. Same value as the Complete flag in all tournaments 2006 onwards.
| 1 | Complete (all games from the tournament are expected to be in the database)
| 2 | Board points
| 3 | Three points for a win
