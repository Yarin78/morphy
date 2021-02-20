# Game tags

Game tags (also known as Game titles) are stored in the .cbl file; see [Entities](entities.md) for the initial structure of this file.
The size of a game tag entity record is always `1608`.

All titles are zero terminated strings. The fields are usually followed by trash bytes.
Each title can be at most 199 characters as the terminating zero is needed as well.

| Offset | Bytes | Description
| --- | --- | ---
| 0 | 200 | English title
| 200 | 200 | German title
| 400 | 200 | French title
| 600 | 200 | Spanish title
| 800 | 200 | Italian title
| 1000 | 200 | Dutch title
| 1200 | 200 | Reserved space for future localised title (Portuguese?)
| 1400 | 200 | Reserved space for future localised title
| 1600 | 4 | Number of references to this game tag.
| 1604 | 4 | The id of the first game in the database with this game tag.
