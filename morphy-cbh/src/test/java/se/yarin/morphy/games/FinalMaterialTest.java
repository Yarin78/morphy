package se.yarin.morphy.games;

import org.junit.Test;
import se.yarin.chess.Chess;
import se.yarin.chess.GameMovesModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FinalMaterialTest {

  @Test
  public void testCalculateFinalMaterial() {
    // Test with starting position (all pieces present)
    GameMovesModel model = new GameMovesModel();
    FinalMaterial.Result result = FinalMaterial.calculate(model);

    // Starting position should have all pieces
    assertEquals(8, result.player1().numPawns());
    assertEquals(1, result.player1().numQueens());
    assertEquals(2, result.player1().numKnights());
    assertEquals(2, result.player1().numBishops());
    assertEquals(2, result.player1().numRooks());

    assertEquals(8, result.player2().numPawns());
    assertEquals(1, result.player2().numQueens());
    assertEquals(2, result.player2().numKnights());
    assertEquals(2, result.player2().numBishops());
    assertEquals(2, result.player2().numRooks());
  }

  @Test
  public void testCalculateFinalMaterialAfterMoves() {
    // Create a simple game: 1. e4 e5 2. Nf3 Nc6 3. Nxe5 (knight takes pawn)
    GameMovesModel model = new GameMovesModel();
    GameMovesModel.Node node = model.root();
    node = node.addMove(Chess.E2, Chess.E4);
    node = node.addMove(Chess.E7, Chess.E5);
    node = node.addMove(Chess.G1, Chess.F3);
    node = node.addMove(Chess.B8, Chess.C6);
    node = node.addMove(Chess.F3, Chess.E5);

    FinalMaterial.Result result = FinalMaterial.calculate(model);

    // White should have 8 pawns, black should have 7 pawns (one captured)
    assertEquals(8, result.player1().numPawns());
    assertEquals(7, result.player2().numPawns());

    // All other pieces should be intact
    assertEquals(1, result.player1().numQueens());
    assertEquals(1, result.player2().numQueens());
    assertEquals(2, result.player1().numKnights());
    assertEquals(2, result.player2().numKnights());
  }

  @Test
  public void testCalculateReturnsMoreMaterialFirst() {
    // Create a game where white has more material
    GameMovesModel model = new GameMovesModel();
    GameMovesModel.Node node = model.root();
    node = node.addMove(Chess.E2, Chess.E4);
    node = node.addMove(Chess.E7, Chess.E5);
    node = node.addMove(Chess.F1, Chess.C4);
    node = node.addMove(Chess.B8, Chess.C6);
    node = node.addMove(Chess.D1, Chess.H5);
    node = node.addMove(Chess.G8, Chess.F6);
    node = node.addMove(Chess.H5, Chess.F7); // Checkmate - white captures pawn

    FinalMaterial.Result result = FinalMaterial.calculate(model);

    // Player1 should have more material (8 pawns vs 7)
    assertTrue(result.player1().numPawns() > result.player2().numPawns());
  }

  @Test
  public void testCompareTo() {
    FinalMaterial m1 = new FinalMaterial(8, 1, 2, 2, 2); // Standard starting position
    FinalMaterial m2 = new FinalMaterial(8, 1, 2, 2, 2); // Same
    FinalMaterial m3 = new FinalMaterial(7, 1, 2, 2, 2); // One fewer pawn
    FinalMaterial m4 = new FinalMaterial(8, 0, 2, 2, 2); // One fewer queen
    FinalMaterial m5 = new FinalMaterial(8, 1, 2, 2, 1); // One fewer rook

    // Equal materials
    assertEquals(0, m1.compareTo(m2));

    // Pawns have highest priority
    assertTrue(m1.compareTo(m3) < 0); // More pawns comes first
    assertTrue(m3.compareTo(m1) > 0);

    // Queens have priority
    assertTrue(m1.compareTo(m4) < 0); // More queens comes first
    assertTrue(m4.compareTo(m1) > 0);

    // Rooks have lowest priority
    assertTrue(m1.compareTo(m5) < 0); // More rooks comes first
    assertTrue(m5.compareTo(m1) > 0);
  }

  @Test
  public void testCompareToOrderOfPriority() {
    // Test that queens are compared before rooks
    FinalMaterial fewerQueens = new FinalMaterial(0, 0, 0, 0, 2); // No queens, 2 rooks
    FinalMaterial moreQueens = new FinalMaterial(0, 1, 0, 0, 0); // 1 queen, no rooks

    // More queens should come first, regardless of other pieces
    assertTrue(moreQueens.compareTo(fewerQueens) < 0);

    // Test that bishops are compared before rooks
    FinalMaterial moreBishops = new FinalMaterial(0, 0, 0, 2, 0); // No rooks, 2 bishops
    FinalMaterial moreRooks = new FinalMaterial(0, 0, 0, 0, 1); // 1 rook, no bishops

    assertTrue(moreBishops.compareTo(moreRooks) < 0);
  }

  @Test
  public void testToString() {
    // Empty board
    FinalMaterial empty = new FinalMaterial(0, 0, 0, 0, 0);
    assertEquals("", empty.toString());

    // Standard starting position
    FinalMaterial starting = new FinalMaterial(8, 1, 2, 2, 2);
    assertEquals("QRRBBNN8P", starting.toString());

    // Only pawns
    FinalMaterial onlyPawns = new FinalMaterial(3, 0, 0, 0, 0);
    assertEquals("3P", onlyPawns.toString());

    // Multiple queens (promoted)
    FinalMaterial multiQueens = new FinalMaterial(0, 3, 0, 0, 0);
    assertEquals("QQQ", multiQueens.toString());

    // Mixed pieces
    FinalMaterial mixed = new FinalMaterial(2, 1, 2, 1, 1);
    assertEquals("QRBNN2P", mixed.toString());

    // One of each
    FinalMaterial oneEach = new FinalMaterial(1, 1, 1, 1, 1);
    assertEquals("QRBN1P", oneEach.toString());

    // No pawns
    FinalMaterial noPawns = new FinalMaterial(0, 1, 1, 1, 1);
    assertEquals("QRBN", noPawns.toString());
  }

  @Test
  public void testEncodeDecodeRoundtrip() {
    FinalMaterial original = new FinalMaterial(8, 1, 2, 2, 2);
    int encoded = FinalMaterial.encode(original);
    FinalMaterial decoded = FinalMaterial.decode(encoded);

    assertEquals(original, decoded);
  }

  @Test
  public void testEncodeNull() {
    int encoded = FinalMaterial.encode(null);
    assertEquals(0, encoded);
  }

  @Test
  public void testDecodeZero() {
    FinalMaterial decoded = FinalMaterial.decode(0);
    assertEquals(new FinalMaterial(0, 0, 0, 0, 0), decoded);
  }

  @Test
  public void testEncodeTruncatesExcessPieces() {
    // Test that encoding truncates pieces that exceed the bit field limits
    // Max for queens, rooks, bishops, knights is 7; max for pawns is 15
    FinalMaterial excessive = new FinalMaterial(16, 8, 8, 8, 8);
    int encoded = FinalMaterial.encode(excessive);
    FinalMaterial decoded = FinalMaterial.decode(encoded);

    // Pawns can be up to 15, others max at 7
    assertEquals(0, decoded.numPawns()); // 16 & 15 = 0
    assertEquals(7, decoded.numQueens()); // min(8, 7) = 7
    assertEquals(7, decoded.numKnights());
    assertEquals(7, decoded.numBishops());
    assertEquals(7, decoded.numRooks());
  }

  @Test
  public void testSum() {
    FinalMaterial white = new FinalMaterial(5, 1, 1, 2, 1);
    FinalMaterial black = new FinalMaterial(3, 0, 1, 0, 1);

    FinalMaterial sum = FinalMaterial.sum(white, black);

    assertEquals(8, sum.numPawns());
    assertEquals(1, sum.numQueens());
    assertEquals(2, sum.numKnights());
    assertEquals(2, sum.numBishops());
    assertEquals(2, sum.numRooks());
  }

  @Test
  public void testSumWithZeros() {
    FinalMaterial m1 = new FinalMaterial(0, 0, 0, 0, 0);
    FinalMaterial m2 = new FinalMaterial(3, 1, 1, 1, 1);

    FinalMaterial sum = FinalMaterial.sum(m1, m2);

    assertEquals(m2, sum);
  }
}
