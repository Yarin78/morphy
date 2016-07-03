package se.yarin.chess;

import java.util.*;
import java.util.stream.Collectors;

import static se.yarin.chess.Chess.*;
import static se.yarin.chess.Piece.*;
import static se.yarin.chess.Player.*;
import static se.yarin.chess.Stone.*;
import static se.yarin.chess.Castles.*;

/**
 * Represents an immutable position in a chess game, including side to move, castle rights
 * and en passant file.
 * Enough information is contained generate all legal moves.
 *
 * If the position is invalid, the behaviour of some methods are undefined.
 */
public class Position {

    // Zobrist Hashing is used to calculate the hash code for a position
    private static long[][] zobristKey = new long[13][64];
    private static long[] zobristKeyCastle = new long[16];
    private static long[] zobristKeyToMove = new long[2];
    private static long[] zobristKeyEnPassant = new long[9];

    private static Position startPosition;

    private static Stone[] startBoard = new Stone[]{
            WHITE_ROOK,   WHITE_PAWN, NO_STONE, NO_STONE, NO_STONE, NO_STONE, BLACK_PAWN, BLACK_ROOK,
            WHITE_KNIGHT, WHITE_PAWN, NO_STONE, NO_STONE, NO_STONE, NO_STONE, BLACK_PAWN, BLACK_KNIGHT,
            WHITE_BISHOP, WHITE_PAWN, NO_STONE, NO_STONE, NO_STONE, NO_STONE, BLACK_PAWN, BLACK_BISHOP,
            WHITE_QUEEN,  WHITE_PAWN, NO_STONE, NO_STONE, NO_STONE, NO_STONE, BLACK_PAWN, BLACK_QUEEN,
            WHITE_KING,   WHITE_PAWN, NO_STONE, NO_STONE, NO_STONE, NO_STONE, BLACK_PAWN, BLACK_KING,
            WHITE_BISHOP, WHITE_PAWN, NO_STONE, NO_STONE, NO_STONE, NO_STONE, BLACK_PAWN, BLACK_BISHOP,
            WHITE_KNIGHT, WHITE_PAWN, NO_STONE, NO_STONE, NO_STONE, NO_STONE, BLACK_PAWN, BLACK_KNIGHT,
            WHITE_ROOK,   WHITE_PAWN, NO_STONE, NO_STONE, NO_STONE, NO_STONE, BLACK_PAWN, BLACK_ROOK
    };

    private static Stone[] emptyBoard;

    static private long getNonzeroLong(Random r) {
        long v;
        do {
            long v1 = r.nextLong(), v2 = r.nextLong();
            v = (v1 << 32) + v2;
        } while (v == 0);
        return v;
    }

    static {
        Random r = new Random(0);
        for (int i = 0; i < 64; i++)
            for (int c = 0; c < 13; c++)
                zobristKey[c][i] = getNonzeroLong(r);
        for (int i = 0; i < 16; i++) zobristKeyCastle[i] = getNonzeroLong(r);
        for (int i = 0; i < 9; i++) zobristKeyEnPassant[i] = getNonzeroLong(r);
        for (int i = 0; i < 2; i++) zobristKeyToMove[i] = getNonzeroLong(r);

        startPosition = new Position(startBoard, WHITE, EnumSet.allOf(Castles.class), Chess.NO_COL);
        emptyBoard = new Stone[64];
        for (int i = 0; i < 64; i++) {
            emptyBoard[i] = NO_STONE;
        }
    }

    private final Stone[] board; // This array must not be modified nor exposed
    private final Player toMove;
    private final int enPassantCol; // Chess.NO_COL if EP not possible;
    private final EnumSet<Castles> castlesSet; // This set must not be modified nor exposed
    private int whiteKingSqi, blackKingSqi;
    private long hash; // Cached hash value of the position
    private Boolean isCheck, canCaptureKing; // cached values
    private List<Move> allLegalMoves; // cached values

    public Stone stoneAt(int sqi) {
        if (sqi < 0 || sqi > 63)
            throw new IllegalArgumentException("sqi must be between 0 and 63, inclusive.");
        return board[sqi];
    }

    public Stone stoneAt(int col, int row) {
        if (row < 0 || col < 0 || row >= 8 || col >= 8)
            throw new IllegalArgumentException("row and col must be between 0 and 7, inclusive.");
        return board[Chess.coorToSqi(col, row)];
    }

    public Player playerToMove() {
        return toMove;
    }

    public int getEnPassantCol() {
        return enPassantCol;
    }

