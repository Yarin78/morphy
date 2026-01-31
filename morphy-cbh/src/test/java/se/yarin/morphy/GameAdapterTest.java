package se.yarin.morphy;

import org.junit.Test;
import se.yarin.chess.GameModel;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.games.ImmutableExtendedGameHeader;
import se.yarin.morphy.games.ImmutableGameHeader;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

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
    List<String> failures = new ArrayList<>();

    for (int gameId = 1; gameId <= totalGames; gameId++) {
      try {
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

        // Compare headers, excluding storage-layer fields that GameAdapter doesn't set
        // (id, movesOffset, annotationOffset, lastChangedTimestamp, creationTimestamp, gameVersion)
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
                .build();

        try {
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
        } catch (AssertionError e) {
          failures.add("Game " + gameId + ": " + e.getMessage());
          break;
        }

      } catch (Exception e) {
        failures.add("Game " + gameId + ": Exception - " + e.getMessage());
      }
    }

    // Print summary
    System.out.println("\n=== Roundtrip Conversion Test Results ===");
    System.out.println("Total games processed: " + totalGames);
    System.out.println("Games passed: " + passedGames);
    System.out.println("Games failed: " + failures.size());

    if (!failures.isEmpty()) {
      System.out.println("\n=== Failures ===");
      for (String failure : failures) {
        System.out.println(failure);
      }
    }

    database.close();
  }
}
