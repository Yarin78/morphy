package se.yarin.cbhlib;

import se.yarin.chess.*;

import static se.yarin.chess.Chess.*;

public class TestGames {
    static GameMovesModel getCrazyGame() {
        GameMovesModel moves = new GameMovesModel();
        moves.root()
                .addMove(E2, E4)
                .addMove(B8, C6)
                .addMove(E4, E5)
                .addMove(F7, F5)
                .addMove(E5, F6)
                .addMove(G8, F6)
                .addMove(C2, C4)
                .addMove(G7, G6)
                .addMove(C4, C5)
                .addMove(B7, B5)
                .addMove(C5, B6)
                .addMove(F8, G7)
                .addMove(B6, B7)
                .addMove(E8, G8)
                .addMove(new ShortMove(B7, A8, Stone.WHITE_ROOK))
                .addMove(C8, A6)
                .addMove(B1, C3)
                .addMove(H7, H5)
                .addMove(D2, D3)
                .addMove(H5, H4)
                .addMove(C1, F4)
                .addMove(G6, G5)
                .addMove(D1, D2)
                .addMove(G5, F4)
                .addMove(E1, C1)
                .addMove(C6, E5)
                .addMove(G2, G3)
                .addMove(H4, G3)
                .addMove(H2, G3)
                .addMove(F4, G3)
                .addMove(D3, D4)
                .addMove(G3, F2)
                .addMove(D4, E5)
                .addMove(new ShortMove(F2, G1, Stone.BLACK_BISHOP))
                .addMove(E5, F6)
                .addMove(G1, E3)
                .addMove(F6, E7)
                .addMove(A6, C4)
                .addMove(new ShortMove(E7, D8, Stone.WHITE_ROOK))
                .addMove(A7, A5)
                .addMove(A8, A6)
                .addMove(A5, A4)
                .addMove(A6, H6)
                .addMove(A4, A3)
                .addMove(D8, D7)
                .addMove(A3, B2)
                .addMove(C1, C2)
                .addMove(new ShortMove(B2, B1, Stone.BLACK_BISHOP))
                .addMove(C2, B2)
                .addMove(B1, F5)
                .addMove(D1, E1)
                .addMove(F8, B8)
                .addMove(B2, A1)
                .addMove(E3, D2)
                .addMove(H6, H8)
                .addMove(G7, H8)
                .addMove(H1, H8)
                .addMove(G8, H8)
                .addMove(E1, E7)
                .addMove(D2, C3);

        return moves;
    }

    static GameMovesModel getVariationGame() {
        GameMovesModel moves = new GameMovesModel();

        moves.root()
                .addMove(E2, E4)
                .addMove(E7, E5)
                .addMove(G1, F3)
                .addMove(B8, C6)
                .addMove(F1, B5).parent()
                .addMove(F1, C4).addMove(F8, C5).addMove(D2, D3).parent().parent().parent()
                .addMove(B1, C3).parent().parent()
                .addMove(G8, F6).parent().parent()
                .addMove(B1, C3).addMove(B8, C6);

        return moves;
    }

    static GameMovesModel getEndGame() {
        String s = "........\n....kr..\n..p.....\n.p.n..p.\n.P......\n.KPB.P..\n..R.....\n........";
        Position start = Position.fromString(s, Player.WHITE);
        GameMovesModel moves = new GameMovesModel(start, 45);
        moves.root()
                .addMove(D3, E4)
                .addMove(E7, E6)
                .addMove(C2, G2)
                .addMove(F7, G7)
                .addMove(C3, C4)
                .addMove(B5, C4)
                .addMove(B3, C4)
                .addMove(D5, E3)
                .addMove(C4, D4)
                .addMove(E3, G2)
                .addMove(F3, F4)
                .addMove(G5, F4)
                .addMove(E4, G2)
                .addMove(G7, G2)
                .addMove(D4, C4)
                .addMove(F4, F3)
                .addMove(B4, B5)
                .addMove(C6, B5)
                .addMove(C4, B5)
                .addMove(F3, F2)
                .addMove(B5, C4)
                .addMove(new ShortMove(F2, F1, Stone.BLACK_QUEEN));

        return moves;
    }
}
