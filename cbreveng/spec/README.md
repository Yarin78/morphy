# The ChessBase 2 database format

A specification of the on-disk format used by ChessBase databases whose files
have the `.2cbh` family of extensions, sufficient to implement a reader and a
writer. The format version byte found in these files is 5.

Anything not yet understood is marked **unknown**. Where there is evidence for a
likely meaning it is given as such. Everything else is stated as fact.

| Document | Contents |
|---|---|
| [1-game-headers.md](1-game-headers.md) | `.2cbh`: one record per game, guiding text or analysis |
| [2-moves.md](2-moves.md) | `.2cbg`: the moves of a game, and the body of a guiding text |
| [3-annotations.md](3-annotations.md) | `.2cba`: comments, symbols and other annotations |
| [4-entities.md](4-entities.md) | `.2lid`: players, tournaments, sources, teams, game tags |
| [5-indexes.md](5-indexes.md) | `.2lcd` sort orders and `.2lgd` game lists |
| [6-behaviour.md](6-behaviour.md) | How ChessBase uses the format: free space, placeholders, statistics |

## Files

A database is a set of files sharing one base name. All of them are needed to
read a database in full except the `.ini`.

| Extension | Contents |
|---|---|
| `.2cbh` | game headers — the spine of the database, one fixed-size record per game |
| `.2cbg` | moves, and the body of each guiding text |
| `.2cba` | annotations |
| `.2lid` | entities: players, tournaments, sources, teams, game tags |
| `.2lgd` | for each entity, the games that refer to it |
| `.2lcd` | for each kind of entity, its sort order |
| `.ini` | settings and user-interface state, in plain text; not needed to read the database |

A database may also carry `.cko` and `.cpo` files, holding the opening
classification key and its positions. They are not part of this format and are
not described here.

Records in `.2cbh` are addressed by a 1-based **game id**. Entities are addressed
by a 0-based **entity id**, per entity type.

## Conventions

Offsets are hexadecimal and relative to the start of the structure being
described. Sizes are in bytes.

`byte`, `short`, `int` and `long` are signed integers of 1, 2, 4 and 8 bytes.
`ushort` and `uint` are their unsigned counterparts.

**Integers are little-endian**, with three exceptions:

- the `.2lid` file header;
- the `.2lcd` file header and catalog (its tree nodes and directory pages are
  little-endian);
- the checksum field of each `.2cbg` and `.2cba` record.

A **string** is an `int` byte count followed by that many bytes of UTF-8, with no
terminator. The count is in bytes, not characters. Fixed-size strings, where they
occur, are noted at the point of use; they are padded with zero bytes and carry
no terminator when they fill the field.

Fields whose meaning is unknown are zero in every database examined unless stated
otherwise. A writer should reproduce the values it read, and write zero in a new
record.

## File headers

Every file begins with a header whose byte order follows the rest of that file.
Each header fixes the shape of the file with a size or a count.

| File | Header size | Contents |
|---|---|---|
| `.2cbh` | 192 | next game id, record size, format version |
| `.2cbg` | 12 | file size, header size, format version |
| `.2cba` | 12 | file size, header size |
| `.2lid` | 184 | per entity type: container size, count, first deleted id |
| `.2lgd` | 12 | number of list blocks in use |
| `.2lcd` | 4096 (page 0) | page count, page size |

## Common encodings

### Dates

A date is packed into an `int`:

| Bits | Field |
|---|---|
| 0-4 | day, 1-31 |
| 5-8 | month, 1-12 |
| 9-20 | year |

Bits above 20 are ignored. A part that is 0 is unknown, so 0 is an entirely
unknown date and a year alone is a valid date. Used for played dates, tournament
start and end dates, source dates, and the access date in the `.ini`.

### Timestamps

Two distinct encodings, both `long`:

| Name | Epoch | Unit |
|---|---|---|
| creation | 2008-12-01 00:00 Europe/Berlin | 1/2²² second |
| last changed | 1582-10-15 00:00 UTC | 100 nanoseconds |

A last-changed timestamp of 0 means the record has not been changed since it was
created.

A creation timestamp may also appear at 1/2¹⁰ second, 4096 times smaller, in
databases converted from the previous ChessBase format. The two ranges do not
overlap in practice — a 1/2¹⁰ value does not reach 2⁴⁰ until the 2040s, and a
1/2²² value falls below 2⁴⁰ only within days of the epoch — so a reader can pick
the scale by comparing the value with 2⁴⁰.

### Nation codes