    /**
     * Check if it's still possible to castle in some direction
     */
    public boolean isCastles(Castles castles) {
        return castlesSet.contains(castles);
    }

    public static Position start() {
        return startPosition;
    }

    /**
     * Creates a new position
     * @param board an 64-element array of the stones on the board
     * @param playerToMove the player to move
     * @param castles castle rights
     * @param epFile the file which an en-passant is possible; -1 if no file
     */
    public Position(Stone[] board, Player playerToMove, EnumSet<Castles> castles, int epFile) {
        this.board = board.clone();
        locateKings();
        this.toMove = playerToMove;
        this.castlesSet = castles.clone();
        if (!(whiteKingSqi == E1 && this.board[H1] == WHITE_ROOK)) this.castlesSet.remove(WHITE_SHORT_CASTLE);
        if (!(whiteKingSqi == E1 && this.board[A1] == WHITE_ROOK)) this.castlesSet.remove(WHITE_LONG_CASTLE);
        if (!(blackKingSqi == E8 && this.board[H8] == BLACK_ROOK)) this.castlesSet.remove(BLACK_SHORT_CASTLE);
        if (!(blackKingSqi == E8 && this.board[A8] == BLACK_ROOK)) this.castlesSet.remove(BLACK_LONG_CASTLE);
        this.enPassantCol = epFile;
    }

    public static Position fromString(String boardStr, Player playerToMove) {
        return fromString(boardStr, playerToMove, EnumSet.allOf(Castles.class), NO_COL);
    }

    public static Position fromString(String boardStr, Player playerToMove, EnumSet<Castles> castles, int epFile) {
        Stone[] board = emptyBoard.clone();
        boardStr = boardStr.trim();
        int x = 0, y = 7;
        for (int i = 0; i < boardStr.length() && y >= 0; i++) {
            char c = boardStr.charAt(i);
            if (c == '.') {
                x++;
            } if (c == '\n') {
                x = 0;
                y--;
            } else {
                Stone stone = Stone.fromChar(c);
                if (stone != null && stone != Stone.NO_STONE) {
                    board[Chess.coorToSqi(x++, y)] = stone;
                }
            }
        }

        return new Position(board, playerToMove, castles, epFile);
    }

    private void locateKings() {
        for (int i = 0; i < 64; i++) {
            if (this.board[i] == WHITE_KING) this.whiteKingSqi = i;
            if (this.board[i] == BLACK_KING) this.blackKingSqi = i;
        }
    }

    public String toString() {
        return toString("\n");
    }

    public String toString(String rowDelimeter) {
        StringBuilder sb = new StringBuilder(64 + 8);
        for (int row = 7; row >= 0; row--) {
            for (int col = 0; col < 8; col++) {
                char c = stoneAt(col, row).toChar();
                sb.append(c == ' ' ? '.' : c);
            }
            sb.append(rowDelimeter);
        }
        return sb.toString();
    }

    /**
     * @return true if the player to move is being checked (possible check mate)
     */
    public boolean isCheck() {
        if (this.isCheck == null) {
            if (toMove == WHITE) {
                this.isCheck = isAttacked(whiteKingSqi, BLACK);
            } else {
                this.isCheck = isAttacked(blackKingSqi, WHITE);
            }
        }
        return this.isCheck;
    }

    /**
     * @return true if the player to move is check mate
     */
    public boolean isMate() {
        return isCheck() && !canMove();
    }

    /**
     * @return true if the player to move is stale mate
     */
    public boolean isStaleMate() {
        return !isCheck() && !canMove();
    }

    /**
     * @return true if the player to move can capture the opponents king
     */
    public boolean canCaptureKing() {
        if (this.canCaptureKing == null) {
            this.canCaptureKing = isAttacked(toMove == WHITE ? blackKingSqi : whiteKingSqi, toMove);
        }
        return this.canCaptureKing;
    }

