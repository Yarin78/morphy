package yarin.cbhlib;

import yarin.cbhlib.annotations.Annotation;
import yarin.cbhlib.exceptions.CBHException;
import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

public class AnnotatedGame extends Game {
    private static final int OPCODE_NULLMOVE = 0;
    private static final int OPCODE_KING = 1;
    private static final int OPCODE_KING_OO = 9;
    private static final int OPCODE_KING_OOO = 10;
    private static final int OPCODE_QUEEN_1 = 11;
    private static final int OPCODE_ROOK_1 = 39;
    private static final int OPCODE_ROOK_2 = 53;
    private static final int OPCODE_BISHOP_1 = 67;
    private static final int OPCODE_BISHOP_2 = 81;
    private static final int OPCODE_KNIGHT_1 = 95;
    private static final int OPCODE_KNIGHT_2 = 103;
    private static final int OPCODE_PAWNS = 111;
    private static final int OPCODE_QUEEN_2 = 143;
    private static final int OPCODE_QUEEN_3 = 171;
    private static final int OPCODE_ROOK_3 = 199;
    private static final int OPCODE_BISHOP_3 = 213;
    private static final int OPCODE_KNIGHT_3 = 227;
    private static final int OPCODE_TWO_BYTES = 235;
    private static final int OPCODE_IGNORE = 236;
    private static final int OPCODE_START_VARIANT = 254;
    private static final int OPCODE_END_VARIANT = 255;

    private static int[] kingDirY = new int[]{1, 1, 0, -1, -1, -1, 0, 1};
    private static int[] kingDirX = new int[]{0, 1, 1, 1, 0, -1, -1, -1};
    private static int[] knightDirY = new int[]{1, 2, 2, 1, -1, -2, -2, -1};
    private static int[] knightDirX = new int[]{2, 1, -1, -2, -2, -1, 1, 2};

    // This encryption map occurs in in CBase9.exe starting at position 0x7AE4A8
    // Hence it has probably been randomly generated, so no formula exist.
    // In CBase10.exe, it starts at 0x9D6530
    private static int[] encryptMap = new int[] {
        0xAA, 0x49, 0x39, 0xD8, 0x5D, 0xC2, 0xB1, 0xB2,
        0x47, 0x76, 0xB5, 0xA5, 0xB8, 0xCB, 0x53, 0x7F,
        0x6B, 0x8D, 0x79, 0xBE, 0xEB, 0x21, 0x99, 0xD2,
        0x57, 0x4D, 0xB4, 0xBF, 0x62, 0xBD, 0x24, 0x96,
        0xA7, 0x48, 0x28, 0x6E, 0x2F, 0x5A, 0x18, 0x4E,
        0xF8, 0x43, 0xD7, 0x63, 0x9C, 0xE6, 0x2E, 0xC6,
        0x26, 0x88, 0x30, 0x61, 0x6F, 0x14, 0xA9, 0x68,
        0xEE, 0xFB, 0x77, 0xE2, 0xA6, 0x05, 0x8B, 0xA1,
        0x98, 0x32, 0x52, 0x02, 0x97, 0xE1, 0x41, 0xC3,
        0x7C, 0xE4, 0x06, 0xB7, 0x55, 0xD9, 0x2C, 0xAE,
        0x37, 0xF6, 0x3F, 0x08, 0x93, 0x73, 0x5E, 0x78,
        0x35, 0xF2, 0x6D, 0x71, 0xA2, 0xF3, 0x16, 0x58,
        0x3D, 0xFA, 0xE9, 0xBA, 0xD4, 0xDD, 0x4A, 0xC4,
        0x0E, 0xFE, 0x5F, 0x75, 0x07, 0x89, 0x34, 0x2D,
        0xC1, 0x8E, 0xF5, 0x64, 0x17, 0x70, 0xA4, 0x7B,
        0xDA, 0xE0, 0x85, 0xC5, 0x0B, 0x90, 0xF9, 0x84,
        0xFF, 0x15, 0x36, 0x09, 0x9E, 0x7D, 0xDE, 0xBB,
        0xDF, 0xBC, 0x3A, 0x12, 0x33, 0x13, 0x19, 0xE5,
        0x94, 0x50, 0x11, 0xEA, 0x31, 0x01, 0x5C, 0x95,
        0xCA, 0xD3, 0x1D, 0x7E, 0xEF, 0x44, 0x80, 0xA0,
        0x1F, 0x83, 0x00, 0x4B, 0x67, 0x20, 0x5B, 0x2A,
        0x92, 0xB6, 0x60, 0x1A, 0x42, 0x0F, 0x0D, 0xB0,
        0xD1, 0x23, 0xF0, 0x7A, 0x54, 0x4F, 0xF4, 0xA8,
        0x72, 0xE7, 0x40, 0x38, 0x59, 0x87, 0xE8, 0x6C,
        0x86, 0x04, 0xF1, 0x8C, 0xCE, 0x6A, 0xDB, 0x81,
        0x82, 0x9A, 0x1B, 0x9D, 0x0A, 0x2B, 0x8F, 0xCD,
        0xED, 0x10, 0x74, 0x69, 0xD6, 0x51, 0xB9, 0x45,
        0x3B, 0x56, 0x91, 0xFD, 0xAB, 0x66, 0x3E, 0x46,
        0xB3, 0xFC, 0xC8, 0x9B, 0xC0, 0xE3, 0xA3, 0xAC,
        0xC9, 0xEC, 0x27, 0x29, 0x9F, 0x25, 0xC7, 0xCC,
        0x65, 0x4C, 0xD5, 0x1E, 0xCF, 0x03, 0x8A, 0xAF,
        0xF7, 0xAD, 0x3C, 0xD0, 0x22, 0x1C, 0xDC, 0x0C
    };

