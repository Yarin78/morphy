package se.yarin.cbhlib.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.AnnotationParser;
import se.yarin.cbhlib.CBUtil;
import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.timeline.*;
import yarin.cbhlib.GameResult;
import yarin.cbhlib.TournamentTimeControls;
import yarin.cbhlib.TournamentType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * Parser of ASF text commands into {@link se.yarin.chess.timeline.GameEvent}s
 */
public class ASFTextCommandParser {

    private static final Logger log = LoggerFactory.getLogger(ASFTextCommandParser.class);

    /**
     * Parses a TEXT command in the ASF script command stream
     * @param command the data of the TEXT command encoded as a hexstring
     * @return the parsed {@link GameEvent}; if the event code is unknown, it will be an @{@link UnknownEvent}
     * @throws ChessBaseMediaException if unexpected or invalid data was found in a known event
     */
    public static GameEvent parseTextCommand(String command) throws ChessBaseMediaException {
        if (command.length() % 2 != 0) {
            throw new ChessBaseMediaException("Invalid format of ChessBase script command");
        }

        ByteBuffer buf = ByteBuffer.allocateDirect(command.length() / 2);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < command.length(); i+=2) {
            try {
                buf.put((byte) (Integer.parseInt("" + command.charAt(i) + command.charAt(i + 1), 16)));
            } catch (NumberFormatException e) {
                throw new ChessBaseMediaException("Invalid format of ChessBase script command", e);
            }
        }
        buf.rewind();

        for (int i = 0; i < 3; i++) {
            byte b = CBUtil.getSignedByte(buf);
            if (b != 0) throw new ChessBaseMediaException("Unexpected header byte: " + i + " " + b);
        }
        int len = CBUtil.getShortBCD(buf);
        int bytesLeft = buf.limit() - buf.position();
        if (bytesLeft != len) {
            log.warn("Number of bytes left in buffer (" + bytesLeft + ") didn't match specified length (" + len + ")");
        }

        int commandType = CBUtil.getIntL(buf);
        GameEvent action;

        // The following command types seems to exist
        // 2 = complete game refresh (sent every 10 seconds)
        // 3 = change selected moved (< 0x60 = ply in main line, otherwise in a variation!?)
        // 4 = insert new move
        // 6 = add annotations
        // 7 = ?
        // 8 = noop!?
        // 10 = ?
        // 11 = ?
        // 12 = ? Some messaging? Occurs in Simon Williams - Amazing Moves, with text "Stream is protected"
        // 14 = ? Some sequential id marker occurring just before ReplaceAllEvent

