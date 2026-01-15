package se.yarin.morphy.cli.tournaments;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.yarin.morphy.Database;
import se.yarin.morphy.cli.columns.GameColumn;
import se.yarin.morphy.cli.columns.TournamentColumn;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.qqueries.QueryResult;

import java.util.*;

public class StdoutTournamentsSummary implements TournamentConsumer {
  private static final Logger log = LogManager.getLogger();

  private Database currentDatabase; // Ugly hack, remove when the raw columns have been removed

  private final boolean showTotal;
  private final Collection<TournamentColumn> columns;

  protected int totalFoundTournaments = 0;
  protected int totalConsumedTournaments = 0;
  protected long totalSearchTime = 0;

  public static final String DEFAULT_COLUMNS =
      "title,place,date,type,nation,category,rounds,count,complete";

  public StdoutTournamentsSummary(boolean showTotal, Collection<TournamentColumn> columns) {
    this.showTotal = showTotal;
    this.columns = columns;
  }

  public static List<TournamentColumn> parseColumns(String columnSpec) {
    // TODO: This is duplicated in StdoutGamesSummary - move to separate class
    HashMap<String, Integer> specColumns = new HashMap<>();
    boolean isRelative = true;
    for (String col : columnSpec.split(",")) {
      String strippedCol = col.replaceAll("^[\\+\\-]", "").strip();
      int val;
      if (col.startsWith("+")) {
        val = 1;
      } else if (col.startsWith("-")) {
        val = -1;
      } else {
        isRelative = false;
        val = 1;
      }
      if (strippedCol.endsWith("*")) {
        String prefix = strippedCol.substring(0, strippedCol.length() - 1);
        for (GameColumn gc : GameColumn.ALL) {
          if (gc.getId().startsWith(prefix)) {
            specColumns.put(gc.getId(), val);
          }
        }
      } else {
        specColumns.put(strippedCol, val);
      }
    }
    if (isRelative) {
      for (String defaultCol : DEFAULT_COLUMNS.split(",")) {
        if (specColumns.getOrDefault(defaultCol, 1) != -1) {
          specColumns.put(defaultCol, 1);
        }
      }
    }

    ArrayList<TournamentColumn> visibleColumns = new ArrayList<>();
    HashSet<String> usedCols = new HashSet<>();
    for (TournamentColumn tournamentColumn : TournamentColumn.ALL) {
      if (specColumns.getOrDefault(tournamentColumn.getTournamentId(), 0) == 1) {
        visibleColumns.add(tournamentColumn);
      }
      if (specColumns.containsKey(tournamentColumn.getTournamentId())) {
        usedCols.add(tournamentColumn.getTournamentId());
      }
    }

    for (String col : specColumns.keySet()) {
      if (!usedCols.contains(col)) {
        log.warn("Unknown column: " + col);
        log.warn("Available columns: " + TournamentColumn.allColumnsString());
      }
    }

    return visibleColumns;
  }

  @Override
  public void init() {
    outputHeader();
  }

  @Override
  public void finish() {
    if (showTotal) {
      if (totalFoundTournaments == 0) {
        System.out.printf("No hits (%.2f s)%n", totalSearchTime / 1000.0);
      } else {
        System.out.println();
        if (totalConsumedTournaments < totalFoundTournaments) {
          System.out.printf(
              "%d out of %d hits displayed (%.2f s)%n",
              totalConsumedTournaments, totalFoundTournaments, totalSearchTime / 1000.0);
        } else {
          System.out.printf("%d hits  (%.2f s)%n", totalFoundTournaments, totalSearchTime / 1000.0);
        }
      }
    }
  }

  public void setCurrentDatabase(Database database) {
    this.currentDatabase = database;
  }

  @Override
  public void accept(Tournament tournament) {
    StringBuilder sb = new StringBuilder();
    TournamentColumn lastColumn = null;
    for (TournamentColumn column : columns) {
      int marginBefore =
          Math.max(column.marginLeft(), lastColumn == null ? 0 : lastColumn.marginRight());
      sb.append(" ".repeat(marginBefore));
      lastColumn = column;

      String value = column.getTournamentValue(currentDatabase, tournament);
      if (column.trimValueToWidth() && value.length() > column.width()) {
        value = value.substring(0, column.width());
      }
      sb.append(value);
      sb.append(" ".repeat(Math.max(0, column.width() - value.length())));
    }
    if (lastColumn != null) {
      sb.append(" ".repeat(lastColumn.marginRight()));
    }
    System.out.println(sb.toString());
  }

  private void outputHeader() {
    StringBuilder sb = new StringBuilder();
    TournamentColumn lastColumn = null;
    for (TournamentColumn column : columns) {
      int marginBefore =
          Math.max(column.marginLeft(), lastColumn == null ? 0 : lastColumn.marginRight());
      sb.append(" ".repeat(marginBefore));
      lastColumn = column;
      sb.append(column.getHeader());
      sb.append(" ".repeat(column.width() - column.getHeader().length()));
    }
    if (lastColumn != null) {
      sb.append(" ".repeat(lastColumn.marginRight()));
    }
    System.out.println(sb.toString());
    System.out.println("-".repeat(sb.length()));
  }

  @Override
  public void searchDone(QueryResult<Tournament> result) {
    totalFoundTournaments += result.total();
    totalConsumedTournaments += result.consumed();
    totalSearchTime += result.elapsedTime();
  }
}