    // Inverse of the above map
    private static int[] decryptMap;

    static {
        decryptMap = new int[256];
        for (int i = 0; i < 256; i++)
            decryptMap[encryptMap[i]] = i;
    }

    private HashMap<GamePosition, List<Annotation>> annotationMap = new HashMap<>();

    public AnnotatedGame(ByteBuffer moveData, ByteBuffer annotationData)
            throws CBHException {
        List<GamePosition> allPositions = parseMoveData(moveData, true);
        parseAnnotationData(annotationData, allPositions);
    }

    public AnnotatedGame(Board b, int moveNo, ByteBuffer moveData, ByteBuffer annotationData)
            throws CBHException {
        super(b, moveNo);
        List<GamePosition> allPositions = parseMoveData(moveData, true);
        parseAnnotationData(annotationData, allPositions);
    }

    /**
     * Gets all annotations for a specified position
     * @param position The position to get annotations for
     * @return All annotations for this game position, or an empty array if there are no annotations for this position.
     */
    public List<Annotation> getAnnotations(GamePosition position) {
        if (position.getOwnerGame() != this)
            throw new IllegalArgumentException("The position must belong to this game.");
        List<Annotation> annotations = annotationMap.get(position);
        if (annotations == null)
            return new ArrayList<>();
        return annotations;
    }

    /**
     * Gets all annotations for a specified position of a specific type
     * @param position The position to get annotations for
     * @param clazz The type of annotations to get
     * @return All annotations of the specified classs for this game position,
     * or an empty array if there are no annotations for this position.
     */
    public <T extends Annotation> List<T> getAnnotations (GamePosition position, Class<?> clazz) {
        if (position.getOwnerGame() != this)
            throw new IllegalArgumentException("The position must belong to this game.");
        List<Annotation> annotations = annotationMap.get(position);
        if (annotations == null)
            return new ArrayList<>();
        ArrayList<T> result = new ArrayList<>();
        for (Annotation annotation : annotations) {
            if (annotation.getClass().isAssignableFrom(clazz)) {
                result.add((T) annotation);
            }

        }
        return result;
    }

    /**
     * Gets an annotation for a specified position of a specific type.
     * If there are more than one matching annotation, the first is returned.
     * @param position The position to get the annotation for
     * @param clazz The type of annotation to get
     * @return An annotation of the specified type, or null if no annotation of that type for the given position exists
     */
    public <T extends Annotation> T getAnnotation (GamePosition position, Class<?> clazz) {
        if (position.getOwnerGame() != this)
            throw new IllegalArgumentException("The position must belong to this game.");
        List<Annotation> annotations = annotationMap.get(position);
        if (annotations == null)
            return null;
        for (Annotation annotation : annotations) {
            if (annotation.getClass().isAssignableFrom(clazz)) {
                return (T) annotation;
            }
        }
        return null;
    }

    protected String getPreMoveComment(GamePosition position) {
        List<Annotation> annotations = getAnnotations(position);
        StringBuilder sb = new StringBuilder();
        for(Annotation a : annotations) {
            String preText = a.getPreText();
            if (preText != null)
                sb.append(preText);
        }
        if (sb.length() == 0)
            return null;
        return sb.toString();
    }

