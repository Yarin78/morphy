package yarin.cbhlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yarin.cbhlib.actions.*;
import yarin.cbhlib.annotations.Annotation;
import yarin.cbhlib.exceptions.CBMException;
import yarin.chess.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChessBaseMediaParser {
    private static final Logger log = LoggerFactory.getLogger(ChessBaseMediaParser.class);

    private int readBCDEncodedShort(ByteBuffer buf) {
        int b1 = buf.get(), b2 = buf.get();
        if (b1<0) b1 += 256;
        if (b2<0) b2 += 256;
        return (b1/16)*1000+(b1%16)*100+(b2/16)*10+b2%16;
    }

    /**
     * Parses a TEXT command in the ASF script command stream
     * @param command the data of the TEXT command encoded as a hexstring
     * @return an action object. If the action is unknown, an @{@link UnknownAction} is returned
     * @throws CBMException if unexpected or invalid data was found in a known action
     */
    public RecordedAction parseTextCommand(String command) throws CBMException {
        if (command.length() % 2 != 0) {
            throw new CBMException("Invalid format of ChessBase script command");
        }

        ByteBuffer buf = ByteBuffer.allocateDirect(command.length() / 2);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < command.length(); i+=2) {
            try {
                buf.put((byte) (Integer.parseInt("" + command.charAt(i) + command.charAt(i + 1), 16)));
            } catch (NumberFormatException e) {
                throw new CBMException("Invalid format of ChessBase script command", e);
            }
        }
        buf.rewind();

        for (int i = 0; i < 3; i++) {
            byte b = buf.get();
            if (b != 0) throw new CBMException("Unexpected header byte: " + i + " " + b);
        }
        int len = readBCDEncodedShort(buf);
        int bytesLeft = buf.limit() - buf.position();
        if (bytesLeft != len) {
            log.warn("Number of bytes left in buffer (" + bytesLeft + ") didn't match specified length (" + len + ")");
        }

        int commandType = buf.getInt();
        RecordedAction action;

        // The following command types seems to exist
        // 2 = complete game refresh (sent every 10 seconds)
        // 3 = change selected moved (< 0x60 = ply in main line, otherwise in a variation!?)
        // 4 = insert new move
        // 6 = add annotations
        // 7 = ?
        // 8 = noop!?
        // 10 = ?
        // 11 = ?

        switch (commandType) {
            case 2:
                action = new FullUpdateAction(parseFullUpdate(buf));
                break;
            case 3:
                action = new SelectMoveAction(buf.getInt());
                break;
            case 4:
                int actionFlags = ByteBufferUtil.getUnsignedShort(buf);
                int actionFlags2 = ByteBufferUtil.getUnsignedShort(buf);
                int fromSquare = buf.get();
                int toSquare = buf.get();
                int moveFlags = ByteBufferUtil.getUnsignedShort(buf);
                action = new AddMoveAction(actionFlags, actionFlags2, fromSquare, toSquare, moveFlags);
                break;
            case 6 :
                int unknown = buf.getInt();
                if (unknown != 0 && unknown != 1) {
                    // Is usually 0, but was 1 in Andrew Martin - The ABC of the Benko Gambit (2nd Edition)/10 Accepted.wmv
                    // Version!?
                    throw new CBMException("Expected 0 or 1 at start of add annotation action");
                }
                short noAnnotations = buf.getShort();
                ArrayList<Annotation> annotations = new ArrayList<>(noAnnotations);
//                log.info("# annotations: " + noAnnotations);
                for (int i = 0; i < noAnnotations; i++) {
                    int annotationSize = buf.getShort();
                    int next = buf.position() + annotationSize;
                    int moveNo = ByteBufferUtil.getLittleEndian24BitValue(buf);
                    if (moveNo != 0) {
                        // In the ChessBase media format, annotations are always at move 0,
                        // probably since there is no need for it as there is a selected move.
                        throw new CBMException("Three first annotation bytes should be 0");
                    }
                    // TODO: Don't depend on ByteBuffer order!!
                    ByteOrder oldOrder = buf.order();
                    buf.order(ByteOrder.BIG_ENDIAN);
                    annotations.add(Annotation.getFromData(buf));
                    buf.order(oldOrder);
                    buf.position(next);
                }
                action = new AddAnnotationAction(annotations);
                break;
            case 7:
                int zero = buf.getInt();
                if (zero != 0)
                    throw new CBMException("Expected 0 as only data for command type " + commandType);
                // Occurs in Alexei Shirov - My Best Games in the Najdorf (only audio)/My best games in the Sicilian Najdorf.html/2.wmv
                action = new DeleteVariationAction();
                break;
            case 8:
                zero = buf.getInt();
                if (zero != 0)
                    throw new CBMException("Expected 0 as only data for command type " + commandType);
                // Occurs in Alexei Shirov - My Best Games in the Najdorf (only audio)/My best games in the Sicilian Najdorf.html/2.wmv
                action = new PromoteVariationAction();
                break;
            case 10:
                zero = buf.getInt();
                if (zero != 0)
                    throw new CBMException("Expected 0 as only data for command type " + commandType);
                // Occurs in Jacob Aagaard - Queen's Indian Defence/Queen's Indian Defence.avi/8.wmv
                action = new DeleteRemainingMovesAction();
                break;
            case 11:
                int minus1 = buf.getInt();
                if (minus1 != -1)
                    throw new CBMException("Expected -1 as only data for command type 11");
                // Occurs in Karsten Müller - Chess Endgames 3/68.wmv - probably a mouse slip...
                action = new DeleteAllCommentaryAction();
                break;
            default:
                byte[] unknownBytes = new byte[buf.limit() - buf.position()];
                buf.get(unknownBytes);
                log.warn(String.format("Unknown command type %d with data %s",
                        commandType, Arrays.toString(unknownBytes)));
                action = new UnknownAction(unknownBytes);
                break;
        }

        if (buf.hasRemaining()) {
            byte[] unknownBytes = new byte[buf.limit() - buf.position()];
            buf.get(unknownBytes);
            throw new CBMException(String.format("More bytes left in command type %d than expected: %s",
                    commandType, Arrays.toString(unknownBytes)));
        }

        return action;
    }

    public GameModel parseFullUpdate(ByteBuffer buf) throws CBMException {
        GameMetaData metadata = new GameMetaData();

        int zero = buf.getInt();
        if (zero != 0) {
            throw new CBMException("Expected 0 as first int for command type 2");
        }

        short selectedMoveNo = buf.getShort();

        short p21 = buf.getShort(), p22 = buf.getShort(), p23 = buf.getShort();
        if (p21 != 0 || p22 != 0 || p23 != 0) {
            throw new CBMException(String.format("Unknown part 2: %02x %02x %02x", p21, p22, p23));
        }

        metadata.setWhiteLastName(readString(buf));
        metadata.setWhiteFirstName(readString(buf));
        metadata.setBlackLastName(readString(buf));
        metadata.setBlackFirstName(readString(buf));

        metadata.setEventSite(readString(buf));
        metadata.setEventName(readString(buf));

        metadata.addExtra("eventDate", new Date(buf.getInt()).toString());
        int eventTypeValue = buf.get();
        if ((eventTypeValue & 31) < 0 || (eventTypeValue & 31) >= TournamentType.values().length)
            throw new CBMException("Invalid event type value " + eventTypeValue);
        TournamentType eventType = TournamentType.values()[eventTypeValue & 31];
        metadata.addExtra("eventType", eventType.toString());

        TournamentTimeControls timeControl = new TournamentTimeControls();
        if ((eventTypeValue & 0x20) > 0)
            timeControl.add(TournamentTimeControls.TournamentTimeControl.Blitz);
        if ((eventTypeValue & 0x40) > 0)
            timeControl.add(TournamentTimeControls.TournamentTimeControl.Rapid);
        if ((eventTypeValue & 0x80) > 0)
            timeControl.add(TournamentTimeControls.TournamentTimeControl.Corresp);
        metadata.addExtra("timeControl", timeControl.toString());

        int eventValue2 = buf.get();
        metadata.setEventCountry("#" + buf.getShort()); // TODO: Resolve country name
        metadata.addExtra("eventCategory", Byte.toString(buf.get()));
        int eventValue = buf.get(); // Not sure what this is? Not always 0, can be 3
        metadata.addExtra("eventCategory", Short.toString(buf.getShort()));

        metadata.addExtra("sourceTitle", readString(buf));
        metadata.setSource(readString(buf));

        metadata.addExtra("sourceDate", new Date(buf.getInt()).toString());

        Date someOtherDate = new Date(buf.getInt());

        byte b1 = buf.get(), b2 = buf.get();
        // TODO: Figure out what b1 and b2 is used for. It's usually 00 00 but can also be
        // 01 02 in Müller/19
        // 02 01 in Ari Ziegler - French Defence/25.wmv
        // 01 01 in ?
        // 00 01 in ?

        metadata.setAnnotator(readString(buf));

        metadata.setWhiteElo(buf.getShort());
        metadata.setBlackElo(buf.getShort());

        Eco eco = Eco.parse(ByteBufferUtil.getUnsignedShort(buf));
        metadata.setEco(eco.toString());
        GameResult result = GameResult.values()[buf.get()];
        switch (result) {
            case BlackWon:
            case BlackWonOnForfeit:
                metadata.setResult("0-1");
                break;
            case Draw:
                metadata.setResult("½-½");
                break;
            case WhiteWon:
            case WhiteWonOnForfeit:
                metadata.setResult("1-0");
                break;
            default:
                metadata.setResult("");
        }

        int unknownShort = buf.getShort(); // 0 or 13!?
        metadata.setPlayedDate(new Date(buf.getInt()).toString());
        int lastMoveNumber = buf.getShort();
        metadata.setRound(buf.get());
        metadata.setSubRound(buf.get());

        int unknown1 = buf.getInt();
        int unknown2 = buf.getInt();
        int headerSkipBytes = buf.getInt();

        if (unknown1 != 0) {
            throw new CBMException("Unknown int 1: " + unknown1);
        }
        if (unknown2 != 0) {
            throw new CBMException("Unknown int 2: " + unknown2);
        }

        boolean setupPosition = buf.get() == 0;

        Board board = new Board();
        int moveNo = 1;
        if (setupPosition) {
            Piece[][] pieces = new Piece[8][8];
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 8; y++) {
                    Piece p;
                    Piece.PieceColor color = Piece.PieceColor.WHITE;
                    byte pieceCode = buf.get();
                    if ((pieceCode & 8) == 8) {
                        color = Piece.PieceColor.BLACK;
                        pieceCode -= 8;
                    }
                    switch (pieceCode) {
                        case 0 :
                            p = new Piece(Piece.PieceType.EMPTY, Piece.PieceColor.EMPTY);
                            break;
                        case 1 :
                            p = new Piece(Piece.PieceType.KING, color);
                            break;
                        case 2:
                            p = new Piece(Piece.PieceType.QUEEN, color);
                            break;
                        case 3:
                            p = new Piece(Piece.PieceType.KNIGHT, color);
                            break;
                        case 4:
                            p = new Piece(Piece.PieceType.BISHOP, color);
                            break;
                        case 5:
                            p = new Piece(Piece.PieceType.ROOK, color);
                            break;
                        case 6:
                            p = new Piece(Piece.PieceType.PAWN, color);
                            break;
                        default :
                            throw new CBMException(String.format("Unknown piece in setup position at %c%c: %d", (char)('A'+x), (char)('1'+y), pieceCode));
                    }
                    pieces[y][x] = p;
                }
            }

            byte b = buf.get();
            if (b != 0 && b != 1)
                throw new CBMException("Unknown side to move: " + b);
            Piece.PieceColor sideToMove = b == 0 ? Piece.PieceColor.WHITE : Piece.PieceColor.BLACK;


            byte[] bytes = new byte[7];
            buf.get(bytes);
            if (bytes[0] != 0 || bytes[1] != 0 || bytes[2] != 0 || bytes[4] != 0 || bytes[5] != 1 || bytes[6] != 0) {
                throw new CBMException("Unknown setup values: " + Arrays.toString(bytes));
            }
            if (bytes[3] != 0 && bytes[3] != 8) {
                // bytes[3] == 8 occurs in Karsten Müller - Chess Endgames 3/22.wmv" start position
                throw new CBMException("Unknown setup values: " + Arrays.toString(bytes));
            }
            moveNo = buf.getShort();
            b = buf.get();
            if (b != 0) {
                throw new CBMException("Unknown last value in setup position: " + b);
            }

            board.setup(pieces, sideToMove, false, false, false, false, -1);
        }

        // This seems to be some extra game headers in the media format
        // TODO: Figure out what to do with these extra headers
        // Could one of them be teams?
        List<String> headers = new ArrayList<String>();
        int noHeaders = buf.getShort(); // number of extra headers to follow??
