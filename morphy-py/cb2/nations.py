"""Nations, stored as a one-byte index. The numbering is the same as in the
older format (v1), and it was checked by comparing every tournament of `wch2`
with its v1 counterpart; see format/v2/README.md.

The names are IOC codes where one exists, filled out with ISO codes, which is
what ChessBase itself uses. Index 0 means no nation.
"""

NATIONS = [
    "", "AFG", "ALB", "ALG", "AND", "ANG", "ANT", "ARG", "ARM", "AUS", "AUT", "AZE", "BAH",
    "BRN", "BAN", "BAR", "BLR", "BEL", "BIZ", "BER", "BOL", "BIH", "BOT", "BRA", "IVB", "BRU",
    "BUL", "BUR", "CAN", "CHI", "CHN", "COL", "CRC", "CRO", "CUB", "CYP", "CZE", "DEN", "DJI",
    "DOM", "ECU", "EGY", "ENG", "ESP", "EST", "ETH", "FRO", "FIJ", "FIN", "FRA", "MKD", "GAM",
    "GEO", "GER", "GHA", "GRE", "GUA", "GCI", "GUY", "HAI", "HON", "HKG", "HUN", "ISL", "IND",
    "INA", "IRI", "IRQ", "ISR", "IRL", "ITA", "CIV", "JAM", "JPN", "GCI", "JOR", "KAZ", "KEN",
    "KOR", "KGZ", "KUW", "LAT", "LIB", "LBA", "LIE", "LTU", "LUX", "MAC", "MAD", "MAS", "MLI",
    "MLT", "MTN", "MRI", "MEX", "MDA", "MON", "MGL", "MAR", "MOZ", "MYA", "NAM", "NEP", "NED",
    "AHO", "NZL", "NCA", "NGR", "NOR", "PAK", "PLE", "PAN", "PNG", "PAR", "PER", "PHI", "POL",
    "POR", "PUR", "QAT", "ROU", "RUS", "ESA", "SMR", "SCO", "SEN", "SEY", "SIN", "SVK", "SLO",
    "RSA", "SRI", "SUD", "SUR", "SWE", "SUI", "SYR", "TJK", "TAN", "THA", "TTO", "TUN", "TUR",
    "TKM", "UGA", "UKR", "UAE", "USA", "URU", "URS", "UZB", "VEN", "VIE", "ISV", "WLS", "YEM",
    "SCG", "ZAM", "ZIM", "ZAI", "DDR", "TCH", "CMR", "CHA", "CPV", "KIR", "COM", "CGO", "PRK",
    "LAO", "LES", "MAW", "MDV", "MHL", "OMA", "NRU", "MIC", "NIG", "KSA", "TOG", "TGA", "VAN",
    "VAT", "TUV", "SWZ", "SLE", "LCA", "PAP", "SVI", "SAM", "SKI", "SOL", "GE2", "ZAR", "RWA",
    "LBR", "NET", "TPE", "ASA", "AGG", "ARU", "BEN", "BHU", "BDI", "CAM", "CAY", "CAF", "GCI",
    "CIA", "COA", "COK", "GEQ", "ERI", "FGB", "FRG", "FRP", "GAB", "GGB", "GRN", "GRL", "FGA",
    "GUM", "GUI", "GBS", "IOM", "JMY", "MFR", "MYF", "MSG", "NCF", "NNN", "NNA", "NMI", "OTM",
    "PLW", "PIG", "RUF", "STP", "SOM", "SVN", "HGB", "PGB", "TKI", "TCI", "WFR", "NIR", "ISS",
    "GBR", "SAA", "MNE", "SRB", "CAT", "BAS", "KOS"
]


def decode_nation(value):
    """The IOC code of a nation, "" if none is set, and "?<value>" if the
    value is outside the table."""
    if 0 <= value < len(NATIONS):
        return NATIONS[value]
    return f"?{value}"


def decode_language(value):
    """ChessBase numbers languages the same way it numbers nations, so a
    language is the IOC code of the country it belongs to: 42 (ENG) is
    English, 53 (GER) is German. Only a few of the values are ever used."""
    return decode_nation(value)
