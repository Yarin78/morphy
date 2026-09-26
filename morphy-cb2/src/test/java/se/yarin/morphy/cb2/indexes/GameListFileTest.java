package se.yarin.morphy.cb2.indexes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.yarin.morphy.cb2.indexes.GameListFile.Form;
import se.yarin.morphy.cb2.indexes.GameListFile.Role;
import se.yarin.morphy.cb2.storage.MemoryByteStore;

/** Lists are written in the form that fits them, and read back unchanged. */
class GameListFileTest {

  @Test
  void formsAndBlockReuse() {
    MemoryByteStore store = new MemoryByteStore();
    GameListFile.create(store);
    GameListFile lists = new GameListFile(store, "test");

    lists.setGames(3, Role.PLAYER, List.of(5, 5));
    assertEquals(Form.SINGLE, lists.form(3, Role.PLAYER));
    assertEquals(List.of(5, 5), lists.games(3, Role.PLAYER));
    assertEquals(4, lists.recordCount());

    lists.setGames(3, Role.TOURNAMENT, List.of(7, 8, 9, 10));
    assertEquals(Form.RANGE, lists.form(3, Role.TOURNAMENT));
    assertEquals(List.of(7, 8, 9, 10), lists.games(3, Role.TOURNAMENT));

    List<Integer> many = new ArrayList<>();
    for (int i = 1; i <= 75; i++) {
      many.add(i * 2);
    }
    lists.setGames(0, Role.SOURCE, many);
    assertEquals(Form.CHAIN, lists.form(0, Role.SOURCE));
    assertEquals(many, lists.games(0, Role.SOURCE));
    assertEquals(3, lists.blocksInUse());

    // Growing reuses the three blocks and takes one more; shrinking abandons the extra ones
    for (int i = 76; i <= 100; i++) {
      many.add(i * 2);
    }
    lists.setGames(0, Role.SOURCE, many);
    assertEquals(many, lists.games(0, Role.SOURCE));
    assertEquals(4, lists.blocksInUse());
    lists.setGames(0, Role.SOURCE, many.subList(0, 10));
    assertEquals(many.subList(0, 10), lists.games(0, Role.SOURCE));
    assertEquals(1, lists.chain(0, Role.SOURCE).size());

    lists.setGames(0, Role.SOURCE, List.of());
    assertEquals(Form.EMPTY, lists.form(0, Role.SOURCE));
    assertEquals(0, lists.count(0, Role.SOURCE));
    // Untouched lists are unchanged, and a far entity id grows the file
    assertEquals(List.of(5, 5), lists.games(3, Role.PLAYER));
    lists.setGames(40, Role.GAME_TAG, List.of(1));
    assertEquals(List.of(1), new GameListFile(store, "test").games(40, Role.GAME_TAG));
    assertEquals(List.of(), lists.games(39, Role.GAME_TAG));
  }
}
