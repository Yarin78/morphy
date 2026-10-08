package se.yarin.morphy.cb2.moves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.junit.jupiter.api.Test;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.MoveCode;
import se.yarin.chess.Player;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.MainLine;
import se.yarin.morphy.cb2.TestDatabases;
import se.yarin.morphy.cb2.games.GameHeader;
import se.yarin.morphy.cb2.games.GameHeaderFile;
import se.yarin.morphy.cb2.games.GameRecord;
import se.yarin.morphy.cb2.storage.ByteStore;
import se.yarin.morphy.cb2.storage.RecordFile;

/** The main line read off the words is the main line of the game decoded in full. */
class MainLineTest {

  @Test
  void mainLineIsThatOfTheFullDecode() {
    File file = TestDatabases.wch2();
    int games = 0, withVariations = 0;
    try (GameHeaderFile headers = new GameHeaderFile(ByteStore.open(file, AccessMode.READ_ONLY), "cbh");
        RecordFile moves =
            new RecordFile(
                ByteStore.open(TestDatabases.sibling(file, ".2cbg"), AccessMode.READ_ONLY), "cbg")) {
      for (int id = 1; id <= headers.count(); id++) {
        GameRecord record = headers.get(id);
        if (!(record instanceof GameHeader)) {
          continue;
        }
        RecordFile.Record stored = moves.read(record.movesOffset());
        GameMovesModel model = MoveStreamCodec.decode(stored.tag(), stored.content());
        MainLine line = MoveStreamCodec.mainLine(stored.tag(), stored.content());
        GameMovesModel.Node node = model.root();
        if (model.countPly(true) > model.countPly(false)) {
          withVariations++;
        }
        while (true) {
          assertEquals(node.position().getZobristHashLo(), line.hash(), "hash, game " + id + " ply " + node.ply());
          assertEquals(node.position().playerToMove() == Player.WHITE, line.whiteToMove());
          assertEquals(
              node.hasMoves() ? MoveCode.of(node.mainNode().lastMove()) : MoveCode.NONE,
              line.moveCode(),
              "move code, game " + id + " ply " + node.ply());
          assertEquals(node.position(), line.position(), "game " + id + " ply " + node.ply());
          if (!node.hasMoves()) {
            assertNull(line.move(), "game " + id + " goes on");
            break;
          }
          assertEquals(node.mainNode().lastMove(), line.move(), "game " + id + " ply " + node.ply());
          line.advance();
          node = node.mainNode();
        }
        games++;
      }
    }
    assertTrue(games > 500 && withVariations > 50, games + " games, " + withVariations + " with variations");
  }
}
