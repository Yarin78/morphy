#!/usr/bin/env python3
"""Draws the figures the specification embeds. Run it to regenerate them all:

    python3 format/v1/spec/img/make.py

Every figure is a self-contained SVG written next to this script, and the
markdown refers to it with an ordinary image link, so the figures render in a
markdown preview and on a git host without any tooling.

The drawing primitives, and the reasons the figures look the way they do, are in
../../../figkit.py; this file holds only what is particular to the v1 figures.

The numbers in the figures are real. The record figures show the last game of
the World-ch database in test-databases/world-ch (game 1038, the third
tie-break game of the 2018 match), and the file sizes are those of Mega Database
2021. The specification itself is the source of truth for every layout drawn here.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
from figkit import Fig, FILLS, INK  # noqa: E402  (the drawing primitives, shared with v2)

OUT_DIR = os.path.dirname(os.path.abspath(__file__))
Fig.out_dir = OUT_DIR


def byte_grid(f, x0, y0, fields, records, cell=46, row=40, per_row=16, label_min=40):
    """Draws a record byte by byte, `per_row` bytes to a row. A field is
    (offset, size, label, style, value); the value is written under the label."""
    for r in range(records):
        f.text(x0 - 10, y0 + r * row + row / 2 + 4, f"0x{r * per_row:02x}", "xs m fnt", "end")
    for off, size, label, style, *value in fields:
        segments, at, left = [], off, size
        while left > 0:  # a field may cross the end of a row, so draw it a segment at a time
            r, c = divmod(at, per_row)
            n = min(left, per_row - c)
            segments.append((r, c, n))
            at, left = at + n, left - n
        widest = max(segments, key=lambda seg: seg[2])
        for seg in segments:
            r, c, n = seg
            w = n * cell - 3
            x, y = x0 + c * cell, y0 + r * row
            f.cell(x, y, w, row - 4, "", style, r=2)
            if seg is not widest and w >= 30:
                f.text(x + w / 2, y + row / 2, "…", "xs fnt", "middle")
            if seg is widest and w >= label_min:
                ink = INK[style]
                if value and value[0]:
                    f.text(x + w / 2, y + 15, label, ("xs b " + ink).strip(), "middle")
                    f.text(x + w / 2, y + 29, value[0], "xs m mut", "middle")
                else:
                    f.text(x + w / 2, y + row / 2, label, ("xs b " + ink).strip(), "middle")


def legend(f, x, y, items, limit=640):
    for style, label in items:
        f.rect(x, y - 9, 12, 12, FILLS[style], 2)
        f.text(x + 18, y, label, "xs mut")
        x += 20 + len(label) * 5.4 + 22
        if x > limit:
            x, y = 24, y + 20
    return y


def files():
    """README.md — the files of a database and how a game record reaches them."""
    f = Fig(860, 452, "A database is a set of files sharing one base name. The game record in .cbh "
                      "holds the offset of its moves in .cbg and of its annotations in .cba, and "
                      "refers to players, tournaments, annotators and sources by id. The .cbj file "
                      "adds teams and a game tag. The search boosters can be rebuilt from the rest.")
    f.marker("d", "hacc")
    f.marker("e", "hfnt")
    f.text(24, 28, "One database, many files", "lg b")
    f.text(24, 46, "they share a base name; the sizes are those of Mega Database 2021, "
                   "8.5 million games", "xs mut")

    f.rect(24, 74, 236, 206, "box", 5)
    f.text(40, 98, ".cbh", "sm b m acc")
    f.text(88, 98, "game headers", "sm b")
    f.text(40, 114, "46 bytes per game · 391 MB", "xs mut")
    rows = [(0x01, "moves offset", "key"), (0x05, "annotation offset", "key"),
            (0x09, "white · black player", "acc"), (0x0f, "tournament", "acc"),
            (0x12, "annotator · source", "acc"), (0x18, "date · result · elo …", "box")]
    for i, (off, label, style) in enumerate(rows):
        y = 124 + i * 25
        f.cell(40, y, 204, 21, "", style, r=2)
        f.text(48, y + 15, f"0x{off:02x}", "xs m fnt")
        f.text(88, y + 15, label, "xs " + INK[style])

    f.rect(24, 300, 236, 76, "box", 5)
    f.text(40, 324, ".cbj", "sm b m acc")
    f.text(88, 324, "extended headers", "sm b")
    f.text(40, 342, "120 bytes per game · 1.0 GB", "xs mut")
    f.text(40, 360, "teams, game tag, material, ratings, times", "xs mut")

    for x, y, ext, what, size, style in [
            (332, 74, ".cbg", "moves", "896 MB · one record per game", "key"),
            (332, 158, ".cba", "annotations", "176 MB · one record per game", "key"),
            (332, 242, ".cbp .cbt .cbc .cbs", "entities", "", "acc")]:
        f.cell(x, y, 244, 64, "", style, r=5)
        if size:
            f.text(x + 16, y + 26, ext, "sm b m " + INK[style])
            f.text(x + 72, y + 26, what, "sm b")
            f.text(x + 16, y + 44, size, "xs mut")
    f.text(348, 268, ".cbp .cbt .cbc .cbs", "sm b m acc")
    f.text(348, 286, "players, tournaments, annotators,", "xs mut")
    f.text(348, 300, "sources: 26 MB · a tree of records", "xs mut")
    f.cell(332, 322, 244, 54, "", "acc", r=5)
    f.text(348, 344, ".cbe .cbl", "sm b m acc")
    f.text(414, 344, "teams · game tags", "sm b")
    f.text(348, 362, "referred to from .cbj", "xs mut")

    f.path("M244 135 C 290 135, 290 106, 328 106", "lnacc", "d")
    f.path("M244 160 C 290 160, 290 190, 328 190", "lnacc", "d")
    f.path("M244 210 C 290 210, 290 274, 328 274", "lnacc", "d")
    f.path("M260 338 L 328 338", "lnacc", "d")
    f.text(262, 100, "offset", "xs keyc")
    f.text(268, 262, "id", "xs acc")
    f.text(266, 330, "id", "xs acc")

    for x, y, ext, what, size in [(630, 74, ".cit .cib", "games per entity", "257 MB · linked blocks"),
                                  (630, 148, ".cbb", "what happened", "443 MB · 52 bytes per game"),
                                  (630, 222, ".cbgi", "move offsets", "34 MB · 4 bytes per game"),
                                  (630, 296, ".flags", "top games", "2 MB · 2 bits per game")]:
        style = "box" if ext == ".flags" else "slot"
        f.cell(x, y, 206, 60, "", style, r=5)
        f.text(x + 16, y + 25, ext, "sm b m " + INK[style])
        f.text(x + 88, y + 25, what, "sm b")
        f.text(x + 16, y + 44, size, "xs mut")

    f.text(24, 404, "The three search boosters (blue) repeat what the other files say, and any "
                    "of them may be missing or out of date.", "xs mut")
    f.text(24, 422, "A .cbm manifest and the .html and .bmp folders hold the media of guiding "
                    "texts, and an .ini file holds settings.", "xs mut")
    f.save("files.svg")


def cbh_record():
    """1-game-headers.md — the 46-byte game record, 16 bytes to a row."""
    fields = [
        (0x00, 1, "type", "rose", "01"),
        (0x01, 4, "moves offset", "key", "4620620"),
        (0x05, 4, "annotation offset", "key", "1637705"),
        (0x09, 3, "white", "acc", "35"), (0x0c, 3, "black", "acc", "37"),
        (0x0f, 3, "tournament", "acc", "51"), (0x12, 3, "annotator", "acc", "79"),
        (0x15, 3, "source", "acc", "23"), (0x18, 3, "date", "box", "2018-11-28"),
        (0x1b, 1, "result", "box", "1-0"), (0x1c, 1, "eval", "box", "0"),
        (0x1d, 1, "round", "box", "3"), (0x1e, 1, "sub", "box", "0"),
        (0x1f, 2, "w. elo", "box", "2835"), (0x21, 2, "b. elo", "box", "2832"),
        (0x23, 2, "ECO", "box", "B44"), (0x25, 2, "medals", "box", "0"),
        (0x27, 4, "flags", "slot", "0x0e"), (0x2b, 2, "magn.", "slot", "13"),
        (0x2d, 1, "moves", "slot", "51"),
    ]
    f = Fig(860, 280, "A map of the 46-byte game record: a type byte, two file offsets, five "
                      "entity ids, the date, result, ratings and ECO code, and the flags, "
                      "annotation magnitudes and move count that ChessBase computes.")
    f.text(24, 28, "The game record — 46 bytes, 16 to a row", "lg b")
    f.text(24, 46, "every game has one, at 46 · game id; the first record is the file "
                   "header, of the same size", "xs mut")
    f.text(24, 66, "shown: game 1038 of World-ch, Carlsen – Caruana, 2018", "xs fnt")
    byte_grid(f, 74, 84, fields, 3)
    y = legend(f, 24, 226, [("rose", "type"), ("key", "where the moves and annotations are"),
                            ("acc", "entity ids"), ("box", "what the user entered"),
                            ("slot", "computed from the moves")])
    f.text(24, y + 26, "Numbers are big-endian. A guiding text has the same size and its own "
                       "layout from 0x01 on.", "xs mut")
    f.save("cbh-record.svg")


def cbj_record():
    """1-game-headers.md — the 120-byte extended record."""
    fields = [
        (0x00, 4, "white team", "acc", "−1"), (0x04, 4, "black team", "acc", "−1"),
        (0x08, 4, "media", "wash", "−1"), (0x0c, 8, "annotation offset", "key", "1637705"),
        (0x14, 4, "material", "slot", "0x4240"), (0x18, 4, "material", "slot", "0x3408"),
        (0x1c, 2, "total", "slot", "0x73a7"), (0x1e, 8, "moves offset", "key", "4620620"),
        (0x26, 16, "white rating type", "box", "FIDE"), (0x36, 16, "black rating type", "box", "FIDE"),
        (0x46, 4, "?", "wash"), (0x4a, 4, "?", "wash"),
        (0x4e, 2, "version", "wash", "233"), (0x50, 8, "created", "wash"),
        (0x58, 20, "endgames", "slot", "not computed"),
        (0x6c, 8, "last saved", "wash"), (0x74, 4, "game tag", "acc", "0"),
    ]
    f = Fig(860, 470, "A map of the 120-byte extended game record: two team ids, a media offset, "
                      "the offsets of the annotations and moves again, final material, two "
                      "rating types, a version and timestamps, endgame information and a game "
                      "tag.")
    f.text(24, 28, "The extended record — 120 bytes, 16 to a row", "lg b")
    f.text(24, 46, "at 32 + 120 · (game id − 1) in .cbj; the file header is 32 bytes and "
                   "little-endian, the records are big-endian", "xs mut")
    f.text(24, 66, "shown: game 1038 of World-ch", "xs fnt")
    byte_grid(f, 74, 84, fields, 8)
    y = legend(f, 24, 84 + 8 * 40 + 26,
               [("acc", "entity ids"), ("key", "the offsets .cbh holds too"),
                ("slot", "computed from the moves"), ("box", "what the user entered"),
                ("wash", "media, timestamps, version, unknown")])
    f.save("cbj-record.svg")


def cbg_file():
    """2-moves.md — the move file: header, records in game id order, holes."""
    f = Fig(860, 320, "The move file: a 26-byte header, then the records of the games one after "
                      "the other in game id order, with a hole of unused bytes where a record "
                      "became shorter.")
    f.text(24, 28, "The move file", "lg b")
    f.text(24, 46, "the offsets in .cbh say where each record starts; a hole is skipped by "
                   "offset alone", "xs mut")
    f.text(24, 66, "shown: the first games of World-ch", "xs fnt")

    parts = [(96, "header", "wash", "26", "0"), (128, "game 1", "key", "355 bytes", "26"),
             (128, "game 2", "key", "376", "381"), (92, "game 3", "key", "242", "757"),
             (100, "hole", "rose", "1024 bytes", "999"), (84, "game 4", "key", "221", "2023"),
             (110, "game 5", "key", "374", "2244")]
    x = 24
    for w, label, style, size, off in parts:
        f.cell(x, 102, w - 4, 52, "", style, r=3)
        f.text(x + (w - 4) / 2, 124, label, "sm b " + INK[style], "middle")
        f.text(x + (w - 4) / 2, 142, size, "xs mut", "middle")
        f.text(x, 94, off, "xs m fnt")
        x += w
    f.text(x + 6, 132, "…", "lg fnt")

    f.text(24, 190, "The header", "sm b")
    hdr = [(46, "26", "size"), (92, "4,620,808", "file size"), (92, "10,240", "bytes in holes"),
           (120, "4,620,808", "again, 8 bytes"), (120, "10,240", "again, 8 bytes")]
    x = 24
    for w, val, label in hdr:
        f.cell(x, 202, w - 4, 40, "", "wash", r=2)
        f.text(x + (w - 4) / 2, 219, val, "xs m b", "middle")
        f.text(x + (w - 4) / 2, 234, label, "xs mut", "middle")
        x += w
    f.text(x + 12, 226, "the two 8-byte fields are absent when the header is 10 bytes", "xs mut")

    f.text(24, 274, "A game record: a flags byte, a 3-byte size that counts these 4 bytes, "
                    "then the moves.", "xs mut")
    f.text(24, 292, "A guiding text is a record of the same kind with bit 7 of the flags set, "
                    "holding titles and HTML.", "xs mut")
    f.save("cbg-file.svg")


def cba_record():
    """3-annotations.md — the framing of an annotation record."""
    f = Fig(860, 300, "An annotation record: a 14-byte header with the game id, the number of "
                      "annotations plus one and the size, then the annotations, each with a "
                      "position, a type, a size and its data.")
    f.text(24, 28, "An annotation record", "lg b")
    f.text(24, 46, "found through the annotation offset of the game; a game with no "
                   "annotations has none", "xs mut")
    f.text(24, 66, "shown: game 1038 of World-ch, 40 annotations in 3,146 bytes", "xs fnt")

    parts = [(70, "game id", "acc", "3", "1038"), (96, "fixed", "wash", "4", "01 00 0e 0e"),
             (70, "count + 1", "box", "3", "41"), (70, "size", "key", "4", "3146")]
    x = 24
    for (w, label, style, size, val), off in zip(parts, (0x00, 0x03, 0x07, 0x0a)):
        f.cell(x, 102, w - 4, 52, "", style, r=3)
        f.text(x + (w - 4) / 2, 122, label, "xs b " + INK[style], "middle")
        f.text(x + (w - 4) / 2, 138, val or size, "xs m mut", "middle")
        f.text(x, 94, f"0x{off:02x}", "xs m fnt")
        x += w
    f.text(x + 4, 94, "0x0e", "xs m fnt")
    anns = [("position 9", "text, 217 bytes", "acc"),
            ("position 22", "symbols, 7 bytes", "acc"),
            ("position 22", "text, 98 bytes", "acc")]
    for label, kind, style in anns:
        w = 152
        f.cell(x, 102, w - 4, 52, "", style, r=3)
        f.text(x + (w - 4) / 2, 122, label, "xs b " + INK[style], "middle")
        f.text(x + (w - 4) / 2, 138, kind, "xs mut", "middle")
        x += w
    f.text(x + 6, 132, "…", "lg fnt")

    f.text(24, 196, "Each annotation", "sm b")
    parts = [(120, "position", "acc", "3 bytes", "−1 is the game"), (120, "type", "key", "1 byte", "02 text, 03 symbols …"),
             (120, "size", "box", "2 bytes", "counts these 6"), (250, "data", "slot", "size − 6 bytes", "depends on the type")]
    x = 24
    for w, label, style, size, note in parts:
        f.cell(x, 210, w - 4, 52, "", style, r=3)
        f.text(x + (w - 4) / 2, 230, label, "sm b " + INK[style], "middle")
        f.text(x + (w - 4) / 2, 246, size, "xs mut", "middle")
        f.text(x + (w - 4) / 2, 278, note, "xs fnt", "middle")
        x += w
    f.save("cba-record.svg")


def entity_file():
    """4-entities.md — header, records, and the node at the start of each."""
    f = Fig(860, 352, "An entity file: a 32-byte header, then records of a fixed size. Each record "
                      "starts with a tree node, the left and right child and a balance, and "
                      "ends with the data of the entity.")
    f.text(24, 28, "An entity file", "lg b")
    f.text(24, 46, "the same layout for players, tournaments, annotators, sources, teams and "
                   "game tags; only the data differs", "xs mut")
    f.text(24, 66, "shown: the players of World-ch, which has 38", "xs fnt")

    f.text(24, 92, "The header", "sm b")
    hdr = [(72, "records", "38"), (72, "root", "4"), (110, "magic", "1234567890"),
           (80, "size", "58"), (100, "first deleted", "−1"), (72, "count", "38"),
           (72, "extra", "4"), (90, "extra bytes", "")]
    x = 24
    for w, label, val in hdr:
        f.cell(x, 102, w - 4, 44, "", "wash", r=2)
        f.text(x + (w - 4) / 2, 120, label, "xs b", "middle")
        f.text(x + (w - 4) / 2, 136, val, "xs m mut", "middle")
        x += w
    x = 24
    for k, (w, label, val) in enumerate(hdr):
        f.text(x, 162, f"0x{4 * k:02x}", "xs m fnt")
        x += w

    f.text(24, 196, "A record — player 4, 67 bytes", "sm b")
    parts = [(78, "left", "acc", "3"), (78, "right", "acc", "7"), (60, "balance", "slot", "0"),
             (200, "last name", "box", "Lasker"), (132, "first name", "box", "Emanuel"),
             (100, "references", "key", "102"), (100, "first game", "key", "80")]
    x = 24
    for w, label, style, val in parts:
        f.cell(x, 206, w - 4, 48, "", style, r=3)
        f.text(x + (w - 4) / 2, 226, label, "xs b " + INK[style], "middle")
        f.text(x + (w - 4) / 2, 242, val, "xs m mut", "middle")
        x += w
    f.text(24, 274, "the node, 9 bytes", "xs mut")
    f.text(24 + 216, 274, "the data of the entity, 58 bytes for a player", "xs mut")
    f.text(24, 308, "The children are entity ids, −1 for none. The balance is the height of the "
                    "right subtree minus that of the left.", "xs mut")
    f.text(24, 326, "A deleted record has −999 as its left child, and its right child links "
                    "to the next deleted record.", "xs mut")
    f.save("entity-file.svg")


def entity_tree():
    """4-entities.md — the top of the player tree of World-ch, and the order it gives."""
    f = Fig(860, 400, "The top three levels of the tree of the 38 players of World-ch: Lasker at "
                      "the root, Gunsberg and Schlechter below him, and four more players below "
                      "them. Read left to right they are in alphabetical order.")
    f.text(24, 28, "A tree of players", "lg b")
    f.text(24, 46, "each node has the id of the player, the balance is under the name; "
                   "the triangles stand for what hangs below", "xs mut")
    f.marker("t", "hfnt")

    def node(x, y, name, ident, bal, style="box"):
        f.cell(x - 66, y, 132, 44, "", style, r=4)
        f.text(x, y + 18, name, "xs b", "middle")
        f.text(x, y + 34, f"id {ident} · balance {bal:+d}" if bal else f"id {ident} · balance 0",
               "xs m mut", "middle")

    xs = {4: 430, 3: 230, 7: 630, 9: 130, 13: 330, 19: 530, 1: 730}
    ys = {4: 74, 3: 158, 7: 158, 9: 242, 13: 242, 19: 242, 1: 242}
    tree = [(4, "Lasker", 0, "acc"), (3, "Gunsberg", 0, "box"), (7, "Schlechter", 1, "box"),
            (9, "Capablanca", 0, "box"), (13, "Keres", -1, "box"), (19, "Petrosian", -1, "box"),
            (1, "Steinitz", 1, "box")]
    for a, b in ((4, 3), (4, 7), (3, 9), (3, 13), (7, 19), (7, 1)):
        f.line(xs[a], ys[a] + 44, xs[b], ys[b], "ln", "t")
    for ident, name, bal, style in tree:
        node(xs[ident], ys[ident], name, ident, bal, style)
    for ident in (9, 13, 19, 1):
        x, y = xs[ident], ys[ident] + 44
        f.path(f"M{x} {y} L{x - 24} {y + 34} L{x + 24} {y + 34} z", "lndash")
    f.text(24, 358, "In order: Capablanca, Gunsberg, Keres, Lasker, Petrosian, Schlechter, "
                        "Steinitz — with the subtrees between them.", "xs mut")
    f.text(24, 378, "Walking left subtree, node, right subtree gives the players in sort order; "
                    "a search descends from the root.", "xs mut")
    f.save("entity-tree.svg")


def cit_cib():
    """5-search-boosters.md — from an entity to the linked blocks of its games."""
    f = Fig(860, 340, "How the games of a player are found: the record of the player in .cit holds "
                      "the first and last block of a list; each block in .cib holds up to 13 game "
                      "ids and the number of the next block.")
    f.marker("d", "hacc")
    f.text(24, 28, "The games of an entity", "lg b")
    f.text(24, 46, "a record in .cit for each entity id, and blocks of 64 bytes in .cib that "
                   "are linked", "xs mut")
    f.text(24, 66, "shown: player 0 of World-ch, Zukertort, who played 20 games", "xs fnt")

    f.text(24, 96, ".cit — record 0, 40 bytes", "sm b")
    pairs = [("players", "0 · 5", "acc"), ("tournaments", "2 · 6", "box"),
             ("teams", "−1 · −1", "wash"), ("sources", "4 · 347", "box"),
             ("annotators", "3 · 338", "box")]
    x = 24
    for label, val, style in pairs:
        f.cell(x, 108, 108, 46, "", style, r=3)
        f.text(x + 54, 127, label, "xs b " + INK[style], "middle")
        f.text(x + 54, 143, val, "xs m mut", "middle")
        x += 112
    f.text(24 + 5 * 112 + 8, 135, "each pair is the first and the last block of a list", "xs mut")

    top, blocks = 208, [(24, 380, "block 0", 5, 13, "1 2 3 4 5 6 7 8 9 10 11 12 13"),
                        (440, 396, "block 5", -1, 7, "14 15 16 17 18 19 20  … leftovers")]
    for x0, w, name, nxt, count, ids in blocks:
        f.cell(x0, top, w, 64, "", "box", r=3)
        x = x0 + 8
        for label, val, style, cw in (("next", str(nxt).replace("-", "−"), "key", 60),
                                      ("", "0", "wash", 30), ("count", str(count), "box", 56)):
            f.cell(x, top + 8, cw, 48, "", style, r=2)
            f.text(x + cw / 2, top + 28 if label else top + 36, label or val, "xs b " + INK[style], "middle")
            if label:
                f.text(x + cw / 2, top + 44, val, "xs m mut", "middle")
            x += cw + 4
        f.text(x + 4, top + 28, "game ids", "xs b")
        f.text(x + 4, top + 44, ids, "xs m mut")
        f.text(x0, top + 84, ".cib — " + name, "sm b")
    f.path("M404 " + str(top + 32) + " L 436 " + str(top + 32), "lnacc", "d")
    f.text(414, top + 24, "5", "xs acc")
    # the pair points at both ends of the list
    f.path(f"M54 154 L54 {top - 2}", "lnacc", "d")
    f.path(f"M118 154 L118 176 L 700 176 L700 {top - 2}", "lndash", "d")
    f.text(706, 172, "last block", "xs mut")
    f.text(62, 176, "first block", "xs mut")
    f.text(24, 322, "A list ends where the next block is −1. An entity with no games has −1 and −1.",
           "xs mut")
    f.save("cit-cib.svg")


def cbb_record():
    """5-search-boosters.md — the 52-byte record of what happened in a game."""
    rec = bytes.fromhex("000000" "53" "ed" "85" "d10f070400" "001070f814" "07f07c38"
                        "f960000000808000" "0060200000e0388b" "661a346000000000" "0040d0e020680c66")
    assert len(rec) == 52
    # rows of whole groups: (first byte, last byte); a group is (size, label, style)
    rows = [(0, [(3, "unused", "wash"), (1, "pawns", "slot"), (1, "≤ 1", "slot"),
                 (1, "none", "slot"), (5, "white pawns · rank 3–7", "acc")]),
            (11, [(5, "black pawns · rank 2–6", "rose"), (1, "wK rank", "key"),
                  (1, "bK rank", "key"), (1, "wK file", "key"), (1, "bK file", "key")]),
            (20, [(8, "white rooks and queens · rank 1–8", "acc"),
                  (8, "black rooks and queens · rank 1–8", "rose")]),
            (36, [(8, "white knights and bishops · rank 1–8", "acc"),
                  (8, "black knights and bishops · rank 1–8", "rose")])]
    f = Fig(860, 384, "A map of the 52-byte record of the search booster .cbb: three unused "
                      "bytes, the pawn count and the piece counts, then one byte per rank "
                      "for each kind of piece, with a bit for every file the piece has stood on.")
    f.text(24, 28, "A record of the .cbb file — 52 bytes", "lg b")
    f.text(24, 46, "what has happened in the main line of the game; the header record comes "
                   "first, so game n is at 52 · n", "xs mut")
    f.text(24, 66, "shown: game 1 of World-ch, Steinitz – Zukertort, 1886", "xs fnt")
    x0, y0, cell, pitch = 74, 84, 46, 58
    for r, (first, groups) in enumerate(rows):
        y = y0 + r * pitch
        f.text(x0 - 10, y + 36, f"0x{first:02x}", "xs m fnt", "end")
        at = first
        for size, label, style in groups:
            x = x0 + (at - first) * cell
            f.text(x, y + 10, label, "xs b " + INK[style])
            for k in range(size):
                f.cell(x + k * cell, y + 16, cell - 3, 30, "", style, r=2)
                f.text(x + k * cell + (cell - 3) / 2, y + 36, f"{rec[at + k]:02x}", "xs m", "middle")
            at += size
    y = legend(f, 24, 84 + 4 * 58 + 14, [("acc", "white"), ("rose", "black"), ("key", "the kings"),
                                 ("slot", "counts"), ("wash", "unused")])
    f.text(24, y + 22, "A bit is a file, a to h from bit 0; a byte is a rank. A bit set means "
                       "“at some point in the game”.", "xs mut")
    f.save("cbb-record.svg")


if __name__ == "__main__":
    for draw in (files, cbh_record, cbj_record, cbg_file, cba_record, entity_file, entity_tree,
                 cit_cib, cbb_record):
        draw()
