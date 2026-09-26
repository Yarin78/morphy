package se.yarin.morphy.convert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static se.yarin.chess.Chess.*;

import java.lang.reflect.RecordComponent;
import org.junit.Test;
import se.yarin.chess.Chess960;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.ShortMove;
import se.yarin.morphy.DatabaseCbh;
import se.yarin.morphy.DatabaseCbhFacade;
import se.yarin.morphy.TestGames;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.model.GameDto;

/**
 * The start position of a game survives the DTO: a set-up position as its FEN, and a Chess960 game
 * as its FEN together with the Chess960 variant.
 */
public class GameDtoStartPositionTest {

  private static final String CHESS960_FEN =
      "bnrkrbnq/pppppppp/8/8/8/8/PPPPPPPP/BNRKRBNQ w KQkq - 0 1";

  private final DatabaseCbh db = new DatabaseCbh();
  private final DatabaseCbhFacade facade = new DatabaseCbhFacade(db);

  @Test
  public void regularGameHasNoFenOrVariant() {
    GameDto dto = addAndRead(new GameMovesModel());

    assertNull(dto.moves().fen());
    assertNull(dto.variant());
  }

  @Test
  public void setupPositionIsSentAsItsFen() {
    GameDto dto = addAndRead(TestGames.getEndGame());

    assertEquals("8/4kr2/2p5/1p1n2p1/1P6/1KPB1P2/2R5/8 w - - 0 45", dto.moves().fen());
    assertEquals(Boolean.TRUE, dto.setupPosition());
    assertNull(dto.variant());
    assertReadsBackTheSame(dto);
  }

  @Test
  public void chess960GameIsSentAsItsFenAndVariant() {
    GameDto dto = addAndRead(chess960Game());

    assertEquals(CHESS960_FEN, dto.moves().fen());
    assertEquals(Chess960.VARIANT, dto.variant());
    assertReadsBackTheSame(dto);

    // Written back, the header knows the game as Chess960 without looking at the moves
    long id = facade.addGame(dto);
    assertEquals(
        Chess960.getStartPositionNo("BNRKRBNQ"), db.getGame((int) id).header().chess960StartPosition());
  }

  @Test
  public void castlingInAChess960GameReadsBack() {
    // O-O-O in the start position above only works if the start position is read as Chess960
    GameDto dto = addAndRead(chess960Game());

    GameModel model = new GameDtoImporter().toGameModel(dto);

    assertEquals(chess960Game().toString(), model.moves().toString());
  }

  @Test
  public void unknownVariantIsRejected() throws ReflectiveOperationException {
    GameDto dto = withVariant(addAndRead(new GameMovesModel()), "Crazyhouse");

    assertThrows(IllegalArgumentException.class, () -> facade.addGame(dto));
  }

  private GameDto addAndRead(GameMovesModel moves) {
    GameModel simple = TestGames.getSimpleGame("White", "Black");
    int id = db.addGame(new GameModel(simple.header(), moves));
    return facade.getGame(id, GameFetchOptions.full());
  }

  /** Writing the DTO back, as a new game, gives the same moves, start position and variant. */
  private void assertReadsBackTheSame(GameDto dto) {
    long id = facade.addGame(dto);
    GameDto again = facade.getGame(id, GameFetchOptions.full());
    assertEquals(dto.moves(), again.moves());
    assertEquals(dto.variant(), again.variant());
    assertTrue(again.setupPosition());
  }

  private static GameMovesModel chess960Game() {
    int sp = Chess960.getStartPositionNo("BNRKRBNQ");
    GameMovesModel moves = new GameMovesModel(Chess960.getStartPosition(sp), 1);
    moves
        .root()
        .addMove(B2, B3)
        .addMove(B7, B6)
        .addMove(E2, E4)
        .addMove(E7, E5)
        .addMove(G1, F3)
        .addMove(F7, F6)
        .addMove(F1, C4)
        .addMove(F8, C5)
        .addMove(H1, F1)
        .addMove(G8, E7)
        .addMove(ShortMove.longCastles())
        .addMove(B8, C6);
    return moves;
  }

  private static GameDto withVariant(GameDto dto, String variant)
      throws ReflectiveOperationException {
    RecordComponent[] components = GameDto.class.getRecordComponents();
    Object[] args = new Object[components.length];
    Class<?>[] types = new Class<?>[components.length];
    for (int i = 0; i < components.length; i++) {
      types[i] = components[i].getType();
      args[i] =
          components[i].getName().equals("variant")
              ? variant
              : components[i].getAccessor().invoke(dto);
    }
    return GameDto.class.getDeclaredConstructor(types).newInstance(args);
  }
}