    protected String getPostMoveComment(GamePosition position) {
        List<Annotation> annotations = getAnnotations(position);
        StringBuilder sb = new StringBuilder();
        for(Annotation a : annotations) {
            String postText = a.getPostText();
            if (postText != null)
                sb.append(postText);
        }
        if (sb.length() == 0)
            return null;
        return sb.toString();
    }


    private void parseAnnotationData(ByteBuffer annotationData, List<GamePosition> gamePositionsByOrder)
            throws CBHFormatException {
        if (annotationData.limit() == 0)
            return;
        if (annotationData.limit() < 14)
            throw new CBHFormatException("Unexpected end of annotation data");
        annotationData.position(10);
        int noAnnotationBytes = annotationData.getInt();
        if (annotationData.limit() < noAnnotationBytes)
            throw new CBHFormatException("Unexpected end of annotation data");

        while (annotationData.position() < annotationData.limit()) {
            Annotation annotation = Annotation.getFromData(gamePositionsByOrder, annotationData, true);
            // TODO: Is this correct?
            if (annotation != null && annotation.getPosition() != null) {
                if (!annotationMap.containsKey(annotation.getPosition()))
                    annotationMap.put(annotation.getPosition(), new ArrayList<>());
                annotationMap.get(annotation.getPosition()).add(annotation);
            }
        }
    }

