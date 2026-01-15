package se.yarin.morphy.tools;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentBase;
import se.yarin.cbhlib.entities.TournamentEntity;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.GameHeaderBase;
import se.yarin.cbhlib.entities.PlayerBase;
import se.yarin.cbhlib.entities.PlayerEntity;

import java.io.*;
import java.util.Iterator;

public class ExtractTopTournamentGames {
  // Find all games in Top tournaments and save headers in csv format
  // for further statistics processing

  public static void main(String[] args) throws IOException {
    // TODO: Iterating over all tournaments using the iterator doesn't seem to include all
    // tournaments!?
    Database db = Database.open(new File("testbases/Mega Database 2017/Mega Database 2017.cbh"));
    GameHeaderBase headerBase = db.getHeaderBase();
    PlayerBase playerBase = db.getPlayerBase();
    TournamentBase tb = db.getTournamentBase();

    BufferedWriter output = new BufferedWriter(new FileWriter("/Users/yarin/tmp/headers.csv"));

    long[] tcnt = new long[30], gcnt = new long[30];
    int total = 0;

    for (TournamentEntity entity : tb.getAll()) {
      total++;
      int category = entity.getCategory();
      if (category < 0 || category >= 30) {
        System.err.println("Invalid category " + category + " for tournament " + entity.getTitle());
      } else {
        tcnt[category]++;
        gcnt[category] += entity.getCount();
      }
      if (category >= 15) {
        getGames(headerBase, entity, playerBase, output);
      }
    }

    System.out.println(total + " tournaments");

    for (int i = 0; i < 30; i++) {
      System.out.println(
          String.format("Category %2d: %4d tournaments, %8d games", i, tcnt[i], gcnt[i]));
    }
    output.close();
  }

  private static void getGames(
      GameHeaderBase headers, TournamentEntity entity, PlayerBase players, BufferedWriter output)
      throws IOException {
    output.write(
        "gameId;whiteId;blackId;white;black;whiteElo;blackElo;result;noMoves;tourId;tour;tourTime;tourType;tourCategory;round;date\n");
    int found = 0, first = 0, last = 0;
    Iterator<GameHeader> iter = headers.stream(entity.getFirstGameId()).iterator();
    while (iter.hasNext() && found < entity.getCount()) {
      GameHeader header = iter.next();
      if (header.getTournamentId() == entity.getId()) {
        found++;
        if (!header.isGuidingText()) {
          PlayerEntity wp = players.get(header.getWhitePlayerId());
          PlayerEntity bp = players.get(header.getBlackPlayerId());
          output.write(
              String.format(
                  "%d;%d;%d;\"%s\";\"%s\";%d;%d;\"%s\";%d;%d;\"%s\";\"%s\";\"%s\";%d;%d;\"%s\"\n",
                  header.getId(),
                  header.getWhitePlayerId(),
                  header.getBlackPlayerId(),
                  wp == null ? "<null>" : wp.getFullName(),
                  bp == null ? "<null>" : bp.getFullName(),
                  header.getWhiteElo(),
                  header.getBlackElo(),
                  header.getResult().toString(),
                  header.getNoMoves(),
                  entity.getId(),
                  entity.getTitle(),
                  entity.getTimeControl().getName(),
                  entity.getType().getName(),
                  entity.getCategory(),
                  header.getRound(),
                  header.getPlayedDate().toString().replace(".", "-")));
        }

        if (first == 0) first = header.getId();
        last = header.getId();
      }
    }
    String complete = entity.isComplete() ? "complete" : "incomplete";
    if (found < entity.getCount()) {
      System.out.println(
          String.format(
              "Only found %d games in '%s'; expected %d. Tournament marked as %s.",
              found, entity.getTitle(), entity.getCount(), complete));
    } else {
      // System.out.println(String.format("Found all %d games in '%s' between %d and %d. Tournament
      // marked as %s.",
      //      found, entity.getTitle(), first, last, complete));
    }
  }
}
