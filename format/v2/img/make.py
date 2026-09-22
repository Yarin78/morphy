#!/usr/bin/env python3
"""Draws the figures the specification embeds. Run it to regenerate them all:

    python3 format/v2/img/make.py

Every figure is a self-contained SVG written next to this script, and the
markdown refers to it with an ordinary image link, so the figures render in a
markdown preview and on a git host without any tooling.

The drawing primitives, and the reasons the figures look the way they do, are in
../../tools/figkit.py; this file holds only what is particular to the v2 figures.

The numbers in the figures are real, read from Mega Database 2026 with the
readers in morphy-py; MEGA below collects them so they can be checked
against a database rather than trusted. The specification itself is the source
of truth for every layout drawn here.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "tools"))
from figkit import Fig, FILLS, INK  # noqa: E402  (the drawing primitives, shared with v1)

OUT_DIR = os.path.dirname(os.path.abspath(__file__))
Fig.out_dir = OUT_DIR


# Values read from Mega Database 2026. Recovered with inspect and the cb2
# package; see the comment on each for how.
MEGA = {
    # (file size - 192) / 192, so games, guiding texts and analyses together
    "records": "11,990,472",
    "lcd_pages": 7578,            # page count in the .2lcd header
    # the "Default Spieler" catalog entry
    "players_depth": 2, "players_page": 1867, "players_root": 3, "players_count": 481710,
    # Magnus Carlsen, found by his name in the .2lid file
    "carlsen_id": 145915, "carlsen_left": 167742, "carlsen_parent": 158691,
    "carlsen_dir2": 1868, "carlsen_node_page": 2459,
    "lgd_records": "4,355,078",   # (file size - 12) / 512
    # record 11576 of the .2lgd file, which happens to use all three list forms
    "single_first": 109230, "single_last": 109293,
    "range_first": 256403, "range_last": 256538, "range_count": 136,
    "chain_first": 1216794, "chain_last": 1216796, "chain_count": 72,
}


def files():
    """README.md — the files of a database and how a game record reaches them."""
    f = Fig(860, 356, "A database is six files sharing one base name. The game record in .2cbh "
                      "holds the offset of its moves in .2cbg and its annotations in .2cba, and "
                      "refers to players, tournaments and the rest by id in .2lid. The .2lcd and "
                      ".2lgd files index those entities.")
    f.marker("d", "hacc")
    f.marker("e", "hfnt")
    f.text(24, 28, "One database, six files", "lg b")
    f.text(24, 46, "they share a base name; every one is needed to read the database in full, and "
                   "the sizes are Mega Database 2026’s", "xs mut")

    f.rect(24, 74, 236, 212, "box", 5)
    f.text(40, 98, ".2cbh", "sm b m acc")
    f.text(96, 98, "game headers", "sm b")
    f.text(40, 114, "192 bytes per game · 2.3 GB", "xs mut")
    rows = [(0x08, "moves offset", "key"), (0x10, "annotation offset", "key"),
            (0x18, "white player", "acc"), (0x20, "black player", "acc"),
            (0x28, "tournament", "acc"), (0x30, "annotator · source …", "acc")]
    for i, (off, label, style) in enumerate(rows):
        y = 130 + i * 26
        f.cell(40, y, 204, 22, "", style, r=2)
        f.text(48, y + 15, f"0x{off:02x}", "xs m fnt")
        f.text(88, y + 15, label, "xs " + INK[style])

    for x, y, ext, what, size, style in [
            (332, 74, ".2cbg", "moves", "3.6 GB · one record per game", "key"),
            (332, 158, ".2cba", "annotations", "3.0 GB · one record per game", "key"),
            (332, 242, ".2lid", "entities", "2.0 GB · players, tournaments, sources,", "acc")]:
        f.cell(x, y, 244, 64, "", style, r=5)
        f.text(x + 16, y + 26, ext, "sm b m " + INK[style])
        f.text(x + 72, y + 26, what, "sm b")
        f.text(x + 16, y + 44, size, "xs mut")
    f.text(348, 300, "teams and game tags, by id", "xs mut")

    f.path("M244 141 C 290 141, 290 106, 328 106", "lnacc", "d")
    f.path("M244 167 C 290 167, 290 190, 328 190", "lnacc", "d")
    f.path("M244 219 C 290 219, 290 274, 328 274", "lnacc", "d")
    f.text(262, 122, "offset", "xs keyc")
    f.text(262, 250, "id", "xs acc")

    for x, y, ext, what, size in [(630, 158, ".2lcd", "sort orders", "31 MB · a tree per list"),
                                  (630, 242, ".2lgd", "games per entity",
                                   "2.2 GB · the ids, backwards")]:
        f.cell(x, y, 206, 64, "", "slot", r=5)
        f.text(x + 16, y + 26, ext, "sm b m slotc")
        f.text(x + 72, y + 26, what, "sm b")
        f.text(x + 16, y + 44, size, "xs mut")
    f.path("M576 262 C 604 262, 604 196, 626 196", "ln", "e")
    f.path("M576 278 L 626 278", "ln", "e")
    f.text(586, 172, "index it", "xs mut")

    f.text(24, 322, "Derived data: both indexes can be rebuilt from the game headers and the "
                    "entities, but ChessBase relies on them.", "xs mut")
    f.text(24, 340, "A .cko and .cpo pair may sit alongside holding the opening key, and a "
                    "plain-text .ini holds settings; neither is part of this format.", "xs mut")
    f.save("files.svg")


def game_record():
    """1-game-headers.md — the 192-byte record, 16 bytes to a row."""
    cell, row, x0, y0 = 46, 32, 74, 96
    fields = [
        (0x00, 8, "common header", "rose"),
        (0x08, 8, "moves offset", "key"), (0x10, 8, "annotation offset", "key"),
        (0x18, 8, "white player", "acc"), (0x20, 8, "black player", "acc"),
        (0x28, 8, "tournament", "acc"), (0x30, 8, "annotator", "acc"),
        (0x38, 8, "source", "acc"), (0x40, 8, "white team", "acc"),
        (0x48, 8, "black team", "acc"), (0x50, 8, "game tag", "acc"),
        (0x58, 1, "result", "box"), (0x59, 1, "eval", "box"), (0x5a, 2, "round", "box"),
        (0x5c, 2, "subround", "box"), (0x5e, 2, "board", "box"),
        (0x60, 2, "elo", "box"), (0x62, 14, "white rating type", "box"),
        (0x70, 2, "elo", "box"), (0x72, 14, "black rating type", "box"),
        (0x80, 2, "ECO", "box"), (0x82, 2, "medals", "box"), (0x84, 4, "flags", "box"),
        (0x88, 2, "magn.", "box"), (0x8a, 2, "moves", "box"),
        (0x8c, 4, "material", "slot"), (0x90, 4, "material", "slot"), (0x94, 4, "0", "wash"),
        (0x98, 8, "created", "wash"), (0xa0, 8, "last changed", "wash"),
        (0xa8, 6, "endgames", "slot"), (0xae, 2, "0", "wash"),
        (0xb0, 6, "classification", "slot"), (0xb6, 2, "0", "wash"),
        (0xb8, 4, "version", "wash"), (0xbc, 4, "played date", "wash"),
    ]
    f = Fig(860, 560, "A map of the 192-byte game record: eight bytes of common header, two file "
                      "offsets, seven entity ids, the details of the game, and the values "
                      "ChessBase computes from the moves.")
    f.text(24, 28, "The game record — 192 bytes, 16 to a row", "lg b")
    f.text(24, 46, "every game has one, at 192 · game id; ids start at 1 and records are "
                   "never removed", "xs mut")
    f.text(24, 70, f"Mega Database 2026: {MEGA['records']} records, 2.3 GB", "xs fnt")

    for r in range(12):
        f.text(x0 - 10, y0 + r * row + row / 2 + 4, f"0x{r * 16:02x}", "xs m fnt", "end")
    for off, size, label, style in fields:
        # A field may run past the end of a row, so draw it a row-segment at a time.
        at, left, first = off, size, True
        while left > 0:
            r, c = divmod(at, 16)
            n = min(left, 16 - c)
            w = n * cell - 3
            f.cell(x0 + c * cell, y0 + r * row, w, row - 4, "", style, r=2)
            if first and w >= 40:
                f.text(x0 + c * cell + w / 2, y0 + r * row + row / 2 + 1, label,
                       ("xs b " + INK[style]).strip(), "middle")
            at, left, first = at + n, left - n, False

    y = y0 + 12 * row + 26
    legend = [("rose", "type, kind, deleted"), ("key", "where the moves and annotations are"),
              ("acc", "entity ids — 56 of the 192 bytes"), ("box", "what the user entered"),
              ("slot", "computed from the moves"), ("wash", "timestamps, version, padding")]
    x = 24
    for style, label in legend:
        f.rect(x, y - 9, 12, 12, FILLS[style], 2)
        f.text(x + 18, y, label, "xs mut")
        x += 20 + len(label) * 5.4 + 22
        if x > 620:
            x, y = 24, y + 20
    f.text(24, y + 28, "A guiding text and an analysis share only the first eight bytes; "
                       "everything from 0x08 on has a layout of its own.", "xs mut")
    f.save("game-record.svg")


def cbg_record():
    """2-moves.md and 3-annotations.md — the framing both files use."""
    f = Fig(860, 276, "The framing shared by a .2cbg and a .2cba record: an 8-byte magic, the two "
                      "sizes A and B, a big-endian checksum, a 2-byte tag, the content, the "
                      "spare area and the record length.")
    f.text(24, 28, "A record in the move and annotation files", "lg b")
    f.text(24, 46, "the same framing in both; a game’s .2cbh record holds the offset of each",
           "xs mut")

    parts = [(84, "magic", "wash", "8 bytes", "88 77 66 \u2026"),
             (60, "A", "acc", "4", "content size"),
             (60, "B", "slot", "4", "spare size"),
             (84, "checksum", "key", "8, big-endian", "of the content"),
             (66, "tag", "rose", "2 bytes", "what it holds"),
             (202, "content", "acc", "A bytes", "moves, or annotations"),
             (144, "spare", "slot", "B bytes, all zero", "room to grow"),
             (96, "length", "wash", "8 bytes", "A + B + 34")]
    x = 24
    for w, label, style, size, note in parts:
        f.cell(x, 96, w - 4, 52, "", style, r=3)
        f.text(x + (w - 4) / 2, 118, label, "sm b " + INK[style], "middle")
        f.text(x + (w - 4) / 2, 134, size, "xs mut", "middle")
        f.text(x + (w - 4) / 2, 166, note, "xs fnt", "middle")
        x += w
    # Only the fixed part of the record has offsets worth naming.
    for x, off in ((24, 0x00), (108, 0x08), (168, 0x0c), (228, 0x10), (312, 0x18),
                   (378, 0x1a)):
        f.text(x, 88, f"0x{off:02x}", "xs m fnt")

    f.text(24, 202, "The tag says what the record holds, and A does not count it:", "xs mut")
    x = 24
    for tag, what in (("01 00 / 02 00", "a game, and which variant"), ("00 10", "a guiding text"),
                      ("00 20", "annotations")):
        f.text(x, 222, tag, "sm m b")
        f.text(x, 238, what, "xs mut")
        x += 220
    f.text(24, 262, "The checksum is the only big-endian field in either file. Records sit back "
                    "to back, each starting where the last one ended.", "xs mut")
    f.save("cbg-record.svg")


def entity_blocks():
    """4-entities.md — a block holds entity i of every type."""
    f = Fig(860, 300, "The .2lid file: a 184-byte header, then blocks of 4234 bytes. Block i holds "
                      "entity i of every type, each type in a fixed-size container.")
    f.text(24, 28, "Entities are stored a block at a time", "lg b")
    f.text(24, 46, "block i holds entity i of every type — player 5, tournament 5, source 5 "
                   "and the rest are neighbours", "xs mut")

    f.cell(24, 76, 92, 44, "header", "wash", sub="184 B", r=3)
    for x, name in ((124, "block 0"), (300, "block 1"), (476, "block 2")):
        f.cell(x, 76, 168, 44, name, "box", sub="4234 B", r=3)
    f.text(656, 94, "…", "lg fnt")
    f.text(680, 92, "one block per entity id", "xs mut")
    f.text(680, 108, "2.0 GB in Mega 2026", "xs fnt")
    f.path("M300 120 L300 140 L24 140 L24 160", "lndash")
    f.path("M468 120 L468 140 L836 140 L836 160", "lndash")

    types = [(1024, "player", "acc"), (1120, "tournament", "key"), (220, "source", "slot"),
             (1024, "type 3 — never used", "wash"), (314, "team", "rose"),
             (532, "game tag", "acc")]
    total = sum(size for size, _, _ in types)
    scale = 812 / total
    x, off = 24, 0
    for size, name, style in types:
        w = size * scale
        f.cell(x, 160, w - 3, 52, "", style, r=3)
        if w > 70:
            f.text(x + (w - 3) / 2, 184, name, "sm b " + INK[style], "middle")
            f.text(x + (w - 3) / 2, 200, f"{size} B", "xs mut", "middle")
        else:
            f.text(x + (w - 3) / 2, 190, str(size), "xs mut", "middle")
        f.text(x, 152, str(off), "xs m fnt")
        x += w
        off += size
    f.text(836, 152, str(total), "xs m fnt", "end")

    # The two narrow containers have no room for a label of their own.
    for start, size, name, ink in ((1024 + 1120, 220, "source", "slotc"),
                                   (1024 + 1120 + 220 + 1024, 314, "team", "rosec")):
        cx = 24 + (start + size / 2) * scale
        f.line(cx, 212, cx, 228, "ln")
        f.text(cx, 242, name, "xs b " + ink, "middle")
        f.text(cx, 256, f"{size} B", "xs mut", "middle")
    f.text(24, 282, "A type with fewer entities than there are blocks leaves its container empty "
                    "in the later blocks, and the file stops after the last record written.",
           "xs mut")
    f.save("entity-blocks.svg")


def lcd_file():
    """5-indexes.md — the .2lcd file as pages, and one catalog entry."""
    f = Fig(860, 312, "The .2lcd file is a row of 4096-byte pages: page 0 the header, page 1 the "
                      "catalog of indexes, and the rest directory pages and node pages. Below, "
                      "the 128-byte catalog entry of the Mega 2026 player index.")
    f.text(24, 28, "The file: a row of 4096-byte pages", "lg b")
    f.text(24, 46, "with the page numbers the player index of Mega Database 2026 uses", "xs mut")

    page_w = 110
    row = [("box", "page 0", "header", "wash"), ("box", "page 1", "catalog", "key"), ("gap",),
           ("box", f"page {MEGA['players_page']}", "directory", "slot"),
           ("box", f"page {MEGA['carlsen_dir2']}", "directory", "slot"), ("gap",),
           ("box", f"page {MEGA['carlsen_node_page']}", "nodes", "acc"), ("gap",)]
    x, spans = 24, {}
    for item in row:
        if item[0] == "gap":
            f.text(x + 13, 94, "…", "lg fnt", "middle")
            x += 26
        else:
            _, name, what, style = item
            f.cell(x, 62, page_w, 52, name, style, sub=what, r=4)
            spans[name] = (x, x + page_w)
            x += page_w + 6
    f.text(x + 4, 82, f"{MEGA['lcd_pages']} pages in all,", "sm mut")
    f.text(x + 4, 98, "31 MB", "sm mut")

    left = spans[f"page {MEGA['players_page']}"][0]
    right = spans[f"page {MEGA['carlsen_node_page']}"][1]
    f.path(f"M{left} 120 L{left} 128 L{right} 128 L{right} 120", "ln")
    f.text((left + right) / 2, 144,
           "the player index: two levels of directory, then its node pages", "xs mut", "middle")

    f.rect(24, 164, 812, 118, "box", 5)
    f.text(40, 186, "One catalog entry — 128 bytes, big-endian", "sm b")
    f.text(40, 202, "page 1 holds 32 of them, one per index; entries not in use have an empty "
                    "name", "xs mut")
    items = [(0x00, 44, "depth", "slot", MEGA["players_depth"]),
             (0x02, 62, "page", "slot", MEGA["players_page"]),
             (0x06, 88, "root node", "acc", MEGA["players_root"]),
             (0x0e, 88, "count", "box", MEGA["players_count"]),
             (0x16, 48, "type", "wash", "0+1"), (0x17, 40, "?", "wash", 3),
             (0x18, 40, "#", "wash", 0), (0x19, 40, "?", "wash", 1),
             (0x1a, 44, "len", "wash", 15),
             (0x1c, 318, "name", "key", "“Default Spieler”")]
    x = 40
    for off, w, label, style, value in items:
        f.cell(x, 220, w - 2, 34, label, style, r=2)
        f.text(x, 214, f"0x{off:02x}", "xs m fnt")
        f.text(x + (w - 2) / 2, 270, value, "xs m mut", "middle")
        x += w
    f.text(40, 292, "depth 0 — 0x02 is the one node page;  depth 1 or more — it is the "
                    "top directory page", "xs mut")
    f.save("lcd-file.svg")


def lcd_node():
    """5-indexes.md — the 40 bytes of a node, with a real one's values."""
    f = Fig(860, 216, "A node is 40 bytes: left child, right child and parent as 8-byte entity "
                      "ids, an 8-byte key, a 2-byte balance, 2 unused bytes and the 4-byte slot "
                      "number. The values shown are the node of Magnus Carlsen in Mega 2026.")
    f.text(24, 28, "A node — 40 bytes, little-endian", "lg b")
    f.text(24, 46, f"node {MEGA['carlsen_id']} of the player index is player "
                   f"{MEGA['carlsen_id']}, Magnus Carlsen", "xs mut")

    fields = [(0x00, 8, "left child", "acc", MEGA["carlsen_left"]),
              (0x08, 8, "right child", "acc", "−1"),
              (0x10, 8, "parent", "acc", MEGA["carlsen_parent"]),
              (0x18, 8, "key", "key", "“carlsen”"),
              (0x20, 2, "bal", "wash", "−1"), (0x22, 2, "—", "wash", 0),
              (0x24, 4, "slot", "slot", 55)]
    scale = 812 / 40
    x = 24
    for off, size, label, style, value in fields:
        w = size * scale
        f.cell(x, 86, w - 3, 46, label, style, r=3)
        f.text(x, 78, f"0x{off:02x}", "xs m fnt")
        f.text(x + (w - 3) / 2, 150, value, "sm m b mut", "middle")
        x += w
    f.text(24, 180, "entity ids, −1 where there is no such node", "xs acc")
    f.text(24 + 24 * scale, 180, "the first 8 bytes", "xs keyc")
    f.text(24 + 24 * scale, 194, "of the sort fields", "xs keyc")
    f.text(24 + 32 * scale, 180, "right height", "xs mut")
    f.text(24 + 32 * scale, 194, "minus left", "xs mut")
    f.text(836, 180, "id mod 102, or −1", "xs slotc", "end")
    f.text(836, 194, "on some leaves", "xs slotc", "end")
    f.save("lcd-node.svg")


