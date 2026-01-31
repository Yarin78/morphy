package se.yarin.morphy.tools;

import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;

import java.io.File;
import java.io.IOException;

/**
 * Simple tool to inspect unknown1 and unknown2 fields in ExtendedGameHeader.
 * Opens a CBH database, iterates through all games, and prints out games
 * where unknown1 or unknown2 are not zero.
 */
public class InspectUnknownFields {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: InspectUnknownFields <database-path>");
            System.err.println("Example: InspectUnknownFields /path/to/database.cbh");
            System.exit(1);
        }

        String databasePath = args[0];
        File dbFile = new File(databasePath);

        if (!dbFile.exists()) {
            System.err.println("Database file not found: " + databasePath);
            System.exit(1);
        }

        System.out.println("Opening database: " + databasePath);
        Database db = Database.open(dbFile, DatabaseMode.READ_ONLY);

        int totalGames = 0;
        int gamesWithUnknown1 = 0;
        int gamesWithUnknown2 = 0;

        try (var txn = new DatabaseReadTransaction(db)) {
            for (Game game : txn.iterable()) {
                if (game.guidingText()) {
                    continue;
                }

                totalGames++;

                int unknown1 = game.extendedHeader().unknown1();
                int unknown2 = game.extendedHeader().unknown2();

                if (unknown1 != 0 || unknown2 != 0) {
                    System.out.println("Game #" + game.id() + ":");
                    System.out.println("  White: " + game.white().getFullName());
                    System.out.println("  Black: " + game.black().getFullName());

                    if (unknown1 != 0) {
                        System.out.println("  unknown1: " + unknown1 + " (0x" + Integer.toHexString(unknown1) + ")");
                        gamesWithUnknown1++;
                    }

                    if (unknown2 != 0) {
                        System.out.println("  unknown2: " + unknown2 + " (0x" + Integer.toHexString(unknown2) + ")");
                        gamesWithUnknown2++;
                    }

                    System.out.println();
                }

                if (totalGames % 10000 == 0) {
                    System.out.println("Processed " + totalGames + " games...");
                }
            }
        }

        db.close();

        System.out.println("========================================");
        System.out.println("SUMMARY");
        System.out.println("========================================");
        System.out.println("Total games processed: " + totalGames);
        System.out.println("Games with unknown1 != 0: " + gamesWithUnknown1);
        System.out.println("Games with unknown2 != 0: " + gamesWithUnknown2);
    }
}
