package se.yarin.cbhlib.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.annotations.AnnotationsSerializer;
import se.yarin.cbhlib.entities.Nation;
import se.yarin.cbhlib.util.ByteBufferUtil;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.timeline.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * Class responsible for parsing a {@link GameEvent} from the ChessBase media format.
 * <p>
 * Example of events:
 * </p>
 * <ul>
 *     <li>Replace existing game with a new one</li>
 *     <li>Add a move</li>
 *     <li>Add an annotation</li>
 *     <li>Go to a specific move</li>
 * </ul>
 */
public class ChessBaseMediaEventParser {

    private static final Logger log = LoggerFactory.getLogger(ChessBaseMediaEventParser.class);

    /**
     * Parses a ChessBase media event from a byte buffer
     * @param buf a buffer containing the data to parse
     * @return the parsed {@link GameEvent}; if the event code is unknown, it will be an {@link UnknownEvent}
     * @throws ChessBaseMediaException if unexpected or invalid data was found in a known event
     */
    public static GameEvent parseChessMediaEvent(ByteBuffer buf) throws ChessBaseMediaException {
        int commandType = ByteBufferUtil.getIntL(buf);

        // The following command types have been seen in the wild:
        //  2 = Complete game refresh (this event is sent as a heartbeat roughly every 6-10 seconds)
        //  3 = Change selected moved
        //  4 = Add a new move
        //  6 = Add annotations
        //  7 = Delete variation
        //  8 = Promote variation
        // 10 = Delete remaining moves
        // 11 = Delete all annotations
        // 12 = ? Some messaging? Occurs in Simon Williams - Amazing Moves, with text "Stream is protected"
        // 14 = ? Some sequential id marker occurring just before ReplaceAllEvent

        GameEvent event;
        switch (commandType) {
            case 2:
                event = new ReplaceAllEvent(parseFullUpdate(buf));
                break;
            case 3:
                event = new SetCursorEvent(ByteBufferUtil.getIntL(buf));
                break;
            case 4:
                event = parseAddMoveEvent(buf);
                break;
            case 6 :
                int unknown = ByteBufferUtil.getIntL(buf);
                if (unknown != 0 && unknown != 1) {
                    // Is usually 0, but was 1 in Andrew Martin - The ABC of the Benko Gambit (2nd Edition)/10 Accepted.wmv
                    // Version!?
                    throw new ChessBaseMediaException("Expected 0 or 1 at start of add annotation event");
                }
                short noAnnotations = ByteBufferUtil.getSignedShortL(buf);
                ArrayList<Annotation> annotations = new ArrayList<>(noAnnotations);
//                log.info("# annotations: " + noAnnotations);
                for (int i = 0; i < noAnnotations; i++) {
                    int annotationSize = ByteBufferUtil.getUnsignedShortL(buf);
                    int next = buf.position() + annotationSize;
                    int moveNo = ByteBufferUtil.getUnsigned24BitL(buf);
                    if (moveNo != 0) {
                        // In the ChessBase media format, annotations are always at move 0,
                        // probably since there is no need for it as there is a selected move.
                        throw new ChessBaseMediaException("Three first annotation bytes should be 0");
                    }
                    annotations.add(AnnotationsSerializer.deserializeAnnotation(buf));
                    buf.position(next);
                }
                event = new AddAnnotationsEvent(annotations);
                break;
            case 7:
                int zero = ByteBufferUtil.getIntL(buf);
                if (zero != 0)
                    throw new ChessBaseMediaException("Expected 0 as only data for command type " + commandType);
                // Occurs in Alexei Shirov - My Best Games in the Najdorf (only audio)/My best games in the Sicilian Najdorf.html/2.wmv
                event = new DeleteVariationEvent();
                break;
            case 8:
                zero = ByteBufferUtil.getIntL(buf);
                if (zero != 0)
                    throw new ChessBaseMediaException("Expected 0 as only data for command type " + commandType);
                // Occurs in Alexei Shirov - My Best Games in the Najdorf (only audio)/My best games in the Sicilian Najdorf.html/2.wmv
                event = new PromoteVariationEvent();
                break;
            case 10:
                zero = ByteBufferUtil.getIntL(buf);
                if (zero != 0)
                    throw new ChessBaseMediaException("Expected 0 as only data for command type " + commandType);
                // Occurs in Jacob Aagaard - Queen's Indian Defence/Queen's Indian Defence.avi/8.wmv
                event = new DeleteRemainingMovesEvent();
                break;
            case 11:
                int minus1 = ByteBufferUtil.getIntL(buf);
                if (minus1 != -1)
                    throw new ChessBaseMediaException("Expected -1 as only data for command type 11");
                // Occurs in Karsten Müller - Chess Endgames 3/68.wmv - probably a mouse slip...
                event = new DeleteAllAnnotationEvents();
                break;
            case 14:
                int id = ByteBufferUtil.getIntL(buf);
                // Occurs in CBM168/Festival Biel 2015.html/Biel 2015 round 04 Navara-Wojtaszek.wmv
                event = new MarkerEvent(id);
                break;
            default:
                log.warn(String.format("Unknown command type %d with data %s", commandType, CBUtil.toHexString(buf)));
                /*
                StringBuilder sb = new StringBuilder();
                while (buf.hasRemaining()) {
                    byte b = buf.get();
                    sb.append(b >= 32 && b < 127 ? (char) b : '?');
                }
                log.warn(sb.toString());
                */
                buf.position(buf.limit());
                event = new UnknownEvent(commandType);
                break;
        }

        if (buf.hasRemaining()) {
            String msg = String.format("More bytes left in command type %d than expected: %s",
                    commandType, CBUtil.toHexString(buf));
            throw new ChessBaseMediaException(msg);
        }

        if (log.isDebugEnabled()) {
            log.debug("Parsed event " + event.toString());
        }

        return event;
    }