def lcd_lookup():
    """5-indexes.md — reaching a node through two levels of directory."""
    f = Fig(860, 268, "Finding node 145915: 145915 divided by 102 is node page 1430, remainder "
                      "55. The catalog gives directory page 1867, whose entry 1 is page 1868, "
                      "whose entry 406 is page 2459, where slot 55 holds the node.")
    f.marker("b", "hacc")
    f.text(24, 28, "Finding a node when the index has a directory", "lg b")
    f.text(24, 46, f"Magnus Carlsen is player {MEGA['carlsen_id']} in Mega 2026, an index of "
                   f"depth {MEGA['players_depth']}", "xs mut")
    page, slot = divmod(MEGA["carlsen_id"], 102)
    f.text(24, 70, f"{MEGA['carlsen_id']} ÷ 102 = node page {page}, remainder {slot}",
           "sm m b")
    top, rest = divmod(page, 1024)
    f.text(24, 88, f"{page} = {top} × 1024 + {rest}, so each directory level peels off one "
                   f"base-1024 digit", "xs mut")

    cards = [(24, "catalog entry", "Default Spieler",
              [f"depth {MEGA['players_depth']}", f"page {MEGA['players_page']}"], "key"),
             (232, f"page {MEGA['players_page']}", "top directory",
              [f"entry {top}", f"→ page {MEGA['carlsen_dir2']}"], "slot"),
             (440, f"page {MEGA['carlsen_dir2']}", "directory",
              [f"entry {rest}", f"→ page {MEGA['carlsen_node_page']}"], "slot"),
             (648, f"page {MEGA['carlsen_node_page']}", "node page",
              [f"slot {slot}", "→ the node"], "acc")]
    for x, title, sub, lines, style in cards:
        f.cell(x, 110, 188, 96, "", style, r=5)
        f.text(x + 94, 134, title, "sm b", "middle")
        f.text(x + 94, 150, sub, "xs mut", "middle")
        f.text(x + 94, 176, lines[0], "sm m", "middle")
        f.text(x + 94, 194, lines[1], "sm m b", "middle")
    for x in (212, 420, 628):
        f.line(x, 158, x + 16, 158, "lnacc", "b")
    f.text(24, 230, "An entry of 0 means no id in that range belongs to the index, and such holes "
                    "may come before pages that exist.", "xs mut")
    f.text(24, 250, "Depth follows from the highest id, not the number of entities: up to 102 "
                    "→ depth 0, up to 104,448 → depth 1, beyond that → depth 2.",
           "xs mut")
    f.save("lcd-lookup.svg")


