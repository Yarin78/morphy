package se.yarin.morphy.queries.visualisation;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.queries.operations.QueryData;

/** Formats query result lists into column headers and row data for the HTML visualizer table. */
public class QueryResultFormatter {

  private static final int DEFAULT_MAX_ROWS = 10;

  public static @NotNull List<String> gameColumns() {
    return List.of("#", "White", "Black", "Result", "Date", "ECO", "Tournament");
  }

  public static @NotNull List<List<String>> formatGameRows(@NotNull List<QueryData<Game>> results) {
    return formatGameRows(results, DEFAULT_MAX_ROWS);
  }

  public static @NotNull List<List<String>> formatGameRows(
      @NotNull List<QueryData<Game>> results, int maxRows) {
    List<List<String>> rows = new ArrayList<>();
    for (QueryData<Game> qd : results.stream().limit(maxRows).toList()) {
      Game g = qd.data();
      if (g == null) {
        rows.add(List.of(String.valueOf(qd.id()), "", "", "", "", "", ""));
      } else {
        rows.add(
            List.of(
                String.valueOf(g.id()),
                g.guidingText() ? "(text)" : g.white().getFullName(),
                g.guidingText() ? "" : g.black().getFullName(),
                g.result().toString(),
                g.playedDate().toString(),
                g.eco().toString(),
                g.tournament().title()));
      }
    }
    return rows;
  }

  public static @NotNull List<String> entityColumns(@NotNull EntityType entityType) {
    return switch (entityType) {
      case PLAYER -> List.of("#", "Name", "Games");
      case TOURNAMENT -> List.of("#", "Title", "Date", "Category", "Games");
      case ANNOTATOR -> List.of("#", "Name", "Games");
      case SOURCE -> List.of("#", "Title", "Publisher", "Games");
      case TEAM -> List.of("#", "Title", "Year", "Games");
      case GAME_TAG -> List.of("#", "Title", "Games");
    };
  }

  public static <T extends IdObject> @NotNull List<List<String>> formatEntityRows(
      @NotNull List<QueryData<T>> results, @NotNull EntityType entityType) {
    return formatEntityRows(results, entityType, DEFAULT_MAX_ROWS);
  }

  public static <T extends IdObject> @NotNull List<List<String>> formatEntityRows(
      @NotNull List<QueryData<T>> results, @NotNull EntityType entityType, int maxRows) {
    List<List<String>> rows = new ArrayList<>();
    for (QueryData<T> qd : results.stream().limit(maxRows).toList()) {
      T entity = qd.data();
      if (entity == null) {
        rows.add(idOnlyRow(qd.id(), entityType));
      } else {
        rows.add(
            switch (entityType) {
              case PLAYER -> formatPlayer((Player) entity);
              case TOURNAMENT -> formatTournament((Tournament) entity);
              case ANNOTATOR -> formatAnnotator((Annotator) entity);
              case SOURCE -> formatSource((Source) entity);
              case TEAM -> formatTeam((Team) entity);
              case GAME_TAG -> formatGameTag((GameTag) entity);
            });
      }
    }
    return rows;
  }

  private static List<String> idOnlyRow(int id, EntityType entityType) {
    int columnCount = entityColumns(entityType).size();
    List<String> row = new ArrayList<>(columnCount);
    row.add(String.valueOf(id));
    for (int i = 1; i < columnCount; i++) {
      row.add("");
    }
    return row;
  }

  private static List<String> formatPlayer(Player p) {
    return List.of(String.valueOf(p.id()), p.getFullName(), String.valueOf(p.count()));
  }

  private static List<String> formatTournament(Tournament t) {
    return List.of(
        String.valueOf(t.id()),
        t.title(),
        t.date().toString(),
        t.category() > 0 ? String.valueOf(t.category()) : "",
        String.valueOf(t.count()));
  }

  private static List<String> formatAnnotator(Annotator a) {
    return List.of(String.valueOf(a.id()), a.name(), String.valueOf(a.count()));
  }

  private static List<String> formatSource(Source s) {
    return List.of(String.valueOf(s.id()), s.title(), s.publisher(), String.valueOf(s.count()));
  }

  private static List<String> formatTeam(Team t) {
    return List.of(
        String.valueOf(t.id()),
        t.title(),
        t.year() > 0 ? String.valueOf(t.year()) : "",
        String.valueOf(t.count()));
  }

  private static List<String> formatGameTag(GameTag g) {
    return List.of(String.valueOf(g.id()), g.englishTitle(), String.valueOf(g.count()));
  }
}