    /**
     * Checks if the specified square is attacked by any piece of the given color
     * @return true if the square is attacked
     */
    public boolean isAttacked(int sqi, Player attackColor) {
        // Check pawn attack
        int x = Chess.sqiToCol(sqi), y = Chess.sqiToRow(sqi);
        int dy = attackColor == WHITE ? 1 : -1;
        if (x > 0 && y - dy >= 0 && y - dy < 8 && stoneAt(x-1, y-dy) == PAWN.toStone(attackColor))
            return true;
        if (x < 7 && y - dy >= 0 && y - dy < 8 && stoneAt(x+1, y-dy) == PAWN.toStone(attackColor))
            return true;

        // Check attack in the 16 directions
        for (int dir = 0; dir < 16; dir++) {
            int cx = x, cy = y;
            boolean multi = false;
            while (true) {
                if (dir >= 8 && multi) break;
                cx += directionX[dir];
                cy += directionY[dir];
                if (cx < 0 || cy < 0 || cx >= 8 || cy >= 8) break;
                if (stoneAt(cx, cy).hasPlayer(attackColor.otherPlayer())) break;
                if (stoneAt(cx, cy).hasPlayer(attackColor)) {
                    switch (stoneAt(cx, cy).toPiece()) {
                        case BISHOP:
                            if (dir >= 4 && dir < 8) return true;
                            break;
                        case ROOK:
                            if (dir < 4) return true;
                            break;
                        case QUEEN:
                            if (dir < 8) return true;
                            break;
                        case KNIGHT:
                            if (dir >= 8) return true;
                            break;
                        case KING:
                            if (dir < 8 && !multi) return true;
                            break;
                    }
                    break;
                }
                multi = true;
            }
        }
        return false;
    }

    private void addPawnMove(int x1, int y1, int x2, int y2, List<Move> moveList) {
        if (y2 > 0 && y2 < 7)
            moveList.add(new Move(this, x1, y1, x2, y2));
        else {
            Player player = y1 < y2 ? WHITE : BLACK;
            moveList.add(new Move(this, x1, y1, x2, y2, QUEEN.toStone(player)));
            moveList.add(new Move(this, x1, y1, x2, y2, KNIGHT.toStone(player)));
            moveList.add(new Move(this, x1, y1, x2, y2, ROOK.toStone(player)));
            moveList.add(new Move(this, x1, y1, x2, y2, BISHOP.toStone(player)));
        }
    }

    private static int[] directionX = new int[]{0, 1, 0, -1, 1, 1, -1, -1, 1, 2, 2, 1, -1, -2, -2, -1};
    private static int[] directionY = new int[]{1, 0, -1, 0, 1, -1, -1, 1, 2, 1, -1, -2, -2, -1, 1, 2};

    private List<Move> genericMoves(int sqi, int dirBegin, int dirEnd, boolean multi) {
        if (!stoneAt(sqi).hasPlayer(toMove)) {
            throw new IllegalArgumentException("No piece at " + Chess.sqiToStr(sqi) + " with color " + toMove);
        }
        int x = Chess.sqiToCol(sqi), y = Chess.sqiToRow(sqi);
        ArrayList<Move> moveList = new ArrayList<>();
        for (int dir = dirBegin; dir < dirEnd; dir++) {
            int cx = x, cy = y;
            while (true) {
                cx += directionX[dir];
                cy += directionY[dir];
                if (cx < 0 || cy < 0 || cx >= 8 || cy >= 8) break;
                Player c = stoneAt(cx, cy).toPlayer();
                if (c == toMove) break;
                moveList.add(new Move(this, x, y, cx, cy));
                if (!multi || c != NOBODY) break;
            }
        }
        return moveList;
    }

    /**
     * Generates all pawn moves for the pawn at square x,y
     */
    public List<Move> generatePawnMoves(int sqi) {
        if (stoneAt(sqi) != PAWN.toStone(toMove)) {
            throw new IllegalArgumentException("There is no pawn of color " + toMove + " at the square " + Chess.sqiToStr(sqi));
        }
        ArrayList<Move> moveList = new ArrayList<>();
        int x = Chess.sqiToCol(sqi), y = Chess.sqiToRow(sqi);
        int dy = toMove == WHITE ? 1 : -1;
        if (x > 0 && stoneAt(x-1, y+dy).hasPlayer(toMove.otherPlayer())) {
            addPawnMove(x, y, x - 1, y + dy, moveList);
        }
        if (x < 7 && stoneAt(x+1, y+dy).hasPlayer(toMove.otherPlayer())) {
            addPawnMove(x, y, x + 1, y + dy, moveList);
        }
        if (enPassantCol >= 0 && Math.abs(enPassantCol - x) == 1 &&
                ((y == 4 && dy == 1) || (y == 3 && dy == -1))) {
            addPawnMove(x, y, enPassantCol, y + dy, moveList);
        }
        if (stoneAt(x, y+dy).isNoStone()) {
            if ((y - dy == 0 || y - dy == 7) && stoneAt(x, y + dy * 2).isNoStone()) {
                addPawnMove(x, y, x, y + dy * 2, moveList);
            }
            addPawnMove(x, y, x, y + dy, moveList);
        }
        return moveList;
    }