def lcd_tree():
    """5-indexes.md — a real subtree, and what an in-order walk gives."""
    f = Fig(860, 352, "A real subtree of the Mega 2026 player index, six players named Carlsen. "
                      "Walking it in order gives them sorted by first name, although all six "
                      "share the key carlsen.")
    f.text(24, 28, "Walking the tree gives the sort order", "lg b")
    f.text(24, 46, "a real subtree of the Mega 2026 player index — six of its Carlsens",
           "xs mut")

    width, height = 176, 48
    nodes = {
        "jimmy":  (430, 76, 158691, "Jimmy Gronbaek", "acc"),
        "ingrid": (216, 164, 237176, "Ingrid Oen", "box"),
        "magnus": (644, 164, MEGA["carlsen_id"], "Magnus", "box"),
        "henrik": (110, 252, 146997, "Henrik", "box"),
        "jasper": (322, 252, 217185, "Jasper", "box"),
        "kjell":  (538, 252, MEGA["carlsen_left"], "Kjell Arne", "box"),
    }
    for parent, child in (("jimmy", "ingrid"), ("jimmy", "magnus"), ("ingrid", "henrik"),
                          ("ingrid", "jasper"), ("magnus", "kjell")):
        f.line(nodes[parent][0], nodes[parent][1] + height, nodes[child][0], nodes[child][1], "ln")
    for cx, y, node_id, name, style in nodes.values():
        f.cell(cx - width / 2, y, width, height, "", style, r=8)
        f.text(cx, y + 20, name + " Carlsen", "sm b", "middle")
        f.text(cx, y + 36, f"node {node_id}", "xs m mut", "middle")
    f.text(430, 68, "root of this subtree", "xs mut", "middle")
    f.text(216, 158, "left", "xs fnt", "middle")
    f.text(644, 158, "right", "xs fnt", "middle")

    f.text(24, 322, "In order:", "sm b")
    order = ["Henrik", "Ingrid Oen", "Jasper", "Jimmy Gronbaek", "Kjell Arne", "Magnus"]
    f.text(92, 322, "  ·  ".join(order) +
           "  —  every one of them keyed “carlsen”", "sm")
    f.text(24, 342, "The key ties, so ChessBase compared the entities in full; the tree, not the "
                    "key, carries the order.", "xs mut")
    f.save("lcd-tree.svg")