    private List<GamePosition> parseMoveData(ByteBuffer moveData, boolean verifyAfterEachMove)
            throws CBHException {
        int[][][] piecePosition = getPiecePositions();

        int modifier = 256; // Start at 256 instead of 0 to avoid negative modulo
        Stack<GamePosition> positionStack = new Stack<>();
        Stack<int[][][]> piecePositionStack = new Stack<>();
        GamePosition currentPosition = this;

        // All positions in the game in a flat list, excluding the initial position
        ArrayList<GamePosition> positionsInOrder = new ArrayList<>();

        //string moveSeq = "";

        while (true) {
            int data = decryptMap[(moveData.get() + modifier) % 256];

            if (data == OPCODE_IGNORE) {
                // Not sure what this opcode do. Just ignoring it seems works fine.
                // Chessbase 9 removes this opcode when replacing the game.
                continue;
            } else if (data > OPCODE_IGNORE && data < OPCODE_START_VARIANT) {
                throw new CBHFormatException(String.format("Unknown opcode: 0x%02X", data));
            } else if (data == OPCODE_START_VARIANT) {
                int[][][] piecePositionClone = new int[7][3][];
                for (int i = 1; i <= 6; i++)
                    for (int j = 1; j <= 2; j++)
                        piecePositionClone[i][j] = piecePosition[i][j].clone();

                positionStack.push(currentPosition);
                piecePositionStack.push(piecePositionClone);
                continue;
            } else if (data == OPCODE_END_VARIANT) {
                // Also used as end of game
                if (positionStack.size() == 0)
                    break;

                currentPosition = positionStack.pop();
                piecePosition = piecePositionStack.pop();
                continue;
            }

            // We have a move. Decode it and update the position and adjust the piece position structure if necessary.
            Move move;
            Piece.PieceType pieceType;
            Piece.PieceColor moveColor = currentPosition.getPlayerToMove();
            Board board = currentPosition.getPosition();

            if (data < OPCODE_TWO_BYTES) {
                int pieceNo;
                if (data == OPCODE_NULLMOVE) {
                    move = new NullMove();
                    pieceType = Piece.PieceType.EMPTY;
                    pieceNo = 0;
                } else if (data < OPCODE_QUEEN_1) {
                    pieceNo = 0;
                    pieceType = Piece.PieceType.KING;
                    int backRank = moveColor == Piece.PieceColor.WHITE ? 0 : 7;
                    if (data == OPCODE_KING_OO)
                        move = new Move(currentPosition.getPosition(), 4, backRank, 6, backRank);
                    else if (data == OPCODE_KING_OOO)
                        move = new Move(currentPosition.getPosition(), 4, backRank, 2, backRank);
                    else {
                        int dir = data - OPCODE_KING;
                        Square p = getPieceSquare(piecePosition, Piece.PieceType.KING, moveColor, 0);
                        move = new Move(board, p.x, p.y, p.x + kingDirX[dir], p.y + kingDirY[dir]);
                    }
                } else if ((data < OPCODE_KNIGHT_1) || (data >= OPCODE_QUEEN_2 && data < OPCODE_KNIGHT_3)) {
                    if (data >= OPCODE_BISHOP_3) {
                        pieceNo = 2;
                        data -= OPCODE_BISHOP_3;
                        pieceType = Piece.PieceType.BISHOP;
                    } else if (data >= OPCODE_ROOK_3) {
                        pieceNo = 2;
                        data -= OPCODE_ROOK_3;
                        pieceType = Piece.PieceType.ROOK;
                    } else if (data >= OPCODE_QUEEN_3) {
                        pieceNo = 2;
                        data -= OPCODE_QUEEN_3;
                        pieceType = Piece.PieceType.QUEEN;
                    } else if (data >= OPCODE_QUEEN_2) {
                        pieceNo = 1;
                        data -= OPCODE_QUEEN_2;
                        pieceType = Piece.PieceType.QUEEN;
                    } else if (data >= OPCODE_BISHOP_2) {
                        pieceNo = 1;
                        data -= OPCODE_BISHOP_2;
                        pieceType = Piece.PieceType.BISHOP;
                    } else if (data >= OPCODE_BISHOP_1) {
                        pieceNo = 0;
                        data -= OPCODE_BISHOP_1;
                        pieceType = Piece.PieceType.BISHOP;
                    } else if (data >= OPCODE_ROOK_2) {
                        pieceNo = 1;
                        data -= OPCODE_ROOK_2;
                        pieceType = Piece.PieceType.ROOK;
                    } else if (data >= OPCODE_ROOK_1) {
                        pieceNo = 0;
                        data -= OPCODE_ROOK_1;
                        pieceType = Piece.PieceType.ROOK;
                    } else if (data >= OPCODE_QUEEN_1) {
                        pieceNo = 0;
                        data -= OPCODE_QUEEN_1;
                        pieceType = Piece.PieceType.QUEEN;
                    } else
                        throw new RuntimeException("Opcode error"); // Shouldn't happen

                    Square p = getPieceSquare(piecePosition, pieceType, moveColor, pieceNo);
                    int dir = data / 7, stride = data % 7 + 1;
                    switch (dir + (pieceType == Piece.PieceType.BISHOP ? 2 : 0)) {
                        case 0:
                            move = new Move(board, p.x, p.y, p.x, (p.y + stride) % 8);
                            break;
                        case 1:
                            move = new Move(board, p.x, p.y, (p.x + stride) % 8, p.y);
                            break;
                        case 2:
                            move = new Move(board, p.x, p.y, (p.x + stride) % 8, (p.y + stride) % 8);
                            break;
                        case 3:
                            move = new Move(board, p.x, p.y, (p.x + stride) % 8, (p.y + 8 - stride) % 8);
                            break;
                        default:
                            throw new RuntimeException("Opcode error"); // Shouldn't happen
                    }
                } else if ((data < OPCODE_PAWNS) || (data >= OPCODE_KNIGHT_3 && data < OPCODE_TWO_BYTES)) {
                    pieceType = Piece.PieceType.KNIGHT;
                    if (data >= OPCODE_KNIGHT_3) {
                        pieceNo = 2;
                        data -= OPCODE_KNIGHT_3;
                    } else if (data >= OPCODE_KNIGHT_2) {
                        pieceNo = 1;
                        data -= OPCODE_KNIGHT_2;
                    } else {
                        pieceNo = 0;
                        data -= OPCODE_KNIGHT_1;
                    }
                    Square sq = getPieceSquare(piecePosition, Piece.PieceType.KNIGHT, moveColor, pieceNo);
                    move = new Move(board, sq.x, sq.y, sq.x + knightDirX[data], sq.y + knightDirY[data]);
                } else if (data >= OPCODE_PAWNS && data < OPCODE_QUEEN_2) {
                    data -= OPCODE_PAWNS;
                    pieceNo = data / 4;
                    pieceType = Piece.PieceType.PAWN;
                    int pawnMove = data % 4;
                    Square sq = getPieceSquare(piecePosition, Piece.PieceType.PAWN, moveColor, pieceNo);
                    int dir = moveColor == Piece.PieceColor.WHITE ? 1 : -1;
                    switch (pawnMove) {
                        case 0:
                            move = new Move(board, sq.x, sq.y, sq.x, sq.y + dir);
                            break;
                        case 1:
                            move = new Move(board, sq.x, sq.y, sq.x, sq.y + dir * 2);
                            break;
                        case 2:
                            move = new Move(board, sq.x, sq.y, sq.x + dir, sq.y + dir);
                            break;
                        case 3:
                            move = new Move(board, sq.x, sq.y, sq.x - dir, sq.y + dir);
                            break;
                        default:
                            throw new RuntimeException("Opcode error"); // Shouldn't happen
                    }
                } else
                    throw new RuntimeException("Opcode error"); // Shouldn't happen

                // Update position of the moved piece
                if (pieceType != Piece.PieceType.EMPTY)
                    piecePosition[pieceType.ordinal()][moveColor.ordinal()][pieceNo]=move.getY2() * 8 + move.getX2();
                if (move.isCastle()) {
                    int rookX1 = move.getX2() == 6 ? 7 : 0;
                    int rookX2 = (move.getX1() + move.getX2()) / 2;
                    int rookY = moveColor == Piece.PieceColor.WHITE ? 0 : 7;
                    // Update rook position as well
                    for (int i = 0; i < 3; i++) {
                        if (piecePosition[Piece.PieceType.ROOK.ordinal()][moveColor.ordinal()][i]==rookX1 + rookY * 8)
                        piecePosition[Piece.PieceType.ROOK.ordinal()][moveColor.ordinal()][i]=rookX2 + rookY * 8;
                    }
                }
            } else if (data == OPCODE_TWO_BYTES) {
                int msb = decryptMap[(moveData.get() + modifier) % 256];
                int lsb = decryptMap[(moveData.get() + modifier) % 256];
                int word = msb * 256 + lsb;
                int y1 = word % 8;
                int x1 = (word / 8) % 8;
                int y2 = (word / 64) % 8, x2 = (word / 512) % 8;
                pieceType = board.pieceAt(y1, x1).getPiece();
                if (pieceType == Piece.PieceType.EMPTY)
                    throw new CBHFormatException("No piece at source position");
                if (pieceType == Piece.PieceType.PAWN) {
                    // This should be a pawn promotion, weird otherwise
                    if (y2 > 0 && y2 < 7)
                        throw new CBHFormatException("Double bytes used for non-promotion pawn move");
                    // Remove the pawn
                    boolean found = false;
                    for (int i = 0; i < 8 && !found; i++) {
                        if (piecePosition[Piece.PieceType.PAWN.ordinal()][moveColor.ordinal()][i]==y1 * 8 + x1)
                        {
                            piecePosition[Piece.PieceType.PAWN.ordinal()][moveColor.ordinal()][i]=-1;
                            found = true;
                        }
                    }
                    if (!found)
                        throw new CBHFormatException("Board in inconsistent state");

                    // Add the promoted piece, unless three pieces of that type already exist
                    Piece.PieceType promotedPiece;
                    switch (word / 4096) {
                        case 0:
                            promotedPiece = Piece.PieceType.QUEEN;
                            break;
                        case 1:
                            promotedPiece = Piece.PieceType.ROOK;
                            break;
                        case 2:
                            promotedPiece = Piece.PieceType.BISHOP;
                            break;
                        case 3:
                            promotedPiece = Piece.PieceType.KNIGHT;
                            break;
                        default:
                            throw new CBHException("Illegal promoted piece");
                    }
                    for (int i = 0; i < 3; i++) {
                        if (piecePosition[promotedPiece.ordinal()][moveColor.ordinal()][i]==-1)
                        {
                            piecePosition[promotedPiece.ordinal()][moveColor.ordinal()][i]=y2 * 8 + x2;
                            break;
                        }
                    }
                    move = new Move(board, x1, y1, x2, y2, promotedPiece);
                } else {
                    move = new Move(board, x1, y1, x2, y2);
                    // Sanity check: make sure this piece doesn't occur in piecePosition
                    for (int i = 0; i < 3; i++) {
                        if (piecePosition[pieceType.ordinal()][moveColor.ordinal()][i]==y1 * 8 + x1)
                        throw new CBHException("A piece was moved with double bytes even though it was among the first three");
                    }
                }
            } else
                throw new RuntimeException("Opcode error"); // Shouldn't happen


            if (move.isCapture()) {
                // If there is a capture of a knight, bishop, rook or queen,
                // those pieces must be adjusted.
                Piece.PieceType capturedPiece = board.pieceAt(move.getY2(), move.getX2()).getPiece();
                if (capturedPiece != Piece.PieceType.EMPTY && capturedPiece != Piece.PieceType.PAWN) // Empty if ep
                {
                    int[] adjustPos = piecePosition[capturedPiece.ordinal()][3 - board.getToMove().ordinal()];
                    int i = 0, j = 0, capSq = move.getY2() * 8 + move.getX2();
                    while (i < adjustPos.length) {
                        if (adjustPos[i] != capSq)
                            adjustPos[j++] = adjustPos[i++];
                        else
                            i++;
                    }
                    while (j < adjustPos.length)
                        adjustPos[j++] = -1;
                } else {
                    // Remove captured pawn from list
                    int capSq = move.getY2() * 8 + move.getX2();
                    if (capturedPiece == Piece.PieceType.EMPTY) // En passant
                    {
                        capSq = move.getY1() * 8 + move.getX2();
                        capturedPiece = Piece.PieceType.PAWN;
                    }

                    int[] pawnRemove = piecePosition[capturedPiece.ordinal()][3 - board.getToMove().ordinal()];
                    for (int i = 0; i < 8; i++)
                        if (pawnRemove[i] == capSq)
                            pawnRemove[i] = -1;
                }
            }
            modifier = (modifier + 255) % 256;

            //moveSeq += move.toString(board) + "  ";

            currentPosition = currentPosition.addMove(move);
            positionsInOrder.add(currentPosition);

            if (verifyAfterEachMove) {
                // Verify that board and piece positions match
                // Only for debugging!
                int piecesFound = 0, piecesOnBoard = 0;
                for (Piece.PieceColor color : Piece.PieceColor.all()) {
                    for (Piece.PieceType i : Piece.PieceType.all()) {
                        boolean endReached = false;
                        for (int j = 0; j < piecePosition[i.ordinal()][color.ordinal()].length; j++)
                        {
                            int sq = piecePosition[i.ordinal()][color.ordinal()][j];
                            if (sq == -1)
                                endReached = true;
                            else {
                                if (endReached && i != Piece.PieceType.PAWN)
                                    throw new CBHFormatException("Pieces not adjusted correctly");
                                Piece p = currentPosition.getPosition().pieceAt(sq / 8, sq % 8);
                                if (p.getColor() != color || p.getPiece() != i)
                                    throw new CBHFormatException("Board is in inconsistent state");
                                piecesFound++;
                            }
                        }
                        if (i != Piece.PieceType.PAWN) {
                            // There may be more than 3 pieces of this color, count them off
                            for (int y = 0; y < 8; y++) {
                                for (int x = 0; x < 8; x++) {
                                    Piece p = currentPosition.getPosition().pieceAt(y, x);
                                    if (p.getColor() == color && p.getPiece() == i) {
                                        boolean found = false;
                                        for(int sq : piecePosition[i.ordinal()][color.ordinal()])
                                            if (sq == y * 8 + x)
                                                found = true;
                                        if (!found)
                                            piecesFound++; // This is an extra piece
                                    }
                                }
                            }
                        }
                    }
                }
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        if (!currentPosition.getPosition().pieceAt(y, x).isEmpty())
                            piecesOnBoard++;
                    }
                }
                if (piecesFound != piecesOnBoard)
                    throw new CBHFormatException("Board is in inconsistent state"); // Some pieces are missing
            }
        }

        return positionsInOrder;
    }

    private static class Square {
        int x, y;

        Square(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private Square getPieceSquare(int[][][] piecePosition, Piece.PieceType type, Piece.PieceColor color, int pieceNo) {
        int xy = piecePosition[type.ordinal()][color.ordinal()][pieceNo];
        return new Square(xy % 8, xy / 8);
    }

    private int[][][] getPiecePositions() {
        // Current position for pieces
        int[][][] piecePosition = new int[7][3][];
        for (int i = 1; i <= 6; i++) {
            int cnt = 3;
            if (Piece.PieceType.values()[i] == Piece.PieceType.PAWN)
                cnt = 8;
            if (Piece.PieceType.values()[i] == Piece.PieceType.KING)
                cnt = 1;

            for (int k = 1; k <= 2; k++) {
                piecePosition[i][k] = new int[cnt];
                for (int j = 0; j < cnt; j++)
                    piecePosition[i][k][j] = -1;
            }
        }
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                Piece p = this.getPosition().pieceAt(y, x);
                if (p.isEmpty())
                    continue;
                int[] pp = piecePosition[p.getPiece().ordinal()][p.getColor().ordinal()];
                for (int i = 0; i < pp.length; i++) {
                    if (pp[i] == -1) {
                        pp[i] = y * 8 + x;
                        break;
                    }
                }
            }
        }
        return piecePosition;
    }
}