    List<Move> generateKnightMoves(int sqi) {
        return genericMoves(sqi, 8, 16, false);
    }

    List<Move> generateBishopMoves(int sqi) {
        return genericMoves(sqi, 4, 8, true);
    }

    List<Move> generateRookMoves(int sqi) {
        return genericMoves(sqi, 0, 4, true);
    }

    List<Move> generateQueenMoves(int sqi) {
        return genericMoves(sqi, 0, 8, true);
    }

    List<Move> generateKingMoves(int sqi) {
        if (stoneAt(sqi) != KING.toStone(toMove)) {
            throw new IllegalArgumentException("There is no king of color " + toMove + " at the square " + Chess.sqiToStr(sqi));
        }
        List<Move> moveList = genericMoves(sqi, 0, 8, false);
        int x = Chess.sqiToCol(sqi), y = Chess.sqiToRow(sqi);
        if (x == 4) {
            if ((castlesSet.contains(WHITE_SHORT_CASTLE) && toMove == WHITE) || (castlesSet.contains(BLACK_SHORT_CASTLE) && toMove == BLACK)) {
                if (stoneAt(5, y).isNoStone() && stoneAt(6, y).isNoStone() && !isCheck() &&
                        !isAttacked(Chess.coorToSqi(5, y), toMove.otherPlayer())) {
                    moveList.add(new Move(this, x, y, x + 2, y));
                }
            }
            if ((castlesSet.contains(WHITE_LONG_CASTLE) && toMove == WHITE) || (castlesSet.contains(BLACK_LONG_CASTLE) && toMove == BLACK)) {
                if (stoneAt(3, y).isNoStone() && stoneAt(2, y).isNoStone() && stoneAt(1, y).isNoStone() &&
                        !isCheck() && !isAttacked(Chess.coorToSqi(3, y), toMove.otherPlayer())) {
                    moveList.add(new Move(this, x, y, x - 2, y));
                }
            }
        }
        return moveList;
    }

    /**
     * Performs a move and returns the new position. This assumes that the move is pseudolegal.
     * The resulting position may thus be a board where the king can be captured in the next move.
     *
     * @param fromSqi from square
     * @param toSqi to square
     * @return the position after the move has been made
     */
    public Position doMove(int fromSqi, int toSqi) {
        return doMove(new ShortMove(fromSqi, toSqi));
    }

    /**
     * Performs a move and returns the new position. This assumes that the move is pseudolegal.
     * The resulting position may thus be a board where the king can be captured in the next move.
     *
     * @param move the move
     * @return the position after the move has been made
     */
    public Position doMove(ShortMove move) {
        if (move.isNullMove()) {
            return new Position(board, playerToMove().otherPlayer(), castlesSet, NO_COL);
        }

        Stone[] newBoard = board.clone();

        Piece movingPiece = stoneAt(move.fromSqi()).toPiece();

        if (movingPiece == KING && Math.abs(Chess.deltaCol(move.fromSqi(), move.toSqi())) == 2) {
            // Castles; update the position of the rook
            int sqiBetween = (move.toSqi() + move.fromSqi()) / 2;
            newBoard[sqiBetween] = Piece.ROOK.toStone(toMove);
            switch (sqiBetween) {
                case F1 : newBoard[H1] = NO_STONE; break;
                case D1 : newBoard[A1] = NO_STONE; break;
                case F8 : newBoard[H8] = NO_STONE; break;
                case D8 : newBoard[A8] = NO_STONE; break;
            }
        }

        if (movingPiece == PAWN && Chess.deltaCol(move.fromSqi(), move.toSqi()) != 0
                && board[move.toSqi()] == NO_STONE) {
            // En passant
            newBoard[coorToSqi(move.toCol(), move.fromRow())] = NO_STONE;
        }

        newBoard[move.toSqi()] = newBoard[move.fromSqi()];
        newBoard[move.fromSqi()] = NO_STONE;

        if (movingPiece == PAWN && (move.toRow() == 0 || move.toRow() == 7)) {
            Stone promotionStone = move.promotionStone();
            if (promotionStone == NO_STONE) {
                promotionStone = toMove == WHITE ? WHITE_QUEEN : BLACK_QUEEN;
            }
            newBoard[move.toSqi()] = promotionStone;
        }

        EnumSet<Castles> newCastlesSet = this.castlesSet.clone();

        if (movingPiece == KING) {
            if (toMove == WHITE) {
                newCastlesSet.remove(WHITE_SHORT_CASTLE);
                newCastlesSet.remove(WHITE_LONG_CASTLE);
            } else {
                newCastlesSet.remove(BLACK_SHORT_CASTLE);
                newCastlesSet.remove(BLACK_LONG_CASTLE);
            }
        }

        if (movingPiece == ROOK) {
            if (move.fromSqi() == A1) newCastlesSet.remove(WHITE_LONG_CASTLE);
            if (move.fromSqi() == H1) newCastlesSet.remove(WHITE_SHORT_CASTLE);
            if (move.fromSqi() == A8) newCastlesSet.remove(BLACK_LONG_CASTLE);
            if (move.fromSqi() == H8) newCastlesSet.remove(BLACK_SHORT_CASTLE);
        }

        int enPassantFile = NO_COL;
        if (movingPiece == PAWN && Math.abs(Chess.deltaRow(move.fromSqi(), move.toSqi())) == 2) {
            enPassantFile = move.fromCol();
        }

        return new Position(newBoard, toMove.otherPlayer(), newCastlesSet, enPassantFile);
    }

