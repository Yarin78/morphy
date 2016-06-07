package yarin.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Represents the current state of the chess board
 */
public class Board {

    private static long[][][] zobristKey = new long[13][8][8];
    private static long[] zobristKeyCastle = new long[16];
    private static long[] zobristKeyToMove = new long[2];
    private static long[] zobristKeyEnPassant = new long[9];

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
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++)
                for (int c = 0; c < 13; c++)
                    zobristKey[c][y][x] = getNonzeroLong(r);
        for (int i = 0; i < 16; i++) zobristKeyCastle[i] = getNonzeroLong(r);
        for (int i = 0; i < 9; i++) zobristKeyEnPassant[i] = getNonzeroLong(r);
        for (int i = 0; i < 2; i++) zobristKeyToMove[i] = getNonzeroLong(r);
    }

    private Piece[][] board;
    private Piece.PieceColor toMove;
    private int enPassantFile; // -1 if EP not possible;
    private boolean whiteKingCastle, whiteQueenCastle;
    private boolean blackKingCastle, blackQueenCastle;
    private int whiteKingX, whiteKingY;
    private int blackKingX, blackKingY;

    public Piece pieceAt(int rank, int file) {
        if (rank < 0 || file < 0 || rank >= 8 || file >= 8)
            throw new IllegalArgumentException("file and rank must be between 0 and 7, inclusive.");
        return board[rank][file];
    }

    public Piece.PieceColor getToMove() {
        return toMove;
    }

    Piece.PieceColor getOpponent(Piece.PieceColor c) {
        switch (c) {
            case WHITE:
                return Piece.PieceColor.BLACK;
            case BLACK:
                return Piece.PieceColor.WHITE;
        }
        return c;
    }

    private static String initialPositionFEN = "RNBQKBNR/PPPPPPPP/8/8/8/8/pppppppp/rnbqkbnr";

    public Board() {
        board = new Piece[8][8];
        setupBoard(initialPositionFEN, Piece.PieceColor.WHITE, true, true, true, true, -1);
    }

    public Board(Board b) {
        this.board = new Piece[8][8];
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++)
                this.board[y][x] = b.board[y][x];
        this.toMove = b.toMove;
        this.enPassantFile = b.enPassantFile;
        this.whiteKingCastle = b.whiteKingCastle;
        this.blackKingCastle = b.blackKingCastle;
        this.whiteQueenCastle = b.whiteQueenCastle;
        this.blackQueenCastle = b.blackQueenCastle;
        this.whiteKingX = b.whiteKingX;
        this.whiteKingY = b.whiteKingY;
        this.blackKingX = b.blackKingX;
        this.blackKingY = b.blackKingY;
    }

    public Board(String position, Piece.PieceColor toMove, boolean whiteKingCastle, boolean whiteQueenCastle, boolean blackKingCastle, boolean blackQueenCastle, int epFile) {
        board = new Piece[8][8];
        setupBoard(position, toMove, whiteKingCastle, whiteQueenCastle, blackKingCastle, blackQueenCastle, epFile);
    }

    public void setup(Piece[][] squares, Piece.PieceColor toMove, boolean whiteKingCastle, boolean whiteQueenCastle, boolean blackKingCastle, boolean blackQueenCastle, int epFile) {
        this.board = new Piece[8][8];
        this.whiteKingX = -1;
        this.whiteKingY = -1;
        this.blackKingX = -1;
        this.blackKingY = -1;
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++) {
                this.board[y][x] = squares[y][x];
                if (squares[y][x].getPiece() == Piece.PieceType.KING) {
                    if (squares[y][x].getColor() == Piece.PieceColor.WHITE) {
                        if (this.whiteKingX >= 0) {
                            throw new RuntimeException("Two white kings is not allowed");
                        }
                        this.whiteKingX = x;
                        this.whiteKingY = y;
                    } else if (squares[y][x].getColor() == Piece.PieceColor.BLACK) {
                        if (this.blackKingX >= 0) {
                            throw new RuntimeException("Two black kings is not allowed");
                        }
                        this.blackKingX = x;
                        this.blackKingY = y;
                    }
                }
            }
        this.toMove = toMove;
        this.enPassantFile = epFile;
        this.whiteKingCastle = whiteKingCastle;
        this.blackKingCastle = blackKingCastle;
        this.whiteQueenCastle = whiteQueenCastle;
        this.blackQueenCastle = blackQueenCastle;

        if (this.whiteKingX < 0) {
            throw new RuntimeException("No white king on the board");
        }
        if (this.blackKingX < 0) {
            throw new RuntimeException("No black king on the board");
        }
        // TODO: Validate the position (e.g. side to move can't capture the king)
    }

    public String toString() {
        String s = "";
        for (int y = 0; y < 8; y++) {
            if (y > 0) s += "/";
            int cnt = 0;
            for (int x = 0; x < 8; x++) {
                char c = '.';
                switch (this.board[y][x].getPiece()) {
                    case PAWN:
                        c = 'p';
                        break;
                    case KNIGHT:
                        c = 'n';
                        break;
                    case BISHOP:
                        c = 'b';
                        break;
                    case ROOK:
                        c = 'r';
                        break;
                    case QUEEN:
                        c = 'q';
                        break;
                    case KING:
                        c = 'k';
                        break;
                }
                if (c == '.')
                    cnt++;
                else {
                    if (cnt > 0)
                        s += Integer.toString(cnt);
                    cnt = 0;
                    if (this.board[y][x].getColor() == Piece.PieceColor.WHITE)
                        s += Character.toUpperCase(c);
                    else
                        s += c;
                }
            }
            if (cnt > 0)
                s += Integer.toString(cnt);
        }
        if (whiteKingCastle) s += " WK";
        if (whiteQueenCastle) s += " WQ";
        if (blackKingCastle) s += " BK";
        if (blackQueenCastle) s += " BQ";
        if (enPassantFile >= 0) s += " ep" + (char) (enPassantFile + 'A');
        return s;
    }

    /**
     * Setup the board
     *
     * @param position the position in FEN notation
     * @param toMove   the player to move
     * @param wk       can white castle king side?
     * @param wq       can white castle queen side?
     * @param bk       can black castle king side?
     * @param bq       can black castle queen side?
     * @param epFile   en passant file (-1 if nothing)
     * @return true if the input was valid; otherwise false
     */
    private boolean setupBoard(String position, Piece.PieceColor toMove, boolean wk, boolean wq, boolean bk, boolean bq, int epFile) {
        if (epFile < -1 || epFile > 8) return false;

        this.whiteKingCastle = wk;
        this.whiteQueenCastle = wq;
        this.blackKingCastle = bk;
        this.blackQueenCastle = bq;
        this.enPassantFile = epFile;
        this.toMove = toMove;
        this.whiteKingX = this.blackKingX = -1;

        int n = 0;
        for (char c : position.toCharArray()) {
            if (Character.isDigit(c)) {
                int m = Integer.parseInt("" + c);
                for (int i = 0; i < m; i++) {
                    if (n >= 64) return false;
                    this.board[n / 8][n % 8] = new Piece();
                    n++;
                }
            } else {
                Piece.PieceType piece;
                Piece.PieceColor color;
                color = Character.isUpperCase(c) ? Piece.PieceColor.WHITE : Piece.PieceColor.BLACK;
                piece = Piece.PieceType.EMPTY;
                switch (Character.toUpperCase(c)) {
                    case 'P':
                        piece = Piece.PieceType.PAWN;
                        break;
                    case 'N':
                        piece = Piece.PieceType.KNIGHT;
                        break;
                    case 'B':
                        piece = Piece.PieceType.BISHOP;
                        break;
                    case 'R':
                        piece = Piece.PieceType.ROOK;
                        break;
                    case 'Q':
                        piece = Piece.PieceType.QUEEN;
                        break;
                    case 'K':
                        piece = Piece.PieceType.KING;
                        break;
                    case '/':
                        continue;
                }
                Piece p = new Piece(piece, color);
                if (p.getPiece() == Piece.PieceType.EMPTY)
                    return false;
                if (p.getPiece() == Piece.PieceType.KING) {
                    if (p.getColor() == Piece.PieceColor.WHITE) {
                        if (whiteKingX < 0) {
                            whiteKingX = n % 8;
                            whiteKingY = n / 8;
                        } else
                            return false;
                    }
                    if (p.getColor() == Piece.PieceColor.BLACK) {
                        if (blackKingX < 0) {
                            blackKingX = n % 8;
                            blackKingY = n / 8;
                        } else
                            return false;
                    }
                }
                if (n >= 64) return false;
                this.board[n / 8][n % 8] = p;
                n++;
            }
        }
        if (n != 64 || whiteKingX < 0 || blackKingX < 0) return false;

        // Make sure castling flags are correct
        if (whiteKingX != 4 || whiteKingY != 0)
            whiteKingCastle = whiteQueenCastle = false;
        if (blackKingX != 4 || blackKingY != 7)
            blackKingCastle = blackQueenCastle = false;
        if (board[0][7].getColor() != Piece.PieceColor.WHITE || board[0][7].getPiece() != Piece.PieceType.ROOK)
            whiteKingCastle = false;
        if (board[0][0].getColor() != Piece.PieceColor.WHITE || board[0][0].getPiece() != Piece.PieceType.ROOK)
            whiteQueenCastle = false;
        if (board[7][7].getColor() != Piece.PieceColor.BLACK || board[7][7].getPiece() != Piece.PieceType.ROOK)
            blackKingCastle = false;
        if (board[7][0].getColor() != Piece.PieceColor.BLACK || board[7][0].getPiece() != Piece.PieceType.ROOK)
            blackQueenCastle = false;

        // Make sure en passant flag is correct
        if (enPassantFile >= 0) {
            if (toMove == Piece.PieceColor.WHITE && board[4][enPassantFile] != new Piece(Piece.PieceType.PAWN, Piece.PieceColor.BLACK))
                enPassantFile = -1;
            if (toMove == Piece.PieceColor.BLACK && board[5][enPassantFile] != new Piece(Piece.PieceType.PAWN, Piece.PieceColor.WHITE))
                enPassantFile = -1;
        }
        return true;
    }

    /**
     * @return true if the player to move is being checked
     */
    public boolean isCheck() {
        // TODO: Add caching
        if (toMove == Piece.PieceColor.WHITE)
            return isAttack(whiteKingX, whiteKingY, getOpponent(toMove));
        return isAttack(blackKingX, blackKingY, getOpponent(toMove));
    }

    /**
     * @return true if the player to move can capture the opponents king
     */
    public boolean canCaptureKing() {
        // TODO: Add caching
        if (toMove == Piece.PieceColor.BLACK)
            return isAttack(whiteKingX, whiteKingY, toMove);
        return isAttack(blackKingX, blackKingY, toMove);
    }

    /**
     * @return true if the square x,y is attacked by a piece of the given color
     */
    public boolean isAttack(int x, int y, Piece.PieceColor attackColor) {
        // Check pawn attack
        int dy = attackColor == Piece.PieceColor.WHITE ? 1 : -1;
        if (x > 0 && y - dy >= 0 && y - dy < 8 && board[y - dy][x - 1].equals(new Piece(Piece.PieceType.PAWN, attackColor)))
            return true;
        if (x < 7 && y - dy >= 0 && y - dy < 8 && board[y - dy][x + 1].equals(new Piece(Piece.PieceType.PAWN, attackColor)))
            return true;

        // Check attack in the 16 directions
        for (int dir = 0; dir < 16; dir++) {
            int curx = x, cury = y;
            boolean multi = false;
            while (true) {
                if (dir >= 8 && multi) break;
                curx += directionX[dir];
                cury += directionY[dir];
                if (curx < 0 || cury < 0 || curx >= 8 || cury >= 8) break;
                if (board[cury][curx].getColor() == getOpponent(attackColor)) break;
                if (board[cury][curx].getColor() == attackColor) {
                    switch (board[cury][curx].getPiece()) {
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

    private void addPawnMove(int x1, int y1, int x2, int y2, List<Move> mvList, boolean enpassant) {
        if (y2 > 0 && y2 < 7)
            mvList.add(new Move(Piece.PieceType.PAWN, x1, y1, x2, y2, x1 != x2, enpassant, Piece.PieceType.EMPTY));
        else {
            mvList.add(new Move(Piece.PieceType.PAWN, x1, y1, x2, y2, x1 != x2, false, Piece.PieceType.QUEEN));
            mvList.add(new Move(Piece.PieceType.PAWN, x1, y1, x2, y2, x1 != x2, false, Piece.PieceType.KNIGHT));
            mvList.add(new Move(Piece.PieceType.PAWN, x1, y1, x2, y2, x1 != x2, false, Piece.PieceType.ROOK));
            mvList.add(new Move(Piece.PieceType.PAWN, x1, y1, x2, y2, x1 != x2, false, Piece.PieceType.BISHOP));
        }
    }

    private static int[] directionX = new int[]{0, 1, 0, -1, 1, 1, -1, -1, 1, 2, 2, 1, -1, -2, -2, -1};
    private static int[] directionY = new int[]{1, 0, -1, 0, 1, -1, -1, 1, 2, 1, -1, -2, -2, -1, 1, 2};

    private List<Move> genericMoves(int x, int y, int dirBegin, int dirEnd, boolean multi) {
        ArrayList<Move> mvList = new ArrayList<>();
        if (board[y][x].getColor() != toMove)
            return mvList;
        Piece.PieceColor color = board[y][x].getColor();
        Piece.PieceType piece = board[y][x].getPiece();
        for (int dir = dirBegin; dir < dirEnd; dir++) {
            int curx = x, cury = y;
            while (true) {
                curx += directionX[dir];
                cury += directionY[dir];
                if (curx < 0 || cury < 0 || curx >= 8 || cury >= 8) break;
                Piece.PieceColor c = board[cury][curx].getColor();
                if (c == color) break;
                mvList.add(new Move(piece, x, y, curx, cury, c != Piece.PieceColor.EMPTY));
                if (!multi || c != Piece.PieceColor.EMPTY) break;
            }
        }
        return mvList;
    }

    /**
     * @return all pseudomoves for a pawn at square x,y
     */
    public List<Move> pawnMoves(int x, int y) {
        ArrayList<Move> mvList = new ArrayList<>();
        if (board[y][x].getColor() != toMove) return mvList;
        int dy = toMove == Piece.PieceColor.WHITE ? 1 : -1;
        if (x > 0 && board[y + dy][x - 1].getColor() == getOpponent(toMove))
            addPawnMove(x, y, x - 1, y + dy, mvList, false);
        if (x < 7 && board[y + dy][x + 1].getColor() == getOpponent(toMove))
            addPawnMove(x, y, x + 1, y + dy, mvList, false);
        if (enPassantFile >= 0 && Math.abs(enPassantFile - x) == 1 && ((y == 4 && dy == 1) || (y == 3 && dy == -1)))
            addPawnMove(x, y, enPassantFile, y + dy, mvList, true);
        if (board[y + dy][x].isEmpty()) {
            if ((y - dy == 0 || y - dy == 7) && board[y + dy * 2][x].isEmpty())
                addPawnMove(x, y, x, y + dy * 2, mvList, false);
            addPawnMove(x, y, x, y + dy, mvList, false);
        }
        return mvList;
    }

    public List<Move> knightMoves(int x, int y) {
        return genericMoves(x, y, 8, 16, false);
    }

    public List<Move> bishopMoves(int x, int y) {
        return genericMoves(x, y, 4, 8, true);
    }

    public List<Move> rookMoves(int x, int y) {
        return genericMoves(x, y, 0, 4, true);
    }

    public List<Move> queenMoves(int x, int y) {
        return genericMoves(x, y, 0, 8, true);
    }

    public List<Move> kingMoves(int x, int y) {
        List<Move> mvList = genericMoves(x, y, 0, 8, false);
        if (board[y][x].getColor() != toMove) return mvList;
        if (x == 4) {
            if ((whiteKingCastle && toMove == Piece.PieceColor.WHITE) || (blackKingCastle && toMove == Piece.PieceColor.BLACK))
                if (board[y][5].isEmpty() && board[y][6].isEmpty() && !isCheck() && !isAttack(5, y, getOpponent(toMove)))
                    mvList.add(new Move(Piece.PieceType.KING, x, y, x + 2, y, false));
            if ((whiteQueenCastle && toMove == Piece.PieceColor.WHITE) || (blackQueenCastle && toMove == Piece.PieceColor.BLACK))
                if (board[y][3].isEmpty() && board[y][2].isEmpty() && board[y][1].isEmpty() && !isCheck() && !isAttack(3, y, getOpponent(toMove)))
                    mvList.add(new Move(Piece.PieceType.KING, x, y, x - 2, y, false));
        }
        return mvList;
    }

    /**
     * Performs a move and returns a new board object. Assumes that the move is pseudolegal.
     * The resulting position may thus be a board where the king can be captured in the next move.
     *
     * @param move the move
     * @return the position after the move has been made
     */
    public Board doMove(Move move) {
        Board b = new Board(this);

        if (move.getPiece() != Piece.PieceType.EMPTY) // Check that move is not null move
        {
            if (move.isCastle()) {
                if (move.getX2() > move.getX1()) {
                    b.board[move.getY1()][5] = b.board[move.getY1()][7];
                    b.board[move.getY1()][7] = new Piece();
                } else {
                    b.board[move.getY1()][3] = b.board[move.getY1()][0];
                    b.board[move.getY1()][0] = new Piece();
                }
            }
            if (move.isCapture() && b.board[move.getY2()][move.getX2()].isEmpty())
                b.board[move.getY1()][move.getX2()] = new Piece(); // En passant

            b.board[move.getY2()][move.getX2()] = b.board[move.getY1()][move.getX1()];
            b.board[move.getY1()][move.getX1()] = new Piece();
            if (move.getPromotionPiece() != Piece.PieceType.EMPTY)
                b.board[move.getY2()][move.getX2()] = new Piece(move.getPromotionPiece(), b.toMove);

            if (move.getPiece() == Piece.PieceType.KING) {
                if (b.toMove == Piece.PieceColor.WHITE) {
                    b.whiteKingCastle = b.whiteQueenCastle = false;
                    b.whiteKingX = move.getX2();
                    b.whiteKingY = move.getY2();
                } else {
                    b.blackKingCastle = b.blackQueenCastle = false;
                    b.blackKingX = move.getX2();
                    b.blackKingY = move.getY2();
                }
            }
            if (move.getPiece() == Piece.PieceType.ROOK) {
                if (move.getY1() == 0 && move.getX1() == 0) b.whiteQueenCastle = false;
                if (move.getY1() == 0 && move.getX1() == 7) b.whiteKingCastle = false;
                if (move.getY1() == 7 && move.getX1() == 0) b.blackQueenCastle = false;
                if (move.getY1() == 7 && move.getX1() == 7) b.blackKingCastle = false;
            }
            if (move.getPiece() == Piece.PieceType.PAWN && Math.abs(move.getY1() - move.getY2()) == 2)
                b.enPassantFile = move.getX1();
            else
                b.enPassantFile = -1;
        }

        b.toMove = getOpponent(b.toMove);

        return b;
    }

    public List<Move> getPseudoLegalMoves() {
        ArrayList<Move> moves = new ArrayList<>();
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++)
                if (board[y][x].getColor() == toMove) {
                    List<Move> mvList = null;
                    switch (board[y][x].getPiece()) {
                        case PAWN:
                            mvList = pawnMoves(x, y);
                            break;
                        case KNIGHT:
                            mvList = knightMoves(x, y);
                            break;
                        case BISHOP:
                            mvList = bishopMoves(x, y);
                            break;
                        case ROOK:
                            mvList = rookMoves(x, y);
                            break;
                        case QUEEN:
                            mvList = queenMoves(x, y);
                            break;
                        case KING:
                            mvList = kingMoves(x, y);
                            break;
                    }
                    if (mvList != null)
                        moves.addAll(mvList);
                }

        return moves;
    }

    public List<Move> getLegalMoves() {
        List<Move> pseudomoves = getPseudoLegalMoves();
        ArrayList<Move> moves = new ArrayList<>();
        for (Move m : pseudomoves) {
            Board b = this.doMove(m);
//            System.out.println(m.toString() + " => " + b.toString());
            if (b.canCaptureKing()) continue;
            moves.add(m);
        }

        return moves;
    }

    public boolean equals(Object obj) {
        // Assume that two boards with the same zobrist key (64 bit) are identical
        if (obj instanceof Board)
            return this.getZobristKey() == ((Board) obj).getZobristKey();
        return super.equals(obj);
    }

    public long getZobristKey() {
        long hash = 0;
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++) {
                int code = 0;
                if (!board[y][x].isEmpty()) {
                    code = board[y][x].getPiece().ordinal();
                    if (board[y][x].getColor() == Piece.PieceColor.BLACK)
                        code += 6;
                }
                hash ^= zobristKey[code][y][x];
            }
        int castleStatus = 0;
        if (whiteKingCastle) castleStatus += 1;
        if (whiteQueenCastle) castleStatus += 2;
        if (blackKingCastle) castleStatus += 4;
        if (blackQueenCastle) castleStatus += 8;
        hash ^= zobristKeyCastle[castleStatus];
        hash ^= zobristKeyEnPassant[enPassantFile + 1];
        hash ^= zobristKeyToMove[toMove == Piece.PieceColor.WHITE ? 1 : 0];
        return hash;
    }

    public int hashCode() {
        return (int) getZobristKey();
    }

}