//        log.info("# extra headers: " + noHeaders);
        for (int i = 0; i < noHeaders; i++) {
            short size = buf.getShort();
            byte[] header = new byte[size];
            buf.get(header);
            headers.add(Arrays.toString(header));
        }
        // This is also some extra stuff occurring in a few files. Not sure what it is?
        // Nigel Davies - The Tarrasch Defence/Tarrasch.html/04_Monacell_Nadanyan new.wmv
        // Rustam Kasimdzhanov - Endgames for Experts/Endgame.html/endgame adams-kasim.wmv
        // Rustam Kasimdzhanov - Endgames for Experts/Endgame.html/endgame kasim-ghaem.wmv
        // Rustam Kasimdzhanov - Endgames for Experts/Endgame.html/endgame kasim-shirov.wmv
        if (headerSkipBytes != 0) {
            log.debug("Skipping ahead " + headerSkipBytes + " bytes before move data");
        }
        buf.position(buf.position() + headerSkipBytes);

        AnnotatedGame game = new AnnotatedGame(board, moveNo);
        parseMoves(buf, game);

        int last = buf.getInt();
        if (last != 0 || buf.position() != buf.limit()) {
            buf.position(buf.position() - 4);
            byte[] bytes = new byte[buf.limit() - buf.position()];
            buf.get(bytes);
            log.warn("Unexpected trailing bytes: " + Arrays.toString(bytes));
        }

        List<GamePosition> positions = new ArrayList<>();
        enumeratePositions(positions, game);

        GamePosition selectedMove;
        if (selectedMoveNo < positions.size()) {
            selectedMove = positions.get(selectedMoveNo);
        } else {
            throw new CBMException("Invalid selected move " + selectedMoveNo);
        }

        GameModel gameModel = new GameModel(game, metadata, selectedMove);

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

        return gameModel;
    }

    private void enumeratePositions(List<GamePosition> positions, GamePosition current) {
        positions.add(current);
        for (GamePosition position : current.getForwardPositions()) {
            enumeratePositions(positions, position);
        }
    }

    private void parseMoves(ByteBuffer buf, GamePosition pos) throws CBMException {
        short noMoves = buf.getShort();
        short zero = buf.getShort();

        if (zero != 0) {
            throw new CBMException(String.format("Unknown variation data: %02x %02x", noMoves, zero));
        }

        for (int i = 0; i < noMoves; i++) {
            int fromSquare = ByteBufferUtil.getUnsignedByte(buf);
            int toSquare = ByteBufferUtil.getUnsignedByte(buf);
            byte noAnnotations = buf.get(), b2 = buf.get();

            Piece.PieceType promotionPiece = Piece.PieceType.EMPTY;
            if ((fromSquare & 64) == 64) {
                fromSquare -= 64;
                switch (toSquare / 64) {
                    case 0:
                        promotionPiece = Piece.PieceType.QUEEN;
                        break;
                    case 1:
                        promotionPiece = Piece.PieceType.KNIGHT;
                        break;
                    case 3:
                        promotionPiece = Piece.PieceType.ROOK;
                        break;
                    // Presumably 2 = bishop...
                    default:
                        throw new CBMException("Unknown promotion piece: " + toSquare / 64);
                }
                toSquare %= 64;
            }

            if (fromSquare < 0 || fromSquare >= 64 || toSquare < 0 || toSquare >= 64 || b2 != 0) {
                throw new CBMException(String.format("Unknown move data: %d %d %d", fromSquare, toSquare, b2));
            }

            Move move;
            try {
                if (fromSquare == 0 && toSquare == 0) {
                    move = new NullMove();
                } else {
                    move = new Move(pos.getPosition(), fromSquare / 8, fromSquare % 8, toSquare / 8, toSquare % 8, promotionPiece);
                }
            } catch (IllegalArgumentException e) {
                log.error("Illegal move parsing: %d %d %d", fromSquare, toSquare, b2);
                throw e;
            }

//            log.info("Parsed move " + pos.getMoveNumber() + (pos.getPlayerToMove() == Piece.PieceColor.BLACK ? "..." : ".") + move.toString(pos.getPosition()));
            GamePosition newPos = pos.addMove(move);

            for (int j = 0; j < noAnnotations; j++) {
                short annotationLength = buf.getShort();
                int nextPosition = buf.position() + annotationLength;

                int moveNo = ByteBufferUtil.getSignedBigEndian24BitValue(buf);
                if (moveNo != 0) {
                    // Since annotations are embedded, this should always be 0
                    throw new CBMException("Expected move no to be 0");
                }

                // TODO: Don't use the ByteBuffer order and instead explicitly get little or big endian numbers
                ByteOrder oldOrder = buf.order();
                buf.order(ByteOrder.BIG_ENDIAN);
                Annotation annotation = Annotation.getFromData(buf);
                buf.order(oldOrder);
//                log.info("Parsed annotation: " + annotation.getClass().getSimpleName());
                ((AnnotatedGame) pos.getOwnerGame()).addAnnotation(newPos, annotation);

                buf.position(nextPosition);
            }
            byte noVariations = buf.get();

            for (int j = 0; j < noVariations; j++) {
                parseMoves(buf, pos);
            }
            pos = newPos;
        }
    }

    private String readString(ByteBuffer buf) {
        StringBuilder sb = new StringBuilder();
        byte len = buf.get();
        for (int i = 0; i < len; i++) {
            int c = buf.get();
            if (i == len - 1 && c == 0) break;
            sb.append((char) c);
        }
        return sb.toString();
    }

}