        switch (commandType) {
            case 2:
                action = new ReplaceAllEvent(parseFullUpdate(buf));
                break;
            case 3:
                action = new SetCursorEvent(CBUtil.getIntL(buf));
                break;
            case 4:
                action = parseAddMoveEvent(buf);
                break;
            case 6 :
                int unknown = CBUtil.getIntL(buf);
                if (unknown != 0 && unknown != 1) {
                    // Is usually 0, but was 1 in Andrew Martin - The ABC of the Benko Gambit (2nd Edition)/10 Accepted.wmv
                    // Version!?
                    throw new ChessBaseMediaException("Expected 0 or 1 at start of add annotation action");
                }
                short noAnnotations = CBUtil.getSignedShortL(buf);
                ArrayList<Annotation> annotations = new ArrayList<>(noAnnotations);
//                log.info("# annotations: " + noAnnotations);
                for (int i = 0; i < noAnnotations; i++) {
                    int annotationSize = CBUtil.getUnsignedShortL(buf);
                    int next = buf.position() + annotationSize;
                    int moveNo = CBUtil.getUnsigned24BitL(buf);
                    if (moveNo != 0) {
                        // In the ChessBase media format, annotations are always at move 0,
                        // probably since there is no need for it as there is a selected move.
                        throw new ChessBaseMediaException("Three first annotation bytes should be 0");
                    }
                    annotations.add(AnnotationParser.getAnnotation(buf));
                    buf.position(next);
                }
                action = new AddAnnotationsEvent(annotations);
                break;
            case 7:
                int zero = CBUtil.getIntL(buf);
                if (zero != 0)
                    throw new ChessBaseMediaException("Expected 0 as only data for command type " + commandType);
                // Occurs in Alexei Shirov - My Best Games in the Najdorf (only audio)/My best games in the Sicilian Najdorf.html/2.wmv
                action = new DeleteVariationEvent();
                break;
            case 8:
                zero = CBUtil.getIntL(buf);
                if (zero != 0)
                    throw new ChessBaseMediaException("Expected 0 as only data for command type " + commandType);
                // Occurs in Alexei Shirov - My Best Games in the Najdorf (only audio)/My best games in the Sicilian Najdorf.html/2.wmv
                action = new PromoteVariationEvent();
                break;
            case 10:
                zero = CBUtil.getIntL(buf);
                if (zero != 0)
                    throw new ChessBaseMediaException("Expected 0 as only data for command type " + commandType);
                // Occurs in Jacob Aagaard - Queen's Indian Defence/Queen's Indian Defence.avi/8.wmv
                action = new DeleteRemainingMovesEvent();
                break;
            case 11:
                int minus1 = CBUtil.getIntL(buf);
                if (minus1 != -1)
                    throw new ChessBaseMediaException("Expected -1 as only data for command type 11");
                // Occurs in Karsten Müller - Chess Endgames 3/68.wmv - probably a mouse slip...
                action = new DeleteAllAnnotationEvents();
                break;
            case 14:
                int id = CBUtil.getIntL(buf);
                // Occurs in CBM168/Festival Biel 2015.html/Biel 2015 round 04 Navara-Wojtaszek.wmv
                action = new MarkerEvent(id);
                break;
            default:
                byte[] unknownBytes = new byte[buf.limit() - buf.position()];
                buf.get(unknownBytes);
//                StringBuilder sb = new StringBuilder();
//                for (int i = 0; i < unknownBytes.length; i++) {
//                    sb.append(unknownBytes[i] >= 32 && unknownBytes[i] < 127 ? (char) unknownBytes[i] : '?');
//                }
                log.warn(String.format("Unknown command type %d with data %s",
                        commandType, Arrays.toString(unknownBytes)));
//                log.warn(sb.toString());

                action = new UnknownEvent(commandType);
                break;
        }

        if (buf.hasRemaining()) {
            byte[] unknownBytes = new byte[buf.limit() - buf.position()];
            buf.get(unknownBytes);
            throw new ChessBaseMediaException(String.format("More bytes left in command type %d than expected: %s",
                    commandType, Arrays.toString(unknownBytes)));
        }

        if (log.isDebugEnabled()) {
            log.debug("Parsed event " + action.toString());
        }

