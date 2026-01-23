package se.yarin.chess.pgn;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.*;
import se.yarin.chess.annotations.AnnotationTransformer;

import java.io.Reader;
import java.io.StringReader;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Main parser for PGN (Portable Game Notation) files.
 * Converts PGN text into GameModel objects with the default
 * chess annotations, unless an annotation converter has been
 * specified.
 */
public class PgnParser {

    private final AnnotationTransformer annotationTransformer;

    /**
     * Creates a parser without an annotation transformer.
     */
    public PgnParser() {
        this(null);
    }

    /**
     * Creates a parser with an annotation transformer.
     *
     * @param annotationTransformer the transformer to apply to annotations (may be null)
     */
    public PgnParser(@Nullable AnnotationTransformer annotationTransformer) {
        this.annotationTransformer = annotationTransformer;
    }

    /**
     * Parses a single game from a PGN string.
     *
     * @param pgnText the PGN text to parse
     * @return the parsed game model
     * @throws PgnFormatException if the PGN is invalid
     */
    @NotNull
    public GameModel parseGame(@NotNull String pgnText) throws PgnFormatException {
        return parseGame(new StringReader(pgnText));
    }

    /**
     * Parses a single game from a Reader.
     *
     * @param reader the reader to parse from
     * @return the parsed game model
     * @throws PgnFormatException if the PGN is invalid
     */
    @NotNull
    public GameModel parseGame(@NotNull Reader reader) throws PgnFormatException {
        PgnLexer lexer = new PgnLexer(reader);
        GameModel game = parseNextGame(lexer);
        if (game == null) {
            throw new PgnFormatException("No game found in input");
        }
        return game;
    }

    /**
     * Parses multiple games from a PGN reader lazily.
     * Returns a stream that reads and parses games on-demand.
     *
     * @param reader the reader to parse from
     * @return a stream of parsed game models
     */
    @NotNull
    public Stream<GameModel> parseGames(@NotNull Reader reader) {
        PgnLexer lexer = new PgnLexer(reader);
        return Stream.generate(() -> {
            try {
                return parseNextGame(lexer);
            } catch (PgnFormatException e) {
                throw new RuntimeException("Failed to parse PGN");
            }
        })
        .takeWhile(Objects::nonNull);
    }

    /**
     * Parses the next game from a PGN lexer.
     * Returns null when no more games are available (EOF).
     *
     * @param lexer the lexer to parse from
     * @return the next parsed game model, or null if EOF
     * @throws PgnFormatException if the PGN is invalid
     */
    private GameModel parseNextGame(PgnLexer lexer) throws PgnFormatException {
        // Parse headers
        HeaderParseResult headerResult = parseHeadersWithToken(lexer);

        // If we got EOF immediately, we're done
        if (headerResult.nextToken.type() == PgnToken.TokenType.EOF) {
            return null;
        }

        GameHeaderModel header = headerResult.header;

        // Check that the seven tag rosters all exist
        // Note: If round is set to "?", it will be treated at missing by the parser,
        // so we have to skip that check
        if (header.getEvent() == null || header.getEventSite() == null || header.getDate() == null || header.getWhite() == null || header.getBlack() == null || header.getResult() == null) {
            throw new PgnFormatException("The header tags Event, Site, Date, Round, White, Black and Result must all exist.");
        }

        // Create moves model (check for FEN setup)
        GameMovesModel moves = createMovesModel(header);

        // Parse move text (starting with the token after headers)
        parseMoveText(lexer, moves, headerResult.nextToken);

        return new GameModel(header, moves);
    }

    /**
     * Result of header parsing, including the next token.
     */
    private static class HeaderParseResult {
        final GameHeaderModel header;
        final PgnToken nextToken;

        HeaderParseResult(GameHeaderModel header, PgnToken nextToken) {
            this.header = header;
            this.nextToken = nextToken;
        }
    }

