package se.yarin.morphy.entities.filters;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;
import se.yarin.morphy.DatabaseCbh;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.Player;

public class MultiPlayerNameFilterTest {

  // EntityIndexTransaction#get(id, filter) only consults matchesSerialized(), unlike stream(),
  // which is safe because it also double-checks the deserialized entity with matches(). This is
  // the fast lookup path GameEntityLoopJoin uses, so matchesSerialized() must be correct on its
  // own for OR-of-names ("white.name:foo|bar") searches to actually filter anything.
  @Test
  public void matchesSerializedAgreesWithMatches() {
    DatabaseCbh db = ResourceLoader.openWorldChDatabase();
    try (EntityIndexReadTransaction<Player> txn = db.playerIndex().beginReadTransaction()) {
      Player steinitz = db.playerIndex().prefixSearch("Steinitz").get(0);
      Player capablanca = db.playerIndex().prefixSearch("Capablanca").get(0);

      MultiPlayerNameFilter filter =
          new MultiPlayerNameFilter(List.of("Steinitz", "Lasker"), false, false);

      assertNotNull(
          "Steinitz should match a Steinitz|Lasker filter",
          txn.get(steinitz.id(), filter));
      assertNull(
          "Capablanca should not match a Steinitz|Lasker filter",
          txn.get(capablanca.id(), filter));
    }
  }
}
