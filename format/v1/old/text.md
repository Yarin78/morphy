# Text

Textual contents in the database are spread across multiple files.
The [game header](games.md#cbh_text) contains metadata about the text, such as tournament, annotator, source etc attached to the entry.

## Textual content

The title of the text and the actual textual content (usually HTML formatted)
is stored in the [Moves](moves.md#cbg_text) file.

## Multimedia folder

All images, videos etc in a database are stored in the subfolder `<database>.html`.
The same media file can be used in several texts. 

## Media manifest file

The .cbm file contains a manifest of the media files used in a text.
It's used to determine which media files belong to a game.
Only text entries that contain at least one media file has a record.
If such a text entry get physically deleted, that record still remains, and presumably never gets deleted!? There is no overwrite of CBM records happening.

## <a name="cbm">CBM file format</a>

As most files in a ChessBase database, the .cbm file starts with a header (always `32` bytes), followed by records of the same size.
All integers are Little Endian.

## <a name="cbm_header">CBM file header</a>

| Offset | Bytes | Description
| --- | --- | ---
| 0 | 4 | Unknown. Always `1`. Presumable the version of this file format.
| 4 | 4 | The size of a record in this file. Usually `1024` bytes, but not always.
| 8 | 4 | Number of records.
| 12 | 4 | Unknown. Usually `-1` but can other values as well.
| 16 | 16 | Unknown. Usually 0 but can contain seemingly trash data after packing a database.

## <a name="cbm_record">CBM record</a>

Each record is the same size, even though most of it is usually unused.
In rare cases when one record isn't enough to store the entire manifest, it's
split up across multiple consecutive records. The subsequent records don't contain
the small header below; the text data just continues.

| Offset | Bytes | Description
| --- | --- | ---
| 0 | 4 | Unknown. Always `-1`?
| 4 | 4 | Number of records. Usually `1`.
| 8 | ? | Textual contents. New lines are stored as `0x0a`. No end marker; may contain trash after last line.

All text content follows the following format:

```
FilesUsed
<num files>
filename1.jpg
filename2.jpg
```

After the `<num files>` lines, the data ends and may contain trash data that should not be parsed.
There is no proper end-of-file marker.

The filenames are files in the `<database>.html` folder that are used by the text content.
