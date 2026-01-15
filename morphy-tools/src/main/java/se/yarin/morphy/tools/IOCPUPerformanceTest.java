package se.yarin.morphy.tools;

import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.util.BlobChannel;
import se.yarin.util.PagedBlobChannel;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;

public class IOCPUPerformanceTest {
  private final Database db;
  private final Random random = new Random(0);

  public IOCPUPerformanceTest(Database db) {
    this.db = db;
  }

  public static void main(String[] args) throws IOException {
    Database db =
        Database.open(
            new File("/Users/yarin/chess/bases/Mega2021/Mega Database 2021.cbh"),
            DatabaseMode.READ_ONLY);
    //        db.queryPlanner().updateStatistics();
  }

  private void compareDeserializations() {
    try (var txn = new DatabaseReadTransaction(db)) {
      Runnable fetchTournaments =
          () -> {
            txn.getTournament(100);
            txn.getTournament(120);
            txn.getTournament(150);
            txn.getTournament(190);
          };
      Runnable fetchPlayers =
          () -> {
            txn.getPlayer(100);
            txn.getPlayer(120);
            txn.getPlayer(150);
            txn.getPlayer(190);
          };
      Runnable fetchGames =
          () -> {
            txn.getGame(100);
            txn.getGame(120);
            txn.getGame(150);
            txn.getGame(190);
          };
      Runnable fetchAnnotators =
          () -> {
            txn.getAnnotator(100);
            txn.getAnnotator(120);
            txn.getAnnotator(150);
            txn.getAnnotator(190);
          };
      fetchGames.run();
      long start = System.currentTimeMillis();
      for (int i = 0; i < 250000; i++) {
        fetchGames.run();
      }
      System.out.println(System.currentTimeMillis() - start + " ms");
      db.context().instrumentation().getCurrent().show(10);
    }

    // 1,000,000 annotators => 385 ms deserialization
    // 1,000,000 players => 567 ms deserialization
    // 1,000,000 tournaments => 791 ms deserialization
    // 1,000,000 games => 1514 ms deserialization
  }

  private void rawPageReadInOrder() throws IOException {
    clearPageCache();
    BlobChannel channel =
        BlobChannel.open(
            new File("/Users/yarin/chess/bases/Mega2021/Mega Database 2021.cbj").toPath());
    long start = System.currentTimeMillis();
    long offset = 0;
    int numPages = 0;

    while (offset < channel.size()) {
      channel.read(offset, PagedBlobChannel.PAGE_SIZE);
      offset += PagedBlobChannel.PAGE_SIZE;
      numPages += 1;
    }
    long elapsed = System.currentTimeMillis() - start;
    System.out.println(numPages + " pages read in " + elapsed + " ms");
  }

  private void rawPageReadRandom() throws IOException {
    clearPageCache();
    Random random = new Random();
    BlobChannel channel =
        BlobChannel.open(
            new File("/Users/yarin/chess/bases/Mega2021/Mega Database 2021.cbj").toPath());
    int totalPages = (int) (channel.size() / PagedBlobChannel.PAGE_SIZE);
    int N = 100000;
    long start = System.currentTimeMillis();

    for (int i = 0; i < N; i++) {
      int page = random.nextInt(totalPages);
      channel.read((long) page * PagedBlobChannel.PAGE_SIZE, PagedBlobChannel.PAGE_SIZE);
    }
    long elapsed = System.currentTimeMillis() - start;
    System.out.println(N + " pages read in " + elapsed + " ms");
  }

  private void scanVsScatteredRead() {
    Random random = new Random();
    int N = 340000;
    int[] playerId = new int[N];
    for (int i = 0; i < N; i++) {
      playerId[i] = i;
    }
    // random shuffle
    for (int i = 0; i < N; i++) {
      int t = random.nextInt(N - i);
      int tmp = playerId[i];
      playerId[i] = playerId[i + t];
      playerId[i + t] = tmp;
    }

    try (var txn = new DatabaseReadTransaction(db)) {
      long start = System.currentTimeMillis();
      /*
      for (int i = 0; i < N; i++) {
          txn.getPlayer(playerId[i]);
      }
       */
      txn.playerTransaction().stream().count();
      System.out.println(System.currentTimeMillis() - start + " ms");
      db.context().instrumentation().getCurrent().show(2);
    }
  }

  private void performanceTest() {
    for (int i = 0; i < 10; i++) {
      // readBatchedGames(10, 999);
      readSeqBatchGames(2500, 20);
      readRandomGames(50000);
    }
  }

  private void readBatchedGames(int numBatches, int gamesPerBatch) {
    long start = System.currentTimeMillis();
    try (var txn = new DatabaseReadTransaction(db)) {
      int dummy = 0;
      for (int i = 0; i < numBatches; i++) {
        int firstGame = random.nextInt(db.count() - gamesPerBatch) + 1;
        dummy +=
            txn.stream(firstGame, null).limit(gamesPerBatch).mapToInt(Game::whitePlayerId).sum();
      }
    }
    long elapsed = System.currentTimeMillis() - start;
    System.err.println("Batch read in " + elapsed + " ms");
    // db.context().instrumentation().show(2);
    System.err.println();
  }

  private void readSeqBatchGames(int numBatches, int gamesPerBatch) {
    long start = System.currentTimeMillis();
    try (var txn = new DatabaseReadTransaction(db)) {
      int dummy = 0;
      for (int i = 0; i < numBatches; i++) {
        int firstGame = random.nextInt(db.count() - gamesPerBatch) + 1;
        for (int j = 0; j < gamesPerBatch; j++) {
          dummy += txn.getGame(firstGame + j).whitePlayerId();
        }
      }
    }
    long elapsed = System.currentTimeMillis() - start;
    System.err.println("Seq batch read in " + elapsed + " ms");
    // db.context().instrumentation().show(2);
    System.err.println();
  }

  private void readRandomGames(int numGames) {
    long start = System.currentTimeMillis();
    try (var txn = new DatabaseReadTransaction(db)) {
      int dummy = 0;
      for (int i = 0; i < numGames; i++) {
        int gameId = random.nextInt(db.count()) + 1;
        dummy += txn.getGame(gameId).whitePlayerId();
      }
    }
    long elapsed = System.currentTimeMillis() - start;
    System.err.println("Random read in " + elapsed + " ms");
    // db.context().instrumentation().show(2);
    System.err.println();
  }

  public static void clearPageCache() {
    try {
      String[] cmd = {"/bin/bash", "-c", "sudo -S purge"};
      Process pb = Runtime.getRuntime().exec(cmd);
      /*
      String line;
      BufferedReader input = new BufferedReader(new InputStreamReader(pb.getInputStream()));
      while ((line = input.readLine()) != null) {
          System.out.println(line);
      }
      input.close();

       */
      System.out.println("Page cache cleared");
    } catch (IOException e) {
      throw new RuntimeException("Failed to clear page cache");
    }
  }
}
