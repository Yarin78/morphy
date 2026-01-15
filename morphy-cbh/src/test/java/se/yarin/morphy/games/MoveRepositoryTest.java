package se.yarin.morphy.games;

import org.junit.Test;
import se.yarin.chess.GameMovesModel;
import se.yarin.morphy.TestGames;
import se.yarin.morphy.exceptions.MorphyException;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.storage.BlobStorageHeader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static se.yarin.chess.Chess.*;
import static se.yarin.chess.Chess.F3;

public class MoveRepositoryTest {
  @Test
  public void putAndGetGame() {
    MoveRepository repo = new MoveRepository();
    long offset = repo.putMoves(0, TestGames.getVariationGame());
    assertEquals(BlobStorageHeader.DEFAULT_SERIALIZED_HEADER_SIZE, offset);

    GameMovesModel moves = repo.getMoves(offset, 1);
    assertEquals(TestGames.getVariationGame().root().toSAN(), moves.root().toSAN());
  }

  @Test(expected = MorphyInvalidDataException.class)
  public void putGameBeyondEnd() {
    MoveRepository repo = new MoveRepository();
    repo.putMoves(100, TestGames.getVariationGame());
  }

  @Test
  public void replaceWithLargerGame() {
    GameMovesModel shortGame = new GameMovesModel();
    shortGame.root().addMove(E2, E4).addMove(E7, E5).addMove(G1, F3);
    GameMovesModel longerGame = new GameMovesModel();
    longerGame
        .root()
        .addMove(E2, E4)
        .addMove(E7, E5)
        .addMove(G1, F3)
        .addMove(B8, C6)
        .addMove(F1, B5);

    MoveRepository repo = new MoveRepository();
    long ofs1 = repo.putMoves(0, shortGame);
    long ofs2 = repo.putMoves(0, TestGames.getVariationGame());
    assertTrue(ofs2 > ofs1);

    int delta = repo.preparePutBlob(ofs1, longerGame);
    assertEquals(2, delta); // 2 more moves in the longer game
    repo.putMoves(ofs1, longerGame);

    GameMovesModel movedGame = repo.getMoves(ofs2 + delta, 2);
    assertEquals(TestGames.getVariationGame().root().toSAN(), movedGame.root().toSAN());
  }
}