        return action;
    }

    public static GameEvent parseAddMoveEvent(ByteBuffer buf) throws ChessBaseMediaException {
        int actionFlags = CBUtil.getUnsignedShortL(buf);
        int actionFlags2 = CBUtil.getUnsignedShortL(buf);
        int fromSquare = CBUtil.getUnsignedByte(buf);
        int toSquare = CBUtil.getUnsignedByte(buf);
        int moveFlags = CBUtil.getUnsignedShortL(buf);

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
            move = new ShortMove(fromSquare, toSquare, promotionStone);
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

    public static NavigableGameModel parseFullUpdate(ByteBuffer buf) throws ChessBaseMediaException {
        NavigableGameModel model = new NavigableGameModel();

        int zero = CBUtil.getIntL(buf);
        if (zero != 0) {
            throw new ChessBaseMediaException("Expected 0 as first int for command type 2");
        }

        int selectedMoveNo = CBUtil.getUnsignedShortL(buf);

        int p21 = CBUtil.getUnsignedShortL(buf), p22 = CBUtil.getUnsignedShortL(buf), p23 = CBUtil.getUnsignedShortL(buf);
        if (p21 != 0 || p22 != 0 || p23 != 0) {
            throw new ChessBaseMediaException(String.format("Unknown part 2: %02x %02x %02x", p21, p22, p23));
        }

        String whiteLast = CBUtil.getByteString(buf), whiteFirst = CBUtil.getByteString(buf);
        String blackLast = CBUtil.getByteString(buf), blackFirst = CBUtil.getByteString(buf);
        String whiteDelim = whiteLast.length() > 0 && whiteFirst.length() > 0 ? ", " : "";
        String blackDelim = blackLast.length() > 0 && blackFirst.length() > 0 ? ", " : "";
        model.header().setField("white",  whiteLast + whiteDelim + whiteFirst);
        model.header().setField("black",  blackLast + blackDelim + blackFirst);
        model.header().setField("eventSite", CBUtil.getByteString(buf));
        model.header().setField("eventName", CBUtil.getByteString(buf));
        model.header().setField("eventDate", CBUtil.decodeDate(CBUtil.getIntL(buf)));

        int eventTypeValue = CBUtil.getUnsignedByte(buf);
        if ((eventTypeValue & 31) < 0 || (eventTypeValue & 31) >= TournamentType.values().length)
            throw new ChessBaseMediaException("Invalid event type value " + eventTypeValue);
        TournamentType eventType = TournamentType.values()[eventTypeValue & 31];
        model.header().setField("eventType", eventType.toString());

        TournamentTimeControls timeControl = new TournamentTimeControls();
        if ((eventTypeValue & 0x20) > 0)
            timeControl.add(TournamentTimeControls.TournamentTimeControl.Blitz);
        if ((eventTypeValue & 0x40) > 0)
            timeControl.add(TournamentTimeControls.TournamentTimeControl.Rapid);
        if ((eventTypeValue & 0x80) > 0)
            timeControl.add(TournamentTimeControls.TournamentTimeControl.Corresp);
        model.header().setField("timeControl", timeControl.toString());

        int eventValue2 = CBUtil.getUnsignedByte(buf);
        model.header().setField("eventCountry", "#" + CBUtil.getUnsignedShortL(buf)); // TODO: Resolve country name
        model.header().setField("eventCategory", Integer.toString(CBUtil.getUnsignedByte(buf)));
        int eventValue = CBUtil.getUnsignedByte(buf); // Not sure what this is? Not always 0, can be 3
        // TODO: Huh, why is eventCategory set twice here!?
        model.header().setField("eventCategory", Integer.toString(CBUtil.getUnsignedShortL(buf)));

        model.header().setField("sourceTitle", CBUtil.getByteString(buf));
        model.header().setField("source", CBUtil.getByteString(buf));

        model.header().setField("sourceDate", CBUtil.decodeDate(CBUtil.getIntL(buf)));

        Date someOtherDate = CBUtil.decodeDate(CBUtil.getIntL(buf));

        byte b1 = CBUtil.getSignedByte(buf), b2 = CBUtil.getSignedByte(buf);
        // TODO: Figure out what b1 and b2 is used for. It's usually 00 00 but can also be
        // Crazy guess: number of times white/black repeats moves!?
        // 01 02 in Müller/19
        // 02 01 in Ari Ziegler - French Defence/25.wmv
        // 01 01 in ?
        // 00 01 in ?

        model.header().setField("annotator", CBUtil.getByteString(buf));

        model.header().setField("whiteElo", CBUtil.getUnsignedShortL(buf));
        model.header().setField("blackElo", CBUtil.getUnsignedShortL(buf));

        model.header().setField("eco", CBUtil.decodeEco(CBUtil.getUnsignedShortL(buf)));

        GameResult result = GameResult.values()[CBUtil.getUnsignedByte(buf)];
        switch (result) {
            case BlackWon:
            case BlackWonOnForfeit:
                model.header().setField("result", se.yarin.chess.GameResult.BLACK_WINS);
                break;
            case Draw:
                model.header().setField("result", se.yarin.chess.GameResult.DRAW);
                break;
            case WhiteWon:
            case WhiteWonOnForfeit:
                model.header().setField("result", se.yarin.chess.GameResult.WHITE_WINS);
                break;
            default:
        }

        int unknownShort = CBUtil.getUnsignedShortL(buf); // 0 or 13!?
        model.header().setField("date", CBUtil.decodeDate(CBUtil.getIntL(buf)));
        int lastMoveNumber = CBUtil.getUnsignedShortL(buf);
        model.header().setField("round", CBUtil.getUnsignedByte(buf));
        model.header().setField("subRound", CBUtil.getUnsignedByte(buf));

        int unknown1 = CBUtil.getIntL(buf);
        int unknown2 = CBUtil.getIntL(buf);

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
        int headerSkipBytes = CBUtil.getIntL(buf);
        if (headerSkipBytes > 0) {
            log.debug("Skipping " + headerSkipBytes + " bytes in header");
        }
        buf.position(buf.position() + headerSkipBytes);

        boolean setupPosition = CBUtil.getUnsignedByte(buf) == 0;

        if (setupPosition) {
            Stone[] stones = new Stone[64];
            for (int i = 0; i < 64; i++) {
                Player player = Player.WHITE;
                int pieceCode = CBUtil.getUnsignedByte(buf);
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

            int b = CBUtil.getUnsignedByte(buf);
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
            int moveNo = CBUtil.getUnsignedShortL(buf);
            b = CBUtil.getUnsignedByte(buf);
            if (b != 0) {
                throw new ChessBaseMediaException("Unknown last value in setup position: " + b);
            }

            // TODO: Figure out castling rights and en passant file
            Position position = new Position(stones, toMove, EnumSet.noneOf(Castles.class), -1);

            int ply = Chess.moveNumberToPly(moveNo, toMove);
            model.moves().setupPosition(position, ply);
        }

        // This seems to be some extra game headers in the media format
        // TODO: Figure out what to do with these extra headers
        // Could one of them be teams?
        List<String> headers = new ArrayList<>();
        int noHeaders = CBUtil.getUnsignedShortL(buf); // number of extra headers to follow??
        if (noHeaders != 0) {
            log.debug(String.format("# extra headers: %d", noHeaders));
        }
        for (int i = 0; i < noHeaders; i++) {
            int size = CBUtil.getUnsignedShortL(buf);
            byte[] header = new byte[size];
            buf.get(header);
            headers.add(Arrays.toString(header));
        }

        parseMoves(buf, model.moves().root());

        int last = CBUtil.getIntL(buf);
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

    private static void parseMoves(ByteBuffer buf, GameMovesModel.Node node)
            throws ChessBaseMediaException {
        int noMoves = CBUtil.getUnsignedShortL(buf);
        int zero = CBUtil.getUnsignedShortL(buf);

        if (zero != 0) {
            throw new ChessBaseMediaException(String.format("Unknown variation data: %02x %02x", noMoves, zero));
        }

        for (int i = 0; i < noMoves; i++) {
            int fromSquare = CBUtil.getUnsignedByte(buf);
            int toSquare = CBUtil.getUnsignedByte(buf);
            int noAnnotations = CBUtil.getUnsignedByte(buf), b2 = CBUtil.getUnsignedByte(buf);

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
                int annotationLength = CBUtil.getUnsignedShortL(buf);
                int nextPosition = buf.position() + annotationLength;

                int moveNo = CBUtil.getSigned24BitB(buf);
                if (moveNo != 0) {
                    // Since annotations are embedded, this should always be 0
                    throw new ChessBaseMediaException("Expected move no to be 0");
                }

                Annotation annotation = AnnotationParser.getAnnotation(buf);
                if (log.isDebugEnabled()) {
                    log.debug("Parsed annotation: {}", annotation.toString());
                }
                newNode.addAnnotation(annotation);

                buf.position(nextPosition);
            }
            int noVariations = CBUtil.getUnsignedByte(buf);

            for (int j = 0; j < noVariations; j++) {
                parseMoves(buf, node);
            }
            node = newNode;
        }
    }


}