    static GameEvent parseAddMoveEvent(ByteBuffer buf) throws ChessBaseMediaException {
        int actionFlags = ByteBufferUtil.getUnsignedShortL(buf);
        int actionFlags2 = ByteBufferUtil.getUnsignedShortL(buf);
        int fromSquare = ByteBufferUtil.getUnsignedByte(buf);
        int toSquare = ByteBufferUtil.getUnsignedByte(buf);
        int moveFlags = ByteBufferUtil.getUnsignedShortL(buf);

        // actionFlags:
        // 0000  Move becomes variation 0
        // 0001  Move becomes variation 1
        // 0002  Move becomes variation 2
        // 0003  Move becomes variation 3
        // 0040  Add move in current variation (only seems to occur if there are no other moves at this point)
        // 0080  Overwrite
        // 0200  New variation but same move as in the main variation (?)
        //       This occurs 1:54 in Viswanathan Anand - My Career - Volume 1/10.wmv
        // 0400  Insert in current variation (change move but keep remaining moves if possible)
        //       This occurs 17:20 in Jacob Aagaard - The Nimzoindian Defence - The easy way (only audio)/The Nimzoindian Defence - The easy way.html/6.wmv

        // 0800  New Main Line, the old main line becomes variation 0
        // 0801  New Main Line, the old main line becomes variation 1
        // 0802  New Main Line, the old main line becomes variation 2
        // 0803  New Main Line, the old main line becomes variation 3
        // Hmm the New Maine Line 0800 marker is ALWAYS followed by a "promotion variation" command.
        // So maybe it's not actually new main line but something else?!
        int lineNo = actionFlags & 15;
        boolean appendMove = (actionFlags & 0x40) == 0x40;
        boolean insertMove = (actionFlags & 0x400) == 0x400;
        boolean overwriteMove = (actionFlags & 0x80) == 0x80;
        boolean newMainline = (actionFlags & 0x800) == 0x800;

        // moveFlags:
        // 0000  default
        // 0001  white queen side castle
        // 0002  white king side castle
        // 0004  black queen side castle
        // 0008  black king side castle
        // 0010  en-passant
        // 0020  pawn moves two square (enables en-passant)
        // 0040  promote
        // 0080  is capture
        // 0240  promote to queen
        // 0340  promote to knight
        // 02c0  capture and promote to queen

        Piece promotionPiece;
        if ((moveFlags & 64) == 64) {
            switch (moveFlags & 0x0700) {
                case 0x0200 :
                    promotionPiece = Piece.QUEEN;
                    break;
                case 0x0300 :
                    promotionPiece = Piece.KNIGHT;
                    break;
                default:
                    throw new RuntimeException("Unknown promotion piece: " + moveFlags);
            }
        } else if ((moveFlags & 0xFF00) > 0) {
            throw new RuntimeException("Unknown move flags: " + moveFlags);
        } else {
            promotionPiece = Piece.NO_PIECE;
        }

        if (fromSquare < 0 || fromSquare >= 64 || toSquare < 0 || toSquare >= 64)
            throw new RuntimeException("Illegal move: " + fromSquare + " " + toSquare);

        ShortMove move;
        if (fromSquare == 0 && toSquare == 0) {
            move = ShortMove.nullMove();
        } else {
            Stone promotionStone = promotionPiece.toStone(Chess.sqiToRow(toSquare) == 7 ? Player.WHITE : Player.BLACK);
            if ((moveFlags & 0x0005) > 0) {
                move = ShortMove.longCastles();
            } else if ((moveFlags & 0x000A) > 0) {
                move = ShortMove.shortCastles();
            } else {
                move = new ShortMove(fromSquare, toSquare, promotionStone);
            }
        }

        if ((actionFlags & ~0x0FCF) != 0)
            throw new RuntimeException(String.format("Unknown AddMove action flags: %04x (%s)", actionFlags, move.toString()));
        if ((appendMove || overwriteMove || insertMove) && lineNo > 0) {
            // This combination doesn't make sense (maybe with appendMove?) and shouldn't exist. Probably.
            throw new RuntimeException(String.format("Unknown AddMove action flags: %04x (%s)", actionFlags, move.toString()));
        }
        if (actionFlags2 != 0) {
            throw new RuntimeException(String.format("Unknown AddMove action flags 2: %04x (%s)", actionFlags2, move.toString()));
        }


        if (overwriteMove) {
            return new OverwriteMoveEvent(move);
        } else if (insertMove) {
            return new InsertMoveEvent(move);
        } else {
            return new AddMoveEvent(move);
        }
    }

