package se.yarin.chess.pgn;

/**
 * Configuration options for PGN export formatting.
 */
public record PgnFormatOptions(
        int maxLineLength,
        boolean includeOptionalHeaders,
        boolean includePlyCount,
        boolean exportVariations,
        boolean exportComments,
        boolean exportNAGs,
        boolean useSymbolsForNAGs,
        String lineEnding
) {
    /**
     * Default PGN format options.
     */
    public static final PgnFormatOptions DEFAULT = new PgnFormatOptions(
            79,      // maxLineLength (PGN standard)
            true,    // includeOptionalHeaders
            true,    // includePlyCount
            true,    // exportVariations
            true,    // exportComments
            true,    // exportNAGs
            false,   // useSymbolsForNAGs (use $N format)
            "\n"     // lineEnding
    );

    public static final PgnFormatOptions DEFAULT_WITHOUT_PLYCOUNT = new PgnFormatOptions(
            79,      // maxLineLength (PGN standard)
            true,    // includeOptionalHeaders
            false,    // includePlyCount
            true,    // exportVariations
            true,    // exportComments
            true,    // exportNAGs
            false,   // useSymbolsForNAGs (use $N format)
            "\n"     // lineEnding
    );

    /**
     * Compact format options (no variations, no comments).
     */
    public static final PgnFormatOptions COMPACT = new PgnFormatOptions(
            79,      // maxLineLength
            false,   // includeOptionalHeaders
            false,   // includePlyCount
            false,   // exportVariations
            false,   // exportComments
            false,   // exportNAGs
            false,   // useSymbolsForNAGs
            "\n"     // lineEnding
    );

    public PgnFormatOptions {
        if (maxLineLength < 20) {
            throw new IllegalArgumentException("maxLineLength must be at least 20");
        }
        if (lineEnding == null) {
            lineEnding = "\n";
        }
    }
}
