package se.yarin.morphy;

import org.junit.Test;
import se.yarin.chess.GameResult;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.games.RatingType;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DatabaseTransactionTest {

    @Test
    public void getGameWithoutExtendedHeaderWrite() throws IOException {
        // cbj file is missing
        File cbh_only = ResourceLoader.materializeDatabaseStream(Database.class, "database/cbh_test", "cbh_test");
        Database db = Database.open(cbh_only);
        try (var txn = new DatabaseReadTransaction(db)) {
            assertEquals(4, db.count());

            Game game1 = txn.getGame(1);
            assertEquals(0, game1.whitePlayerId());
            assertEquals(2200, game1.whiteElo());
            assertEquals(2, game1.round());
            assertEquals(1, game1.subRound());
            assertEquals(GameResult.DRAW, game1.result());

            Game game2 = txn.getGame(2);
            assertEquals(2820, game2.blackElo());

            Game game4 = txn.getGame(4);
            assertEquals(-1, game4.whiteTeamId());
            assertEquals(Nation.NONE, game4.whiteRatingType().nation());
        }
    }

    @Test
    public void getGameWithoutExtendedHeaderReadOnly() throws IOException {
        File cbh_only = ResourceLoader.materializeDatabaseStream(Database.class, "database/cbh_test", "cbh_test");
        Database db = Database.open(cbh_only, DatabaseMode.READ_ONLY);
        try (var txn = new DatabaseReadTransaction(db)) {
            assertEquals(4, db.count());

            Game game1 = txn.getGame(1);
            assertEquals(-1, game1.gameTagId());

            Game game4 = txn.getGame(4);
            assertEquals(-1, game4.whiteTeamId());
            assertNull(game4.whiteRatingType().nation());
        }
    }

    @Test
    public void getGameWithExtendedHeader() throws IOException {
        // cbj file exists
        File cbh_cbj = ResourceLoader.materializeDatabaseStream(Database.class, "database/cbh_cbj_test", "cbh_cbj_test");
        Database db = Database.open(cbh_cbj);
        try (var txn = new DatabaseReadTransaction(db)) {
            assertEquals(4, db.count());

            Game game1 = txn.getGame(1);
            assertEquals(0, game1.whitePlayerId());
            assertEquals(2200, game1.whiteElo());
            assertEquals(2, game1.round());
            assertEquals(1, game1.subRound());
            assertEquals(GameResult.DRAW, game1.result());
            assertEquals(0, game1.gameTagId());

            Game game2 = db.getGame(2);
            assertEquals(2820, game2.blackElo());
            assertEquals(1, game2.gameTagId());

            Game game4 = txn.getGame(4);
            assertEquals(2, game4.whiteTeamId());
            assertEquals(Nation.SWEDEN, game4.whiteRatingType().nation());
        }
    }

    @Test
    public void getGameWithShorterExtendedHeader() throws IOException {
        // cbj file exists but contains fewer games than the cbh file
        // Should work in non-strict mode
        File cbh_cbj = ResourceLoader.materializeDatabaseStream(Database.class, "database/shorter_cbj_test", "shorter_cbj_test");
        Database db = Database.open(cbh_cbj, DatabaseMode.READ_REPAIR);
        try (var txn = new DatabaseReadTransaction(db)) {
            assertEquals(4, db.count());
            assertEquals(4, db.gameHeaderIndex().count());
            assertEquals(3, db.extendedGameHeaderStorage().count());

            Game game1 = txn.getGame(1);
            assertEquals(2200, game1.whiteElo());

            Game game2 = txn.getGame(2);
            assertEquals(2820, game2.blackElo());
            assertEquals(1, game2.gameTagId());

            Game game4 = txn.getGame(4);  // missing from the cbj file
            assertEquals(-1, game4.whiteTeamId());
            assertNull(game4.whiteRatingType().nation());
        }
    }

    @Test
    public void getGameAfterOpenInMemoryWithExtendedHeaders() throws IOException {
        // cbj file exists
        File cbh_cbj = ResourceLoader.materializeDatabaseStream(Database.class, "database/cbh_cbj_test", "cbh_cbj_test");
        Database db = Database.open(cbh_cbj, DatabaseMode.IN_MEMORY);
        try (var txn = new DatabaseReadTransaction(db)) {
            assertEquals(4, db.count());

            Game game1 = txn.getGame(1);
            assertEquals(0, game1.whitePlayerId());
            assertEquals(2200, game1.whiteElo());
            assertEquals(0, game1.gameTagId());

            Game game2 = txn.getGame(2);
            assertEquals(2820, game2.blackElo());
            assertEquals(1, game2.gameTagId());

            Game game4 = txn.getGame(4);
            assertEquals(2, game4.whiteTeamId());
            assertEquals(Nation.SWEDEN, game4.whiteRatingType().nation());
        }
    }

    @Test
    public void getGameWithExtendedHeaderOldVersionReadOnly() throws IOException {
        // Older version of cbj file
        File hedgehog = ResourceLoader.materializeDatabaseStream(Database.class, "database/hedgehog", "Hedgehog");
        Database db = Database.open(hedgehog, DatabaseMode.READ_ONLY);
        assertEquals(8, db.extendedGameHeaderStorage().prolog().version());
        try (var txn = new DatabaseReadTransaction(db)) {
            Game game16 = txn.getGame(16);
            assertEquals(2555, game16.whiteElo());
            assertEquals(2, game16.whiteTeamId());  // In cbj file
            assertEquals(-1, game16.gameTagId());   // Not in this version of cbj, so default value
        }
    }

    @Test
    public void getGameAfterOpenInMemoryWithNoExtendedHeaders() throws IOException {
        // cbj file is missing
        File cbh_only = ResourceLoader.materializeDatabaseStream(Database.class, "database/cbh_test", "cbh_test");
        Database db = Database.open(cbh_only, DatabaseMode.IN_MEMORY);
        try (var txn = new DatabaseReadTransaction(db)) {
            assertEquals(4, db.count());

            Game game1 = db.getGame(1);
            assertEquals(2200, game1.whiteElo());
            assertEquals(-1, game1.gameTagId());

            Game game2 = txn.getGame(2);
            assertEquals(2820, game2.blackElo());
            assertEquals(-1, game2.gameTagId());

            Game game4 = txn.getGame(4);
            assertEquals(-1, game4.whiteTeamId());
            assertEquals(RatingType.unspecified(), game4.whiteRatingType());
        }
    }
}
