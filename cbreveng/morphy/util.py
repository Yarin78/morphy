"""Minimal ASCII table rendering, kept separate from whatever gathers the
data so the output format can be swapped out (or reused elsewhere) later
without touching the data-gathering code."""


def format_table(headers, rows):
    str_rows = [[str(cell) for cell in row] for row in rows]
    widths = [len(str(h)) for h in headers]
    for row in str_rows:
        for i, cell in enumerate(row):
            widths[i] = max(widths[i], len(cell))

    def format_row(cells):
        return "| " + " | ".join(cell.ljust(w) for cell, w in zip(cells, widths)) + " |"

    sep = "+" + "+".join("-" * (w + 2) for w in widths) + "+"
    lines = [sep, format_row(headers), sep]
    lines.extend(format_row(row) for row in str_rows)
    lines.append(sep)
    return "\n".join(lines)
