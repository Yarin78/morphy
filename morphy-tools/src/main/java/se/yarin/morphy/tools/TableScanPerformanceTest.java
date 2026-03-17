package se.yarin.morphy.tools;

import java.io.File;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.games.filters.IsGameFilter;
import se.yarin.morphy.games.filters.RatingRangeFilter;
import se.yarin.morphy.games.filters.RatingRangeFilter.RatingColor;
import se.yarin.morphy.query.GameHeaderIdIndexScan;
import java.io.IOException;
import se.yarin.morphy.query.TableScan;
import se.yarin.morphy.storage.ItemStorageFilter;

public class TableScanPerformanceTest {
  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      System.err.println("Usage: TableScanPerformanceTest <database.cbh>");
      System.exit(1);
    }

    File file = new File(args[0]);
    ItemStorageFilter<GameHeader> filter;
    filter = new RatingRangeFilter(2500, 9999, RatingColor.ANY);
    // filter = new IsGameFilter();

    try (Database db = Database.open(file, DatabaseMode.READ_ONLY)) {
      int totalGames = db.gameHeaderIndex().count();
      System.out.printf("Database: %s (%d games)%n", file.getName(), totalGames);
      System.out.printf("Filter: %s%n%n", filter);

      // Warm up
      try (var txn = new DatabaseReadTransaction(db)) {
        TableScan.gameHeaders(txn, filter).stream().count();
      }

      System.out.println("Warmup done");

      long t0;

      // TableScan
      long tableScanCount;
      t0 = System.nanoTime();
      try (var txn = new DatabaseReadTransaction(db)) {
        tableScanCount = TableScan.gameHeaders(txn, filter).stream().count();
      }
      long tableScanMs = (System.nanoTime() - t0) / 1_000_000;

      // GameHeaderIdIndexScan
      long idIndexScanCount;
      t0 = System.nanoTime();
      try (var txn = new DatabaseReadTransaction(db)) {
        idIndexScanCount = new GameHeaderIdIndexScan(txn, filter).stream().count();
      }
      long idIndexScanMs = (System.nanoTime() - t0) / 1_000_000;

      System.out.printf("%-30s %6d matches in %5d ms%n", "TableScan:", tableScanCount, tableScanMs);
      System.out.printf(
          "%-30s %6d matches in %5d ms%n",
          "GameHeaderIdIndexScan:", idIndexScanCount, idIndexScanMs);
    }
  }
}
