# Tournaments

Tournament entities are stored in the .cbt file; see [Entities](entities.md) for the initial structure of this file.
The size of a tournament entity record is always `90`.

Additional tournament information is stored in the [.cbtt file](#cbtt.md).

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

# <a name="cbtt_file">CBTT file format</a>

The original .cbt file doesn't allow records to be extended, so additional information
is stored in the .cbtt file. The size of each record in this file is dynamic, and
can differ between versions. As usual, the file starts with a header. 

All integers are in Little Endian.

| Offset | Bytes | Description
| --- | --- | ---
| 0 | 4 | Version number of this file.
| 4 | 4 | Size of each record. 65 if version 3, 61 if version 2.
| 8 | 4 | Numbers of tournaments?
| 12 | 20 | Unused, usually trash bytes.

Then follows each record. The first record corresponds to the tournament with index 0, and so on.

| Offset | Bytes | Description
| --- | --- | ---
| 0 | 8 | Latitude ([double point precision](https://en.wikipedia.org/wiki/Double-precision_floating-point_format))
| 8 | 8 | Longitude ([double point precision](https://en.wikipedia.org/wiki/Double-precision_floating-point_format))
| 16 | 10 | Unknown
| 26 | 1 | The value `7`
| 27 | 10 | Unknown
| 37 | 1 | The value `7`
| 38 | 10 | Unknown
| 48 | 1 | The value `7`
| 49 | 1 | Unknown
| 50 | 10 | [Tie break rules](#tiebreaks)
| 60 | 1 | Number of tiebreak rules specified (at most 4 possible in ChessBase)
| 61 | 4 | [Tournament end date](types.md#date)

## <a name="tiebreaks">Tie breaks</a>

| Value | Description
| --- | ---
| 1 | Unspecified
| 2 | Not set
| 10 | Swiss System - Rating of Buchholz
| 11 | Swiss System - Feine Buchholz
| 12 | Swiss System - Median Buchholz
| 13 | Swiss System - Fortshritt
| 14 | Swiss System - Sonnenbornberger
| 21 | Swiss System - Median2 Buchholz
| 22 | Swiss System - Cut 1 Buchholz
| 23 | Swiss System - Cut 2 Buchholz
| 200 | Round Robin - Sonnebornberger
| 201 | Round Robin - # wins
| 202 | Round Robin - # black wins
| 203 | Round Robin - # black games
| 204 | Round Robin - Point group
| 206 | Round Robin - Koya