    static NavigableGameModel parseFullUpdate(ByteBuffer buf) throws ChessBaseMediaException {
        NavigableGameModel model = new NavigableGameModel();

        int zero = ByteBufferUtil.getIntL(buf);
        if (zero != 0) {
            throw new ChessBaseMediaException("Expected 0 as first int for command type 2");
        }

        int selectedMoveNo = ByteBufferUtil.getUnsignedShortL(buf);

        int p21 = ByteBufferUtil.getUnsignedShortL(buf), p22 = ByteBufferUtil.getUnsignedShortL(buf), p23 = ByteBufferUtil.getUnsignedShortL(buf);
        if (p21 != 0 || p22 != 0 || p23 != 0) {
            throw new ChessBaseMediaException(String.format("Unknown part 2: %02x %02x %02x", p21, p22, p23));
        }

        String whiteLast = ByteBufferUtil.getByteString(buf), whiteFirst = ByteBufferUtil.getByteString(buf);
        String blackLast = ByteBufferUtil.getByteString(buf), blackFirst = ByteBufferUtil.getByteString(buf);
        String whiteDelim = whiteLast.length() > 0 && whiteFirst.length() > 0 ? ", " : "";
        String blackDelim = blackLast.length() > 0 && blackFirst.length() > 0 ? ", " : "";
        model.header().setWhite(whiteLast + whiteDelim + whiteFirst);
        model.header().setBlack(blackLast + blackDelim + blackFirst);
        model.header().setEventSite(ByteBufferUtil.getByteString(buf));
        model.header().setEvent(ByteBufferUtil.getByteString(buf));
        model.header().setEventDate(CBUtil.decodeDate(ByteBufferUtil.getIntL(buf)));

        int eventTypeValue = ByteBufferUtil.getUnsignedByte(buf);

        model.header().setEventType(CBUtil.decodeTournamentType(eventTypeValue).getName());
        model.header().setEventTimeControl(CBUtil.decodeTournamentTimeControl(eventTypeValue).getName());

        int eventValue2 = ByteBufferUtil.getUnsignedByte(buf);
        model.header().setEventCountry(Nation.values()[ByteBufferUtil.getUnsignedShortL(buf)].getIocCode());
        model.header().setEventCategory(ByteBufferUtil.getUnsignedByte(buf));
        int eventValue = ByteBufferUtil.getUnsignedByte(buf); // Not sure what this is? Not always 0, can be 3
        // TODO: Huh, why is eventCategory set twice here!? Double check all these fields!!
        model.header().setEventCategory(ByteBufferUtil.getUnsignedShortL(buf));

        model.header().setSourceTitle(ByteBufferUtil.getByteString(buf));
        model.header().setSource(ByteBufferUtil.getByteString(buf));

        model.header().setSourceDate(CBUtil.decodeDate(ByteBufferUtil.getIntL(buf)));

        Date someOtherDate = CBUtil.decodeDate(ByteBufferUtil.getIntL(buf));

        byte b1 = ByteBufferUtil.getSignedByte(buf), b2 = ByteBufferUtil.getSignedByte(buf);
        // TODO: Figure out what b1 and b2 is used for. It's usually 00 00 but can also be
        // Crazy guess: number of times white/black repeats moves!?
        // 01 02 in Müller/19
        // 02 01 in Ari Ziegler - French Defence/25.wmv
        // 01 01 in ?
        // 00 01 in ?

        model.header().setAnnotator(ByteBufferUtil.getByteString(buf));

        model.header().setWhiteElo(ByteBufferUtil.getUnsignedShortL(buf));
        model.header().setBlackElo(ByteBufferUtil.getUnsignedShortL(buf));

        model.header().setEco(CBUtil.decodeEco(ByteBufferUtil.getUnsignedShortL(buf)));
        model.header().setResult(GameResult.values()[ByteBufferUtil.getUnsignedByte(buf)]);

        int unknownShort = ByteBufferUtil.getUnsignedShortL(buf); // 0 or 13!?
        model.header().setDate(CBUtil.decodeDate(ByteBufferUtil.getIntL(buf)));
        int lastMoveNumber = ByteBufferUtil.getUnsignedShortL(buf);
        model.header().setRound(ByteBufferUtil.getUnsignedByte(buf));
        model.header().setSubRound(ByteBufferUtil.getUnsignedByte(buf));

        int unknown1 = ByteBufferUtil.getIntL(buf);
        int unknown2 = ByteBufferUtil.getIntL(buf);

        if (unknown1 != 0) {
            throw new ChessBaseMediaException("Unknown int 1: " + unknown1);
        }
        if (unknown2 != 0) {
            throw new ChessBaseMediaException("Unknown int 2: " + unknown2);
        }

        // This is also some extra stuff occurring in a few files. Not sure what it is?
        // Nigel Davies - The Tarrasch Defence/Tarrasch.html/04_Monacell_Nadanyan new.wmv
        // Rustam Kasimdzhanov - Endgames for Experts/Endgame.html/endgame adams-kasim.wmv
        // Rustam Kasimdzhanov - Endgames for Experts/Endgame.html/endgame kasim-ghaem.wmv
        // Rustam Kasimdzhanov - Endgames for Experts/Endgame.html/endgame kasim-shirov.wmv
        // Maurice Ashley - The Secret to Chess/17Secret.wmv
        int headerSkipBytes = ByteBufferUtil.getIntL(buf);
        if (headerSkipBytes > 0) {
            log.debug("Skipping " + headerSkipBytes + " bytes in header");
        }
        buf.position(buf.position() + headerSkipBytes);

        boolean setupPosition = ByteBufferUtil.getUnsignedByte(buf) == 0;

        if (setupPosition) {
            Stone[] stones = new Stone[64];
            for (int i = 0; i < 64; i++) {
                Player player = Player.WHITE;
                int pieceCode = ByteBufferUtil.getUnsignedByte(buf);
                if ((pieceCode & 8) == 8) {
                    player = Player.BLACK;
                    pieceCode -= 8;
                }
                Piece p;
                switch (pieceCode) {
                    case 0: p = Piece.NO_PIECE; break;
                    case 1: p = Piece.KING; break;
                    case 2: p = Piece.QUEEN; break;
                    case 3: p = Piece.KNIGHT; break;
                    case 4: p = Piece.BISHOP; break;
                    case 5: p = Piece.ROOK; break;
                    case 6: p = Piece.PAWN; break;
                    default :
                        throw new ChessBaseMediaException(
                                String.format("Unknown piece in setup position at %s: %d",
                                        Chess.sqiToStr(i), pieceCode));
                }
                stones[i] = p.toStone(player);
            }

            int b = ByteBufferUtil.getUnsignedByte(buf);
            if (b != 0 && b != 1)
                throw new ChessBaseMediaException("Unknown side to move: " + b);
            Player toMove = b == 0 ? Player.WHITE : Player.BLACK;

            byte[] bytes = new byte[7];
            buf.get(bytes);
            if (bytes[0] != 0 || bytes[1] != 0 || bytes[2] != 0 || bytes[4] != 0 || bytes[5] != 1 || bytes[6] != 0) {
                throw new ChessBaseMediaException("Unknown setup values: " + Arrays.toString(bytes));
            }
            if (bytes[3] != 0 && bytes[3] != 8) {
                // bytes[3] == 8 occurs in Karsten Müller - Chess Endgames 3/22.wmv" start position
                throw new ChessBaseMediaException("Unknown setup values: " + Arrays.toString(bytes));
            }
            int moveNo = ByteBufferUtil.getUnsignedShortL(buf);
            b = ByteBufferUtil.getUnsignedByte(buf);
            if (b != 0) {
                throw new ChessBaseMediaException("Unknown last value in setup position: " + b);
            }

            // TODO: Figure out castling rights and en passant file and Chess960 start position
            Position position = new Position(stones, toMove, EnumSet.noneOf(Castles.class), -1, Chess960.REGULAR_CHESS_SP);

            int ply = Chess.moveNumberToPly(moveNo, toMove);
            model.moves().setupPosition(position, ply);
        }

        // This seems to be some extra game headers in the media format
        // TODO: Figure out what to do with these extra headers
        // Could one of them be teams?
        List<String> headers = new ArrayList<>();
        int noHeaders = ByteBufferUtil.getUnsignedShortL(buf); // number of extra headers to follow??
        if (noHeaders != 0) {
            log.debug(String.format("# extra headers: %d", noHeaders));
        }
        for (int i = 0; i < noHeaders; i++) {
            int size = ByteBufferUtil.getUnsignedShortL(buf);
            byte[] header = new byte[size];
            buf.get(header);
            headers.add(Arrays.toString(header));
        }

        parseMoves(buf, model.moves().root());

        int last = ByteBufferUtil.getIntL(buf);
        if (last != 0 || buf.position() != buf.limit()) {
            buf.position(buf.position() - 4);
            byte[] bytes = new byte[buf.limit() - buf.position()];
            buf.get(bytes);
            log.warn("Unexpected trailing bytes: " + Arrays.toString(bytes));
        }

        List<GameMovesModel.Node> allNodes = model.moves().getAllNodes();
        if (selectedMoveNo < allNodes.size()) {
            model.setCursor(allNodes.get(selectedMoveNo));
        } else {
            throw new ChessBaseMediaException("Invalid selected move " + selectedMoveNo);
        }


        /*
        if (selectedMove == game) {
            log.info("Selected move: <start>");
        } else {
            String prefix = selectedMove.getBackPosition().getMoveNumber() + (selectedMove.getPlayerToMove() == Piece.PieceColor.WHITE ? "..." : ".");
            String move = selectedMove.getLastMove().toString(selectedMove.getBackPosition().getPosition());
            log.info("Selected move: " + prefix + move);
        }
        */
        /*

        log.info("part 2: " + part2); // Only zeros found so far!?
        log.info("part 4: " + part4); // Is either 00 00  or 01 01 ??
        log.info("part 5: " + part5); // Only zeros found so far!?
        for (String header : headers) {
            log.info("extra header: " + header);
        }

        log.info("Event: " + event);
        log.info("Site: " + site);
        log.info("Date: " + date);
        log.info("Round: " + round);
        log.info("White: " + whitePlayerLastName + ", " + whitePlayerFirstName);
        log.info("Black: " + blackPlayerLastName + ", " + blackPlayerFirstName);
        log.info("Result: " + result);
        log.info("ECO: " + eco);
        log.info("WhiteElo: " + whiteRating);
        log.info("BlackElo: " + blackRating);
        log.info("Annotator: " + annotator);
        // PlyCount (probably calculated from moves?)
        log.info("Last move number: " + lastMoveNumber);

        log.info("EventDate: " + eventDate);
        log.info("EventType: " + eventType);
        log.info("EventTimeControls: " + timeControl.toString());
        log.info("EventRounds: " + eventRounds);
        log.info("EventCountry: #" + eventCountryCode);
        log.info("EventCategory: " + eventCategory);
        log.info("Source title: " + sourceTitle);
        log.info("Source: " + source);
        log.info("SourceDate: " + sourceDate);

        log.info("SomeOtherEventInfo: " + eventValue);
        log.info("SomeOtherEventInfo2: " + eventValue2);
        log.info("SomeOtherDate: " + someOtherDate);
        log.info("Unknown short: " + unknownShort);

        // TimeControl
        log.info("Setup: " + setupPosition);
        */

        return model;
    }

