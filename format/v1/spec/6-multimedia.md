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

## Embedded pictures

The header of the `.cbh` file counts the embedded sounds, pictures and videos
([1-game-headers.md](1-game-headers.md#file-header)): each counter is the id the
next one will take. A picture with id *n* is the file `n.bmp` in the `.bmp`
folder, a Windows bitmap. Ids start at 0. How a game or a text refers to an
embedded picture is **unknown**, and so are the corresponding sound and video
files.
