package se.yarin.morphy;

import org.junit.Test;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Player;
import se.yarin.chess.Position;
import se.yarin.morphy.games.*;

import static org.junit.Assert.assertEquals;
import static se.yarin.chess.Chess.*;

public class GameAdapterTest {

  /**
   * Tests roundtrip conversion: Game -> GameModel -> GameHeader/ExtendedGameHeader
   *
   * GameAdapter.setGameData() now preserves entity IDs from the GameModel's internal fields
   * if they exist. However, it doesn't set storage-layer fields like id, movesOffset, and
   * annotationOffset - those are set later by DatabaseWriteTransaction.
   */
  @Test
  public void testRoundtripConversion() throws Exception {
    Database database = ResourceLoader.openWorldChDatabase();
    GameAdapter adapter = new GameAdapter();

    int totalGames = database.count();
    int passedGames = 0;

    for (int gameId = 1; gameId <= totalGames; gameId++) {
      Game game = database.getGame(gameId);

      // Skip guiding texts for now as they have different handling
      if (game.guidingText()) {
        continue;
      }

      // Get original headers
      GameHeader originalHeader = game.header();
      ExtendedGameHeader originalExtendedHeader = game.extendedHeader();

      // Convert to GameModel
      GameModel gameModel = adapter.getGameModel(game);

      // Convert back to storage layer
      ImmutableGameHeader.Builder newHeaderBuilder = ImmutableGameHeader.builder();
      ImmutableExtendedGameHeader.Builder newExtendedHeaderBuilder = ImmutableExtendedGameHeader.builder();
      adapter.setGameData(newHeaderBuilder, newExtendedHeaderBuilder, gameModel);
      ImmutableGameHeader newHeader = newHeaderBuilder.build();
      ImmutableExtendedGameHeader newExtendedHeader = newExtendedHeaderBuilder.build();

      // Compare headers, excluding fields that GameAdapter doesn't set
      // (id, movesOffset, annotationOffset, lastChangedTimestamp, creationTimestamp, gameVersion, unknowns)
      GameHeader normalizedNew = ImmutableGameHeader.builder()
          .from(newHeader)
          .id(originalHeader.id())
          .movesOffset(originalHeader.movesOffset())
          .annotationOffset(originalHeader.annotationOffset())
          .build();

      ExtendedGameHeader normalizedNewExtended =
          ImmutableExtendedGameHeader.builder()
              .from(newExtendedHeader)
              .movesOffset(originalExtendedHeader.movesOffset())
              .annotationOffset(originalExtendedHeader.annotationOffset())
              .lastChangedTimestamp(originalExtendedHeader.lastChangedTimestamp())
              .creationTimestamp(originalExtendedHeader.creationTimestamp())
              .gameVersion(originalExtendedHeader.gameVersion())
              .unknown1(originalExtendedHeader.unknown1())
              .unknown2(originalExtendedHeader.unknown2())
              .build();

        assertEquals(
            "GameHeader mismatch for game " + gameId,
            originalHeader,
            normalizedNew
        );
        assertEquals(
            "ExtendedGameHeader mismatch for game " + gameId,
            originalExtendedHeader,
            normalizedNewExtended
        );
        passedGames++;
    }

    // Print summary
    System.out.println("\n=== Roundtrip Conversion Test Results ===");
    System.out.println("Total games processed: " + totalGames);
    System.out.println("Games passed: " + passedGames);

    database.close();
  }


  private int noMoves(GameMovesModel moves) {
    ImmutableGameHeader.Builder header = ImmutableGameHeader.builder();
    new GameAdapter()
        .setGameData(
            header, ImmutableExtendedGameHeader.builder(), new GameModel(new GameHeaderModel(), moves));
    return header.build().noMoves();
  }

  @Test
  public void testNumberOfMoves() {
    assertEquals(0, noMoves(new GameMovesModel()));

    GameMovesModel moves = new GameMovesModel();
    moves.root().addMove(E2, E4);
    assertEquals(1, noMoves(moves));
    moves.root().mainNode().addMove(E7, E5);
    assertEquals(1, noMoves(moves));
    moves.root().mainNode().mainNode().addMove(G1, F3);
    assertEquals(2, noMoves(moves));
  }

  @Test
  public void testNumberOfMovesWhenBlackMovesFirst() {
    // The first move by black counts as a move
    String board = "........\n....kr..\n..p.....\n.p.n..p.\n.P......\n.KPB.P..\n..R.....\n........";
    GameMovesModel moves = new GameMovesModel(Position.fromString(board, Player.BLACK), 45);
    assertEquals(0, noMoves(moves));

    GameMovesModel.Node node = moves.root().addMove(E7, D7);
    assertEquals(1, noMoves(moves));
    node.addMove(D3, E4);
    assertEquals(2, noMoves(moves));
    node.mainNode().addMove(D7, E6);
    assertEquals(2, noMoves(moves));
  }
}