def lgd_record():
    """5-indexes.md — the 64 slots of a .2lgd record."""
    f = Fig(860, 348, "A .2lgd record is 512 bytes, 64 longs, in two independent halves. Slots 0 "
                      "to 31 head the game lists of every entity with that id; slots 32 to 63 "
                      "are one block of a shared pool used by long lists.")
    f.text(24, 28, "One record — 512 bytes, 64 longs, two independent halves", "lg b")
    f.text(24, 46, "record 11576 of Mega Database 2026", "xs mut")

    cell_w, cell_h = 44, 30

    def head_style(k):
        if 1 <= k <= 8:
            return "acc"
        if 11 <= k <= 18:
            return "key"
        if 21 <= k <= 28:
            return "slot"
        return "rose" if k == 30 else "wash"

    def block_style(k):
        return {32: "acc", 33: "key"}.get(k, "slot")

    def grid(x, y, base, style_of):
        for i in range(32):
            r, c = divmod(i, 8)
            f.cell(x + c * cell_w, y + r * cell_h, cell_w - 3, cell_h - 3, str(base + i),
                   style_of(base + i), r=2)

    f.text(32, 78, "Heads — slots 0–31", "sm b")
    f.text(32, 94, "the lists of every entity with id 11576", "xs mut")
    grid(32, 104, 0, head_style)
    f.text(476, 78, "List block 11576 — slots 32–63", "sm b")
    f.text(476, 94, "a block of a pool any long list may use", "xs mut")
    grid(476, 104, 32, block_style)
    f.line(430, 104, 430, 224, "lndash")

    y = 250
    for x, style, label in ((32, "acc", "first"), (112, "key", "last"), (192, "slot", "count"),
                            (272, "rose", "always 12"), (476, "acc", "next block"),
                            (576, "key", "count"), (656, "slot", "up to 30 game ids")):
        f.rect(x, y - 9, 12, 12, FILLS[style], 2)
        f.text(x + 18, y, label, "xs mut")
    f.text(32, y + 20, "role k uses slots k, 10+k and 20+k", "xs mut")
    f.text(476, y + 20, "every block of a chain is full but the last", "xs mut")
    f.text(32, y + 42, "roles:  1 player · 2 tournament · 3 source · 4 annotator "
                       "· 5 team · 6 game tag · 7 text title · 8 analysis "
                       "title", "xs mut")
    f.text(24, 328, "The halves are unrelated: block 11576 is not the block of entity 11576. The "
                    f"file has as many records as whichever half needs more — "
                    f"{MEGA['lgd_records']} in Mega 2026.", "xs mut")
    f.save("lgd-record.svg")


