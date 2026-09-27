package se.yarin.morphy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import org.junit.Test;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.queries.QueryEngineKind;

public class DatabaseCbhFacadeEngineSelectionTest {

  @Test
  public void defaultConstructorUsesLegacyEngine() throws IOException {
    try (DatabaseCbh db = ResourceLoader.openWorldChDatabase()) {
      DatabaseCbhFacade facade = new DatabaseCbhFacade(db);
      // Should not throw: the default engine can execute a search.
      var page = facade.findGames(Query.all(0, 1), GameFetchOptions.headersOnly());
      assertEquals(1, page.items().size());
    }
  }

  @Test
  public void nodeEngineIsNotYetWiredIntoFindGames() throws IOException {
    try (DatabaseCbh db = ResourceLoader.openWorldChDatabase()) {
      DatabaseCbhFacade facade = new DatabaseCbhFacade(db, QueryEngineKind.NODE);
      assertThrows(
          UnsupportedOperationException.class,
          () -> facade.findGames(Query.all(0, 1), GameFetchOptions.headersOnly()));
    }
  }

  @Test
  public void nodeEngineIsNotYetWiredIntoFindEntities() throws IOException {
    try (DatabaseCbh db = ResourceLoader.openWorldChDatabase()) {
      DatabaseCbhFacade facade = new DatabaseCbhFacade(db, QueryEngineKind.NODE);
      assertThrows(
          UnsupportedOperationException.class,
          () ->
              facade.findEntities(
                  se.yarin.morphy.api.EntityKind.PLAYER, Query.all(0, 1)));
    }
  }
}
