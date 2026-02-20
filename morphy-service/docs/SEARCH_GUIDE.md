# Search Interface Guide

There are two search modes: **Game Search** finds games matching filter criteria, and **Entity Search** finds entities (players, tournaments, etc.) directly.

Both use the same query syntax and share entity filter fields. The difference is that Game Search can combine game-level filters (result, date, rating, etc.) with entity filters via joins, while Entity Search uses only the fields for that entity type.

## Query Syntax

Filters use `field:value` syntax. Multiple filters are AND-ed together. The keyword `AND` between filters is optional.

```
result:1-0 date:2024
result:1-0 AND player.name:Carlsen
```

**Bare terms** (no field prefix) default to a context-dependent field: `player.name` in Game Search, or the entity's primary field in Entity Search (e.g., `name` for players, `title` for sources).

```
Carlsen                → player.name:Carlsen  (in Game Search)
Carlsen                → name:Carlsen          (in Player Search)
```

**Modifiers** are comma-separated `key=value` pairs appended to the value:

```
player.name:Carlsen,position=white
rating:2600..,mode=both
```

## Game Filters

The following filters are only available in Game Search.

### result

Match game results: `1-0`, `0-1`, `draw`. Aliases: `white`/`i-o` for White wins, `black`/`o-i` for Black wins, `0-0`/`o-o` for draws.

```
result:1-0
result:draw
```

### date

Partial dates expand automatically: `2024` covers the full year, `2024-03` covers March 2024.

Ranges use `..` syntax:

```
date:2024              # All of 2024
date:2024-03           # March 2024
date:2024-03-15        # Exact date
date:2020..2025        # Range (inclusive)
date:2020..            # From 2020 onwards
date:..2025            # Up to end of 2025
```

### rating

Filter by player Elo rating. Single values match exactly; ranges use `..`.

```
rating:2700            # Exactly 2700
rating:2600..2800      # Range
rating:2600..          # 2600 or higher
```

**Modifiers** (`mode=`): `any` (default, either player), `both`, `white`, `black`, `average`, `difference`.

```
rating:2700..,mode=both       # Both players rated 2700+
rating:100..200,mode=difference  # Rating difference 100-200
```

### eco

ECO opening codes. Supports `*` wildcards.

```
eco:B90
eco:B9*                # B90-B99
eco:B*                 # B00-B99
```

### round

```
round:5                # Round 5 (any sub-round)
round:5.2              # Round 5, sub-round 2
round:5,subround=2     # Same as above
```

### type

Filter by record type: `game` (actual games) or `text` (guiding text entries).

```
type:game
type:text
```

## Entity Filters

These fields work in both search modes:

- **Game Search**: Use dot notation (`player.name:Carlsen`). Games are joined to matching entities. Multiple filters on the same entity type are combined into a single join, so `player.name:Carl player.name:son` matches players whose name contains both substrings, not two different players.
- **Entity Search**: Use the field name directly (`name:Carlsen`), without the entity prefix.

### Player

Default field: `name`.

| Field | Description |
|-------|-------------|
| `player.name` | Case-insensitive name match. Supports `\|` for OR: `Carlsen\|Fischer` |

**Position modifier** (`position=`): `any` (default), `both`, `white`, `black`, `winner`, `loser`.

```
player.name:Carlsen,position=white
```

### Tournament

Default field: `name`.

| Field | Description |
|-------|-------------|
| `tournament.name` / `tournament.title` | Substring match |
| `tournament.date` | Start date (same syntax as game date filter) |
| `tournament.year` | Convenience for date; `year:2024` = `date:2024` |
| `tournament.place` | Substring match |
| `tournament.nation` | Country code (e.g., `NL`, `USA`) |
| `tournament.type` | Tournament type (e.g., `round-robin`) |
| `tournament.time` | Time control (e.g., `rapid`) |
| `tournament.category` | Single value = minimum; range with `..` |
| `tournament.rounds` | Exact or range with `..` |
| `tournament.teams` | Boolean flag (`any`) |

```
tournament.name:Wijk tournament.year:2024
tournament.category:15..20
```

### Annotator

Default field: `name`.

| Field | Description |
|-------|-------------|
| `annotator.name` | Substring match |

### Source

Default field: `title`.

| Field | Description |
|-------|-------------|
| `source.title` / `source.name` | Substring match |

### Team

Default field: `title`. Supports `position=` modifier (same as player, except no `both`).

| Field | Description |
|-------|-------------|
| `team.title` / `team.name` | Substring match |

### Game Tag

Default field: `name`.

| Field | Description |
|-------|-------------|
| `gametag.name` / `gametag.title` | Substring match |

## Entity ID Filters

For direct lookup by internal database ID. These are mainly used programmatically rather than typed by users.

```
player:123
tournament:456
annotator:789
source:321
team:654
gametag:987
```

Player and team ID filters also support the `position=` modifier.