def lgd_forms():
    """5-indexes.md — single, range and chain, from one record."""
    f = Fig(860, 340, "The three forms a game list takes, all three from record 11576 of Mega "
                      "2026: a single pair of games for the player, a range for the tournament, "
                      "and a chain of blocks for the team.")
    f.marker("c", "hacc")
    f.text(24, 28, "Three ways to write a list", "lg b")
    f.text(24, 46, "record 11576 of Mega 2026 happens to use all three — its player, its "
                   "tournament and its team", "xs mut")

    rows = [
        (74, "single", "player 11576", "E Caroe", "acc",
         f"0x4000… + {MEGA['single_first']}", str(MEGA["single_last"]), "2",
         f"games {MEGA['single_first']} and {MEGA['single_last']}"),
        (162, "range", "tournament 11576", "October Revolution 50years", "key",
         f"0x2000… + {MEGA['range_first']}", f"0x2000… + {MEGA['range_last']}",
         str(MEGA["range_count"]),
         f"games {MEGA['range_first']} … {MEGA['range_last']}"),
        (250, "chain", "team 11576", "Schwerin Schachfreunde", "slot",
         f"block {MEGA['chain_first']}", f"block {MEGA['chain_last']}",
         str(MEGA["chain_count"]), "three blocks: 30 + 30 + 12"),
    ]
    for y, form, who, name, style, first, last, count, result in rows:
        f.text(24, y + 4, form, "sm b")
        f.text(24, y + 20, who, "xs mut")
        f.text(24, y + 34, name, "xs fnt")
        f.cell(184, y - 14, 196, 38, "", style, r=3)
        f.text(194, y - 1, "first", "xs mut")
        f.text(194, y + 15, first, "sm m b")
        f.cell(390, y - 14, 166, 38, "", style, r=3)
        f.text(400, y - 1, "last", "xs mut")
        f.text(400, y + 15, last, "sm m b")
        f.cell(566, y - 14, 70, 38, "", "wash", r=3)
        f.text(576, y - 1, "count", "xs mut")
        f.text(576, y + 15, count, "sm m b")
        f.line(642, y + 5, 660, y + 5, "lnacc", "c")
        f.text(668, y + 9, result, "sm")

    f.text(24, 316, "The top bits of first choose the form. A list is in ascending order, an "
                    "entity referred to twice is listed twice, and deleted games stay in it.",
           "xs mut")
    f.save("lgd-forms.svg")


FIGURES = [files, game_record, cbg_record, entity_blocks,
           lcd_file, lcd_node, lcd_lookup, lcd_tree, lgd_record, lgd_forms]


if __name__ == "__main__":
    for figure in FIGURES:
        figure()