    private HeaderParseResult parseHeadersWithToken(PgnLexer lexer) throws PgnFormatException {
        PgnHeaderParser headerParser = new PgnHeaderParser();

        PgnToken token = lexer.nextToken();

        while (token.type() == PgnToken.TokenType.TAG_OPEN) {
            // Expect tag name (which comes as MOVE_TEXT from the lexer)
            PgnToken nameToken = lexer.nextToken();
            if (nameToken.type() != PgnToken.TokenType.MOVE_TEXT) {
                throw new PgnFormatException("Expected tag name", nameToken.line(), nameToken.column());
            }

            // Expect tag value (string)
            PgnToken valueToken = lexer.nextToken();
            if (valueToken.type() != PgnToken.TokenType.TAG_VALUE) {
                throw new PgnFormatException("Expected tag value", valueToken.line(), valueToken.column());
            }

            // Expect closing bracket
            PgnToken closeToken = lexer.nextToken();
            if (closeToken.type() != PgnToken.TokenType.TAG_CLOSE) {
                throw new PgnFormatException("Expected ]", closeToken.line(), closeToken.column());
            }

            // Parse the tag
            headerParser.parseTag(nameToken.value(), valueToken.value());

            // Get next token
            token = lexer.nextToken();
        }

        // The last token we read is the first token of the move text
        return new HeaderParseResult(headerParser.getHeader(), token);
    }

    private GameMovesModel createMovesModel(GameHeaderModel header) throws PgnFormatException {
        // Check for FEN setup position
        Object fenObj = header.getField("FEN");
        Object setupObj = header.getField("SetUp");

        if (setupObj != null && "1".equals(setupObj.toString()) && fenObj != null) {
            PositionState fen = PositionState.fromFen(fenObj.toString());
            return new GameMovesModel(fen.position(), fen.fullMoveNumber());
        }

        // Standard starting position
        return new GameMovesModel();
    }

    private void parseMoveText(PgnLexer lexer, GameMovesModel moves, PgnToken firstToken) throws PgnFormatException {
        PgnGameBuilder builder = new PgnGameBuilder(moves);

        PgnToken token = firstToken;

        while (token.type() != PgnToken.TokenType.EOF && token.type() != PgnToken.TokenType.RESULT) {
            switch (token.type()) {
                case MOVE_NUMBER:
                    // Skip move numbers (could validate)
                    break;

                case MOVE_TEXT:
                    // Parse the move
                    Position currentPos = builder.getCurrentPosition();
                    PgnMoveParser moveParser = new PgnMoveParser(currentPos);
                    Move move = moveParser.parseMove(token.value());
                    builder.addMove(move);
                    break;

                case NAG:
                    // Parse NAG annotation
                    String nagStr = token.value();
                    if (nagStr.startsWith("$")) {
                        int nagValue = Integer.parseInt(nagStr.substring(1));
                        if (nagValue >= 0 && nagValue < NAG.values().length) {
                            NAG nag = NAG.values()[nagValue];
                            builder.addNAG(nag);
                        }
                    }
                    break;

                case COMMENT:
                    // Determine if this is a before-move or after-move comment
                    // 1. If comment contains before-move markers ([%pre ...] or [%pre:XXX ...])
                    // 2. Otherwise, treat as before-move if we're at root or just started a variation
                    // 3. Otherwise treat as after-move (the common case)
                    if (isBeforeMoveComment(token.value()) || builder.isAtBeforeCommentPosition()) {
                        builder.addCommentBefore(token.value());
                    } else {
                        builder.addCommentAfter(token.value());
                    }
                    break;

                case VARIATION_START:
                    builder.startVariation();
                    break;

                case VARIATION_END:
                    builder.endVariation();
                    break;

                case TAG_OPEN:
                case TAG_CLOSE:
                case TAG_NAME:
                case TAG_VALUE:
                    throw new PgnFormatException("Unexpected tag in move text", token.line(), token.column());

                default:
                    throw new PgnFormatException("Unexpected token: " + token.type(), token.line(), token.column());
            }

            token = lexer.nextToken();
        }

        // Verify the result if present
        if (token.type() == PgnToken.TokenType.RESULT) {
            // Could validate against header result
            // For now, just accept it
        }

        // Apply annotation transformer if present
        if (annotationTransformer != null) {
            moves.root().traverseDepthFirst(node -> {
                // Determine which player made the last move to reach this node
                Player lastMoveBy = node.isRoot() ? null : node.parent().position().playerToMove();
                annotationTransformer.transform(node.getAnnotations(), lastMoveBy);
            });
        }
    }

    /**
     * Checks if a comment contains markers that indicate it's a before-move comment.
     * These markers are: [%pre ...] or [%pre:XXX ...]
     */
    private static boolean isBeforeMoveComment(String comment) {
        return comment.contains("[%pre ") ||
               comment.contains("[%pre:");
    }
}
