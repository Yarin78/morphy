# `.cbm` and the media folders — multimedia

A guiding text can show pictures. The picture files live in a folder beside the
database, and a manifest, the `.cbm` file, says which files each text uses so that
ChessBase can find them when the text is copied or exported.

## The media folders

| Folder | Contents |
|---|---|
| `<name>.html` | the files that guiding texts use, in any format a browser shows: `.jpg`, `.png`, `.gif`, and so on |
| `<name>.bmp` | embedded pictures, [below](#embedded-pictures) |

The files of the `.html` folder are named freely, the name being what the HTML of
the text puts in its `src` attribute. Several texts can use the same file, and the
program's own logos are kept in the folder too.

## `.cbm` — the manifest

Little-endian. A 32-byte header, then records of a fixed size back to back.

| Offset | Size | Type | Description |
|---|---|---|---|
| 0x00 | 4 | int | 1 |
| 0x04 | 4 | int | size of a record: 1024 |
| 0x08 | 4 | int | number of records |
| 0x0c | 4 | int | −1 |
| 0x10 | 16 | | unused; may hold leftovers |

A manifest is stored in one record, or when it is longer in as many consecutive
records as it needs. Only a guiding text that uses media has one, and it finds it
through the [`.cbj`](1-game-headers.md#records) record: the value at 0x08 is the
offset in the `.cbm` file of the **text** of the manifest, 8 bytes into the first
of its records. It is −1 for a text with no manifest, and for every game.

| Offset | Size | Type | Description |
|---|---|---|---|
| 0x00 | 4 | int | −1 |
| 0x04 | 4 | int | number of records the manifest takes, 1 for most |
| 0x08 | … | | the text |

A record after the first of a manifest has no such header: the text continues from
its first byte. The text ends where the last record is filled or where zero bytes
begin, and what follows is leftovers that a reader must not interpret. A record
that is all zeros is empty.

The text is lines separated by a `0x0a` byte:

```
FilesUsed
<n>
<file name>
<file name>
…
```

The `n` lines after the count are names of files in the `.html` folder.

A guiding text that uses pictures has the *embedded picture* [flag](1-game-headers.md#flags)
set. The flag does not imply that it has a manifest.

## Embedded pictures and sounds

The header of the `.cbh` file counts the embedded sounds, pictures and videos
([1-game-headers.md](1-game-headers.md#file-header)): each counter is the id the
next one will take, and ids start at 0. The counter can be higher than the number
of files, since the files of removed media are removed.

A picture with id *n* is the file `n.bmp`, a Windows bitmap, in the `<name>.bmp`
folder. A sound with id *n* is the file `n.wav` in the `<name>.wav` folder.

A game refers to a picture with an annotation of type `11` whose data is the id in
ASCII decimal digits: `31 38` is picture 18. The game has the *embedded picture*
[flag](1-game-headers.md#flags).

A guiding text in the older text format, [version 1](2-moves.md#guiding-texts), refers
to a picture by the name of its file, in the binary formatting data that follows the
text; the names found are of files in the `.bmp` folder such as `intro_olimpiada2.bmp`.
The structure of that data is **unknown**. The text has the picture flag, and the
sound flag when it has a sound.

How a text refers to a sound, and the folder and the references of embedded videos,
are **unknown**: a video has a counter and a flag, but no folder has been seen.