    public List<Move> generateAllPseudoLegalMoves() {
        ArrayList<Move> moves = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            if (board[i].hasPlayer(toMove)) {
                switch (board[i].toPiece()) {
                    case PAWN:
                        moves.addAll(generatePawnMoves(i));
                        break;
                    case KNIGHT:
                        moves.addAll(generateKnightMoves(i));
                        break;
                    case BISHOP:
                        moves.addAll(generateBishopMoves(i));
                        break;
                    case ROOK:
                        moves.addAll(generateRookMoves(i));
                        break;
                    case QUEEN:
                        moves.addAll(generateQueenMoves(i));
                        break;
                    case KING:
                        moves.addAll(generateKingMoves(i));
                        break;
                }
            }
        }
        return moves;
    }

    public boolean canMove() {
        return generateAllLegalMoves().size() > 0;
    }

    public List<Move> generateAllLegalMoves() {
        if (allLegalMoves == null) {
            allLegalMoves = generateAllPseudoLegalMoves()
                    .stream()
                    .filter(move -> !doMove(move).canCaptureKing())
                    .collect(Collectors.toList());
        }
        return Collections.unmodifiableList(allLegalMoves);
    }

    public boolean isMoveLegal(int fromSqi, int toSqi) {
        return isMoveLegal(new ShortMove(fromSqi, toSqi));
    }

    public boolean isMoveLegal(ShortMove move) {
        if (move.isNullMove()) {
            return !isCheck();
        }
        if (!board[move.fromSqi()].hasPlayer(toMove)) {
            return false;
        }
        // This could be made more efficient
        boolean pseudoLegal = false;
        switch (board[move.fromSqi()].toPiece()) {
            case PAWN:
                pseudoLegal = generatePawnMoves(move.fromSqi()).contains(move);
                break;
            case KNIGHT:
                pseudoLegal = generateKnightMoves(move.fromSqi()).contains(move);
                break;
            case BISHOP:
                pseudoLegal = generateBishopMoves(move.fromSqi()).contains(move);
                break;
            case ROOK:
                pseudoLegal = generateRookMoves(move.fromSqi()).contains(move);
                break;
            case QUEEN:
                pseudoLegal = generateQueenMoves(move.fromSqi()).contains(move);
                break;
            case KING:
                pseudoLegal = generateKingMoves(move.fromSqi()).contains(move);
                break;
        }
        if (!pseudoLegal) {
            return false;
        }
        return !doMove(move).canCaptureKing();
    }

    public boolean equals(Object obj) {
        // Assume that two boards with the same zobrist key (64 bit) are identical
        if (obj instanceof Position)
            return this.getZobristHash() == ((Position) obj).getZobristHash();
        return super.equals(obj);
    }

    public long getZobristHash() {
        if (hash == 0) {
            for (int i = 0; i < 64; i++) {
                hash ^= zobristKey[board[i].ordinal()][i];
            }
            int castleStatus = 0;
            if (castlesSet.contains(WHITE_SHORT_CASTLE)) castleStatus += 1;
            if (castlesSet.contains(WHITE_LONG_CASTLE)) castleStatus += 2;
            if (castlesSet.contains(BLACK_SHORT_CASTLE)) castleStatus += 4;
            if (castlesSet.contains(BLACK_LONG_CASTLE)) castleStatus += 8;
            hash ^= zobristKeyCastle[castleStatus];
            hash ^= zobristKeyEnPassant[enPassantCol + 1];
            hash ^= zobristKeyToMove[toMove == WHITE ? 1 : 0];
        }
        return hash;
    }

    public int hashCode() {
        return (int) getZobristHash();
    }
}