A nation is a one-byte index into the table below, which holds IOC codes where
one exists and ISO codes otherwise. Index 0 means no nation. The table includes
historical states (149 Soviet Union, 161 Czechoslovakia, 192 German Empire,
193 Russian Empire, 160 East Germany) and one pseudo-nation, 196 `NET`, meaning
the internet.

The same numbering identifies **languages**, of which ChessBase uses seven: 42
English, 43 Spanish, 49 French, 53 German, 70 Italian, 103 Dutch and 117
Portuguese. Annotation text uses a different and much smaller language numbering;
see [3-annotations.md](3-annotations.md).

```
  0 -       1 AFG     2 ALB     3 ALG     4 AND     5 ANG     6 ANT     7 ARG
  8 ARM     9 AUS    10 AUT    11 AZE    12 BAH    13 BRN    14 BAN    15 BAR
 16 BLR    17 BEL    18 BIZ    19 BER    20 BOL    21 BIH    22 BOT    23 BRA
 24 IVB    25 BRU    26 BUL    27 BUR    28 CAN    29 CHI    30 CHN    31 COL
 32 CRC    33 CRO    34 CUB    35 CYP    36 CZE    37 DEN    38 DJI    39 DOM
 40 ECU    41 EGY    42 ENG    43 ESP    44 EST    45 ETH    46 FRO    47 FIJ
 48 FIN    49 FRA    50 MKD    51 GAM    52 GEO    53 GER    54 GHA    55 GRE
 56 GUA    57 GCI    58 GUY    59 HAI    60 HON    61 HKG    62 HUN    63 ISL
 64 IND    65 INA    66 IRI    67 IRQ    68 ISR    69 IRL    70 ITA    71 CIV
 72 JAM    73 JPN    74 GCI    75 JOR    76 KAZ    77 KEN    78 KOR    79 KGZ
 80 KUW    81 LAT    82 LIB    83 LBA    84 LIE    85 LTU    86 LUX    87 MAC
 88 MAD    89 MAS    90 MLI    91 MLT    92 MTN    93 MRI    94 MEX    95 MDA
 96 MON    97 MGL    98 MAR    99 MOZ   100 MYA   101 NAM   102 NEP   103 NED
104 AHO   105 NZL   106 NCA   107 NGR   108 NOR   109 PAK   110 PLE   111 PAN
112 PNG   113 PAR   114 PER   115 PHI   116 POL   117 POR   118 PUR   119 QAT
120 ROU   121 RUS   122 ESA   123 SMR   124 SCO   125 SEN   126 SEY   127 SIN
128 SVK   129 SLO   130 RSA   131 SRI   132 SUD   133 SUR   134 SWE   135 SUI
136 SYR   137 TJK   138 TAN   139 THA   140 TTO   141 TUN   142 TUR   143 TKM
144 UGA   145 UKR   146 UAE   147 USA   148 URU   149 URS   150 UZB   151 VEN
152 VIE   153 ISV   154 WLS   155 YEM   156 SCG   157 ZAM   158 ZIM   159 ZAI
160 DDR   161 TCH   162 CMR   163 CHA   164 CPV   165 KIR   166 COM   167 CGO
168 PRK   169 LAO   170 LES   171 MAW   172 MDV   173 MHL   174 OMA   175 NRU
176 MIC   177 NIG   178 KSA   179 TOG   180 TGA   181 VAN   182 VAT   183 TUV
184 SWZ   185 SLE   186 LCA   187 PAP   188 SVI   189 SAM   190 SKI   191 SOL
192 GE2   193 ZAR   194 RWA   195 LBR   196 NET   197 TPE   198 ASA   199 AGG
200 ARU   201 BEN   202 BHU   203 BDI   204 CAM   205 CAY   206 CAF   207 GCI
208 CIA   209 COA   210 COK   211 GEQ   212 ERI   213 FGB   214 FRG   215 FRP
216 GAB   217 GGB   218 GRN   219 GRL   220 FGA   221 GUM   222 GUI   223 GBS
224 IOM   225 JMY   226 MFR   227 MYF   228 MSG   229 NCF   230 NNN   231 NNA
232 NMI   233 OTM   234 PLW   235 PIG   236 RUF   237 STP   238 SOM   239 SVN
240 HGB   241 PGB   242 TKI   243 TCI   244 WFR   245 NIR   246 ISS   247 GBR
248 SAA   249 MNE   250 SRB   251 CAT   252 BAS   253 KOS
```

### Squares

Squares are numbered `a1` = 0, `a2` = 1, … `a8` = 7, `b1` = 8, … `h8` = 63: file
by file, and within a file from rank 1 upward.
