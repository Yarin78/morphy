package yarin.cbhlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yarin.asflib.ASFScriptCommand;
import yarin.asflib.ASFScriptCommandReader;
import yarin.cbhlib.annotations.Annotation;
import yarin.cbhlib.exceptions.CBMException;
import yarin.chess.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class ChessBaseMediaParser {
    private static final Logger log = LoggerFactory.getLogger(ChessBaseMediaParser.class);

    private int readBCDEncodedShort(ByteBuffer buf) {
        int b1 = buf.get(), b2 = buf.get();
        if (b1<0) b1 += 256;
        if (b2<0) b2 += 256;
        return (b1/16)*1000+(b1%16)*100+(b2/16)*10+b2%16;
    }

    public AnnotatedGame parseCommand(ASFScriptCommand cmd) throws CBMException {
        if (!cmd.getType().equals("TEXT")) {
            throw new CBMException("Unsupported command type: " + cmd.getType());
        }

        String command = cmd.getCommand();
        if (command.length() % 2 != 0) {
            throw new CBMException("Invalid format of ChessBase script command");
        }
//        log.info("Command at " + cmd.getMillis() + ", length " + command.length() / 2);

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
            if (buf.get() != 0) throw new CBMException("Unexpected header byte");
        }
        int len = readBCDEncodedShort(buf);
        int bytesLeft = buf.limit() - buf.position();
        if (bytesLeft != len) {
            log.warn("Number of bytes left in buffer (" + bytesLeft + ") didn't match specified length (" + len + ")");
        }

        int commandType = buf.getInt();

//        log.info("Command " + commandType + " at " + cmd.getMillis() + ", length " + bytesLeft);


        // The following command types seems to exist
        // 2 = complete game refresh (sent every 10 seconds)
        // 3 = change selected moved (< 0x60 = ply in main line, otherwise in a variation!?)
        // 4 = insert new move
        // 6 = add annotations
        // 8 = noop!?

        if (commandType == 3) {
            // Change selected moved (< 0x60 = ply in main line, otherwise in a variation!?)
            int selectMoveNo = buf.getInt();
            if (buf.position() != buf.capacity()) {
                throw new CBMException("More bytes left in command type 3 than expected");
            }
            return null;
        }

        if (commandType == 4) {
            // Add a move
            String code = readBinaryToString(buf, 4);
//            int code = buf.getInt();
            int fromSquare = buf.get();
            int toSquare = buf.get();
            String code2 = readBinaryToString(buf, 2); // bit 15 set if capture
            /*
            log.info(String.format("MOVE %c%c-%c%c   %s   %s",
                    (char)('A'+fromSquare / 8),
                    (char)('1'+fromSquare % 8),
                    (char)('A'+toSquare / 8),
                    (char)('1'+toSquare % 8),
                    code,
                    code2
                    ));
            */
            if (buf.position() != buf.capacity()) {
                throw new CBMException("More bytes left in command type 4 than expected");
            }
            return null;
        }

        if (commandType == 6) {
            int unknown = buf.getInt();
            if (unknown != 0 && unknown != 1) {
                // Is usually 0, but was 1 in Andrew Martin - The ABC of the Benko Gambit (2nd Edition)/10 Accepted.wmv
                // Version!?
                throw new CBMException("Expected 0 or 1 at start of add annotations");
            }
            short noAnnotations = buf.getShort();
//            log.info("# annotations: " + noAnnotations);
            for (int i = 0; i < noAnnotations; i++) {
                int annotationSize = buf.getShort();
                int next = buf.position() + annotationSize;
                String s = readBinaryToString(buf, 3);
                if (!s.equals("00 00 00 "))
                    throw new CBMException("Three first annotation bytes should be 0");

                // TOOD: Parse annotation the same way as usual, starting with one byte of annotation type etc

                buf.position(next);
            }
            if (buf.position() != buf.capacity()) {
                throw new CBMException("More bytes left in command type 6 than expected");
            }
            return null;
        }

        if (commandType == 7) {
//            log.info("Command type 7: " + readBinaryToString(buf, bytesLeft - 4));
            int zero = buf.getInt();
            if (zero != 0 || buf.position() != buf.capacity())
                throw new CBMException("Expected 0 as only data for command type 7");
            return null;
        }

        if (commandType == 8) {
            int zero = buf.getInt();
            if (zero != 0 || buf.position() != buf.capacity())
                throw new CBMException("Expected 0 as only data for command type 8");
            return null;
        }

        if (commandType == 10) {
            int zero = buf.getInt();
            if (zero != 0 || buf.position() != buf.capacity())
                throw new CBMException("Expected 0 as only data for command type 10");

//            log.info("Command type 10: " + readBinaryToString(buf, bytesLeft - 4));
            return null;
        }

        if (commandType == 11) {
            // Occurs in Karsten Müller - Chess Endgames 3/68.wmv
            // Go back to start of game!?
            int zero = buf.getInt();
            if (zero != -1 || buf.position() != buf.capacity())
                throw new CBMException("Expected -1 as only data for command type 11");

            return null;
        }

        if (commandType == 2) {
//            log.info(String.format("Board position at %d:%02d", (cmd.getMillis() - 5000) / 1000 / 60, (cmd.getMillis() - 5000) / 1000 % 60));

            int zero = buf.getInt();
            if (zero != 0) {
                throw new CBMException("Expected 0 as first int for command type 2");
            }

            short selectedMoveNo = buf.getShort();

            String part2 = readBinaryToString(buf, 6);
            if (!part2.equals("00 00 00 00 00 00 ")) {
                throw new CBMException("Unknown part 2: " + part2);
            }

            String whitePlayerLastName = readString(buf);
            String whitePlayerFirstName = readString(buf);
            String blackPlayerLastName = readString(buf);
            String blackPlayerFirstName = readString(buf);

            String site = readString(buf);
            String event = readString(buf);

            Date eventDate = new Date(buf.getInt());
            int eventTypeValue = buf.get();
            if ((eventTypeValue & 31) < 0 || (eventTypeValue & 31) >= TournamentType.values().length)
                throw new CBMException("Invalid event type value " + eventTypeValue);
            TournamentType eventType = TournamentType.values()[eventTypeValue & 31];

            TournamentTimeControls timeControl = new TournamentTimeControls();
            if ((eventTypeValue & 0x20) > 0)
                timeControl.add(TournamentTimeControls.TournamentTimeControl.Blitz);
            if ((eventTypeValue & 0x40) > 0)
                timeControl.add(TournamentTimeControls.TournamentTimeControl.Rapid);
            if ((eventTypeValue & 0x80) > 0)
                timeControl.add(TournamentTimeControls.TournamentTimeControl.Corresp);

            int eventValue2 = buf.get();
            short eventCountryCode = buf.getShort();
            int eventCategory = buf.get();
            int eventValue = buf.get(); // Not sure what this is? Not always 0, can be 3
            int eventRounds = buf.getShort();

            // Parsing event info same way as it's stored in the Tournament db
/*
            Date eventDate = new Date(ByteBufferUtil.getLittleEndian24BitValue(buf));
            int eventTypeValue = ByteBufferUtil.getUnsignedByte(buf);
            TournamentType eventType = TournamentType.values()[eventTypeValue & 31];
            TournamentTimeControls timeControl = new TournamentTimeControls();
            if ((eventTypeValue & 0x20) > 0)
                timeControl.add(TournamentTimeControls.TournamentTimeControl.Blitz);
            if ((eventTypeValue & 0x40) > 0)
                timeControl.add(TournamentTimeControls.TournamentTimeControl.Rapid);
            if ((eventTypeValue & 0x80) > 0)
                timeControl.add(TournamentTimeControls.TournamentTimeControl.Corresp);
            boolean teamTournament = (buf.get() & 1) > 0;
            short eventCountryCode = buf.getShort();
            int eventCategory = buf.get();
            byte b = buf.get();
            //if ((cbtData.get(88) & 3) != 0 && (cbtData.get(88) & 3) != 3)
            //throw new CBHFormatException("Tournament complete flag has invalid value");
            boolean complete = (b & 2) > 0;
            boolean boardPoints = (b & 4) == 4;
            boolean threePointsWin = (b & 8) > 0;
            byte eventRounds = buf.get();
*/

            String sourceTitle = readString(buf);
            String source = readString(buf);

            Date sourceDate = new Date(buf.getInt());
            Date someOtherDate = new Date(buf.getInt());
            String part4 = readBinaryToString(buf, 2);

            // part4 is usually 00 00 but can also be
            // 01 02 in Müller/19
            // 02 01 in Ari Ziegler - French Defence/25.wmv
            // 01 01 in ?
            // 00 01 in ?

            String annotator = readString(buf);

            short whiteRating = buf.getShort();
            short blackRating = buf.getShort();

            Eco eco = Eco.parse(ByteBufferUtil.getUnsignedShort(buf));
            GameResult result = GameResult.values()[buf.get()];

            int unknownShort = buf.getShort(); // 0 or 13!?
            Date date = new Date(buf.getInt());
            int lastMoveNumber = buf.getShort();
            int round = buf.get();
            int subRound = buf.get();

            String part5 = readBinaryToString(buf, 12); // This always seems to be 00?

            if (!part5.equals("00 00 00 00 00 00 00 00 00 00 00 00 ")) {
                throw new CBMException("Unknown part 5: " + part5);
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
                String setupPos = readBinaryToString(buf, 7);
                if (!setupPos.equals("00 00 00 00 00 01 00 ") && !setupPos.equals("00 00 00 08 00 01 00 ")) {
                    // "00 00 00 08 00 01 00" occurs in Karsten Müller - Chess Endgames 3/22.wmv" start position
                    throw new CBMException("Unknown setup values: " + setupPos);
                }
                moveNo = buf.getShort();
                b = buf.get();
                if (b != 0) {
                    throw new CBMException("Unknown last value in setup position: " + b);
                }

                board.setup(pieces, sideToMove, false, false, false, false, -1);
            }

            List<String> headers = new ArrayList<String>();
            int noHeaders = buf.getShort(); // number of extra headers to follow??
            for (int i = 0; i < noHeaders; i++) {
                short size = buf.getShort();
                String header = readBinaryToString(buf, size);
                headers.add(header);
            }

            AnnotatedGame game = new AnnotatedGame(board, moveNo);
            parseMoves(buf, game);

            int last = buf.getInt();
            if (last != 0 || buf.limit() != buf.capacity()) {
                buf.position(buf.position() - 4);
                String rest = readBinaryToString(buf, buf.limit() - buf.position());
                log.warn("Unexpected trailing bytes: " + rest);
            }

            List<GamePosition> positions = new ArrayList<>();
            enumeratePositions(positions, game);

            GamePosition selectedMove = null;
            if (selectedMoveNo < positions.size()) {
                selectedMove = positions.get(selectedMoveNo);
            } else {
                throw new CBMException("Invalid selected move " + selectedMoveNo);
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

            return null;
        }
        throw new CBMException("Unknown command type " + commandType + " with data " + readBinaryToString(buf, buf.limit() - buf.position()));
    }

    private void enumeratePositions(List<GamePosition> positions, GamePosition current) {
        positions.add(current);
        for (GamePosition position : current.getForwardPositions()) {
            enumeratePositions(positions, position);
        }
    }

    private void parseMoves(ByteBuffer buf, GamePosition pos) throws CBMException {
        String t = readBinaryToString(buf, 4);
        buf.position(buf.position() - 4);
        short noMoves = buf.getShort();
        short zero = buf.getShort();

        if (zero != 0) {
            throw new CBMException("Unknown variation data: " + t);
        }

        for (int i = 0; i < noMoves; i++) {
            int cnt = Math.min(80, buf.limit() - buf.position());
            String s = readBinaryToString(buf, cnt);
            buf.position(buf.position() - cnt);

            int fromSquare = buf.get(), toSquare = buf.get();
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
                    default:
                        throw new CBMException("Unknown promotion piece: " + toSquare / 64);
                }
                toSquare %= 64;
            }

            if (fromSquare < 0 || fromSquare >= 64 || toSquare < 0 || toSquare >= 64 || b2 != 0) {
                throw new CBMException("Unknown move data: " + s);
            }

            Move move;
            try {
                if (fromSquare == 0 && toSquare == 0) {
                    move = new NullMove();
                } else {
                    move = new Move(pos.getPosition(), fromSquare / 8, fromSquare % 8, toSquare / 8, toSquare % 8, promotionPiece);
                }
            } catch (IllegalArgumentException e) {
                log.error("Illegal move parsing " + s);
                throw e;
            }
//            log.info("Parsed move " + move);
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

    private String readBinaryToString(ByteBuffer buf, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            int b = buf.get();
            if (b < 0) b += 256;
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
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
