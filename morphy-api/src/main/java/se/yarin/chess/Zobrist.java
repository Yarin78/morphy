package se.yarin.chess;

import java.util.Random;

/**
 * The random keys of the Zobrist hash of a position: one per stone (empty included) and square,
 * per set of castling rights, per en passant file (and none), and per side to move. A position's
 * hash is the exclusive or of its keys, and the Chess960 start position number. Shared by
 * {@link Position} and {@link HashingBoard}, which must hash alike.
 */
final class Zobrist {
  private Zobrist() {}

  static final long[][] PIECE_LO = new long[13][64], PIECE_HI = new long[13][64];
  static final long[] CASTLE_LO = new long[16], CASTLE_HI = new long[16];
  static final long[] EN_PASSANT_LO = new long[9], EN_PASSANT_HI = new long[9];
  static final long[] TO_MOVE_LO = new long[2], TO_MOVE_HI = new long[2];

  private static long nonzeroLong(Random r) {
    long v;
    do {
      long v1 = r.nextLong(), v2 = r.nextLong();
      v = (v1 << 32) + v2;
    } while (v == 0);
    return v;
  }

  // The keys are drawn in this order from two seeded generators; changing it changes every hash
  static {
    Random rlo = new Random(0);
    Random rhi = new Random(1);
    for (int i = 0; i < 64; i++) {
      for (int c = 0; c < 13; c++) {
        PIECE_LO[c][i] = nonzeroLong(rlo);
        PIECE_HI[c][i] = nonzeroLong(rhi);
      }
    }
    for (int i = 0; i < 16; i++) {
      CASTLE_LO[i] = nonzeroLong(rlo);
      CASTLE_HI[i] = nonzeroLong(rhi);
    }
    for (int i = 0; i < 9; i++) {
      EN_PASSANT_LO[i] = nonzeroLong(rlo);
      EN_PASSANT_HI[i] = nonzeroLong(rhi);
    }
    for (int i = 0; i < 2; i++) {
      TO_MOVE_LO[i] = nonzeroLong(rlo);
      TO_MOVE_HI[i] = nonzeroLong(rhi);
    }
  }
}