    static void parseMoves(ByteBuffer buf, GameMovesModel.Node node)
            throws ChessBaseMediaException {
        int noMoves = ByteBufferUtil.getUnsignedShortL(buf);
        int zero = ByteBufferUtil.getUnsignedShortL(buf);

        if (zero != 0) {
            throw new ChessBaseMediaException(String.format("Unknown variation data: %02x %02x", noMoves, zero));
        }

        for (int i = 0; i < noMoves; i++) {
            int fromSquare = ByteBufferUtil.getUnsignedByte(buf);
            int toSquare = ByteBufferUtil.getUnsignedByte(buf);
            int noAnnotations = ByteBufferUtil.getUnsignedByte(buf), b2 = ByteBufferUtil.getUnsignedByte(buf);

            Piece promotionPiece = Piece.NO_PIECE;
            if ((fromSquare & 64) == 64) {
                fromSquare -= 64;
                switch (toSquare / 64) {
                    case 0:  promotionPiece = Piece.QUEEN; break;
                    case 1:  promotionPiece = Piece.KNIGHT; break;
                    // Presumably 2 = bishop...
                    case 3:  promotionPiece = Piece.ROOK; break;
                    default:
                        throw new ChessBaseMediaException("Unknown promotion piece: " + toSquare / 64);
                }
                toSquare %= 64;
            }

            if (fromSquare < 0 || fromSquare >= 64 || toSquare < 0 || toSquare >= 64 || b2 != 0) {
                throw new ChessBaseMediaException(String.format("Unknown move data: %d %d %d", fromSquare, toSquare, b2));
            }

            Move move;
            try {
                if (fromSquare == 0 && toSquare == 0) {
                    move = Move.nullMove(node.position());
                } else {
                    // In one game there is a move Ng8-f6=Q!?
                    // Alexei Shirov - My Best Games in the King's Indian/My best games in the King's Indian.html/Gelfand-Shirov.wmv
                    if (node.position().stoneAt(fromSquare).toPiece() != Piece.PAWN) {
                        promotionPiece = Piece.NO_PIECE;
                    }
                    move = new Move(node.position(), fromSquare, toSquare, promotionPiece.toStone(node.position().playerToMove()));
                }
            } catch (IllegalArgumentException e) {
                log.error("Illegal move parsing: %d %d %d", fromSquare, toSquare, b2);
                throw e;
            }

            if (log.isDebugEnabled()) {
                log.debug("Parsed move {}", move.toSAN(node.ply()));
            }
            GameMovesModel.Node newNode = node.addMove(move);

            for (int j = 0; j < noAnnotations; j++) {
                int annotationLength = ByteBufferUtil.getUnsignedShortL(buf);
                int nextPosition = buf.position() + annotationLength;

                int moveNo = ByteBufferUtil.getSigned24BitB(buf);
                if (moveNo != 0) {
                    // Since annotations are embedded, this should always be 0
                    throw new ChessBaseMediaException("Expected move no to be 0");
                }

                Annotation annotation = AnnotationsSerializer.deserializeAnnotation(buf);
                if (log.isDebugEnabled()) {
                    log.debug("Parsed annotation: {}", annotation.toString());
                }
                newNode.addAnnotation(annotation);

                buf.position(nextPosition);
            }
            int noVariations = ByteBufferUtil.getUnsignedByte(buf);

            for (int j = 0; j < noVariations; j++) {
                parseMoves(buf, node);
            }
            node = newNode;
        }
    }
}
