"""Choosing which attributes of an object are shown as table columns.

A column is named after an attribute of the objects in the table: either a
dataclass field or a property (so calculated values work just like stored
ones). A class says which columns it is shown with by default using the
@default_columns decorator, and a caller can override that with a list of
column names, either absolute ("a,b") or relative to the default columns
("+a" adds a column, "-b" removes one). The "id" column is special and is
always shown first.
"""
import dataclasses

from morphy.util import format_table

ID_COLUMN = "id"


def available_columns(cls):
    """Names of the attributes of cls that can be columns: its dataclass
    fields, then its properties, skipping private (underscore) names."""
    names = [f.name for f in dataclasses.fields(cls)]
    for klass in reversed(cls.__mro__):
        names.extend(name for name, value in vars(klass).items() if isinstance(value, property))
    return [name for name in dict.fromkeys(names) if not name.startswith("_")]


def default_columns(*names):
    """Class decorator (apply it on top of @dataclass) that sets which
    columns are shown for the class by default. The id column is implied."""
    def decorate(cls):
        unknown = [name for name in names if name not in available_columns(cls)]
        if unknown:
            raise ValueError(f"{cls.__name__} has no attribute(s) {', '.join(unknown)} to use as default columns")
        cls.DEFAULT_COLUMNS = tuple(dict.fromkeys(names))
        return cls
    return decorate


def parse_columns(cls, spec=None):
    """Return the list of columns to show for cls, with the id column first.

    spec is None for the default columns of cls, or a comma-separated string
    of column names. A plain name is a column to show, "+name" adds a column
    to the default columns, and "-name" removes one from them. The default
    columns are only the starting point if every name in spec is relative
    ("+"/"-"); if spec has any plain name, the columns are just the ones
    named. (An empty spec therefore gives only the id column.) The names are
    applied in order, so mixing the two kinds works, but is not very useful.
    Removing a column that isn't shown is not an error, and neither is
    trying to remove the id. Raises ValueError for an unknown column."""
    if spec is None:
        return [ID_COLUMN] + [name for name in cls.DEFAULT_COLUMNS if name != ID_COLUMN]

    available = available_columns(cls)
    changes = []  # (sign, name), where sign is "+", "-" or "" for a plain name
    for token in (token.strip() for token in spec.split(",")):
        if not token:
            continue
        sign = token[0] if token[0] in "+-" else ""
        name = token[len(sign):].strip()
        if not name:
            raise ValueError(f"no column name after {sign!r}")
        if name not in available:
            raise ValueError(f"unknown column {name} (available: {', '.join(available)})")
        changes.append((sign, name))

    relative = bool(changes) and all(sign for sign, _ in changes)
    names = list(cls.DEFAULT_COLUMNS) if relative else []
    for sign, name in changes:
        if sign == "-":
            names = [n for n in names if n != name]
        elif name not in names:
            names.append(name)
    return [ID_COLUMN] + [name for name in names if name != ID_COLUMN]


def column_header(name):
    return name.replace("_", " ").title()


def format_objects(objects, columns):
    """Format objects as a table with the given columns (attribute names)."""
    headers = [column_header(name) for name in columns]
    rows = []
    for obj in objects:
        cells = [getattr(obj, name) for name in columns]
        rows.append(["" if cell is None else cell for cell in cells])
    return format_table(headers, rows)
