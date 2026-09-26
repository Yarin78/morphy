package se.yarin.morphy.cb2.indexes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import se.yarin.morphy.cb2.entities.Player;

/** The keys of the sort orders. */
class EntityOrderTest {

  private static long key(String lastName) {
    return EntityOrder.key(Player.ofFullName(lastName));
  }

  @Test
  void aPlayerKeyIsTheFirstEightCharactersOfTheLastName() {
    // "caruana", padded with a zero byte
    assertEquals(0x63617275616e6100L, key("Caruana"));
    // "northolt"; the hyphen is the ninth character and is not in the key
    assertEquals(0x6e6f7274686f6c74L, key("Northolt-Long"));
  }

  @Test
  void aPlayerKeyIsLowercased() {
    assertEquals(key("carlsen"), key("Carlsen"));
  }

  @Test
  void aPlayerKeyWithAHyphenInItIsZero() {
    // The hyphen is the 8th character of the key, and a hyphen anywhere in it is the same
    assertEquals(0, key("Vachier-Lagrave"));
    assertEquals(0, key("Ab-cd"));
    // Beyond the eighth character it plays no part
    assertEquals(key("Abcdefgh"), key("Abcdefgh-Ij"));
  }

  @Test
  void aPlayerKeyWithACharacterThatIsNotPermittedIsZero() {
    assertEquals(0, key("O'Brien"));
    assertEquals(0, key("Gr\u00fcnfeld"));
  }
}
