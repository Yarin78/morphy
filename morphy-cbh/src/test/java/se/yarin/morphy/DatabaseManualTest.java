package se.yarin.morphy;

import org.junit.Test;
import se.yarin.chess.Date;
import se.yarin.chess.GameHeaderModel;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.games.ImmutableExtendedGameHeader;
import se.yarin.morphy.games.ImmutableGameHeader;

import static org.junit.Assert.assertEquals;

public class DatabaseManualTest extends DatabaseTestSetup {

    @Test
    public void newAddSingleGameToEmptyDatabase() {
        Database db = new Database();

        assertEquals(db.count(), 0);
        assertEquals(db.playerIndex().count(), 0);

        DatabaseTransaction txn = new DatabaseTransaction(db);
        putTestGame(txn, 0, "Aronian - Ding", "tour2", null, null, "t1 - t2", null, 0, 20, 0, 50);
        txn.commit();

        assertEquals(db.count(), 1);
        assertEquals(db.playerIndex().count(), 2);
        assertEquals("Aronian", db.playerIndex().get(0).lastName());
        assertEquals("Ding", db.playerIndex().get(1).lastName());

        assertEquals(db.teamIndex().count(), 2);
        assertEquals("t1", db.teamIndex().get(0).title());
    }

    @Test
    public void addSingleGameWithExistingEntities() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 0, "Aronian - Ding", "tour2", null, null, null, null, 0, 20, 0, 50);
        txn.commit();

        assertEquals(16, testBase.count());
        assertEquals(2, testBase.playerIndex().get(Player.of("Aronian", "")).count());
    }

    @Test
    public void addSingleGameWithNewPlayer() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 0, "Mardell, Jimmy - Carlsen", null, null, null, null, null, 0, 20, 0, 50);
        txn.commit();

        assertEquals(11, testBase.playerIndex().count());
        assertEquals("Mardell", testBase.playerIndex().get(10).lastName());
        assertEquals("Jimmy", testBase.playerIndex().get(10).firstName());
    }

    @Test
    public void replaceSingleGame() {
        assertEquals(4, playerCount("Ding"));
        assertEquals(5, playerCount("Carlsen"));
        assertEquals(1, playerCount("Aronian"));
        assertEquals(4, playerCount("So"));
        assertEquals(5, tournamentCount("tour2", Date.unset()));

        assertEquals(0, wastedMoveBytes());
        assertEquals(0, wastedAnnotationBytes());

        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        // Replacing a game between Ding - Carlsen; tour3, ann2
        putTestGame(txn, 13, "Aronian - So", "tour2", "ann2", null, null, null, 500, 0, 0, 80);
        txn.commit();

        assertEquals(3, playerCount("Ding"));
        assertEquals(4, playerCount("Carlsen"));
        assertEquals(2, playerCount("Aronian"));
        assertEquals(5, playerCount("So"));
        assertEquals(6, tournamentCount("tour2", Date.unset()));

        assertEquals(20, wastedMoveBytes());
        assertEquals(1500, wastedAnnotationBytes());
    }

    @Test
    public void replaceLastGameLeavingMoveGapAtTheEnd() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 15, "Carlsen - So", "tour1", null, null, null, null, 0, 15, 0, 90);
        txn.commit();

        assertEquals(10, wastedMoveBytes());
    }

    @Test
    public void replaceLastGameWithAnnotations() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 15, "Carlsen - So", "tour1", null, null, null, null, 500, 15, 15, 100);
        txn.commit();

        assertEquals(0, wastedAnnotationBytes());
    }

    @Test
    public void replaceSingleGameCausingFirstGameToChange() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);

        assertEquals(3, playerFirstGameId("Mamedyarov"));
        assertEquals(3, playerFirstGameId("So"));
        assertEquals(4, playerFirstGameId("Caruana"));

        putTestGame(txn, 3, "Caruana - Mardell", 50, 0, 0, 0);
        txn.commit();

        assertEquals(11, playerFirstGameId("Mamedyarov"));
        assertEquals(7, playerFirstGameId("So"));
        assertEquals(3, playerFirstGameId("Caruana"));
        assertEquals(3, playerFirstGameId("Mardell"));
    }

    @Test
    public void replaceSingleGameCausingEntityToBeRemoved() {
        assertEquals(10, testBase.playerIndex().count());

        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 12, "Ding - Giri", 50, 0, 0, 0);
        txn.commit();

        assertEquals(9, testBase.playerIndex().count());
        assertEquals(0, playerCount("Aronian"));
    }

    @Test
    public void replaceSingleGameWithMoreMoves() {
        long oldMovesOffset = testBase.getGame(6).getMovesOffset();
        assertEquals(generateMovesBlob(100, 6), testBase.getGame(6).getMovesBlob());;

        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 5, "Caruana - Radjabov", 250, 0, 20, 0);
        txn.commit();

        assertEquals(generateMovesBlob(250, 20), testBase.getGame(5).getMovesBlob());

        assertEquals(150 + oldMovesOffset, testBase.getGame(6).getMovesOffset());
        assertEquals(generateMovesBlob(100, 6), testBase.getGame(6).getMovesBlob());
    }

    @Test
    public void replaceSingleGameWithMoreAnnotations() {
        long oldAnnotationOffset = testBase.getGame(6).getAnnotationOffset();
        assertEquals(generateAnnotationsBlob(2000, 6), testBase.getGame(6).getAnnotationsBlob());;

        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 4, "Nepo - Caruana", 50, 10000, 20, 20);
        txn.commit();

        assertEquals(generateAnnotationsBlob(10000, 20), testBase.getGame(4).getAnnotationsBlob());

        assertEquals(8000 + oldAnnotationOffset, testBase.getGame(6).getAnnotationOffset());
        assertEquals(generateAnnotationsBlob(2000, 6), testBase.getGame(6).getAnnotationsBlob());
    }

    @Test
    public void replaceSingleGameIntroducingAnnotations() {
        long oldAnnotationOffset = testBase.getGame(13).getAnnotationOffset();
        assertEquals(generateAnnotationsBlob(2000, 13), testBase.getGame(13).getAnnotationsBlob());;

        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 10, "Ding - Giri", 100, 5000, 20, 21);
        txn.commit();

        assertEquals(generateAnnotationsBlob(5000, 21), testBase.getGame(10).getAnnotationsBlob());

        assertEquals(5000 + oldAnnotationOffset, testBase.getGame(13).getAnnotationOffset());
        assertEquals(generateAnnotationsBlob(2000, 13), testBase.getGame(13).getAnnotationsBlob());
    }

    @Test
    public void replaceSingleGameRemovingAnnotations() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 4, "Nepo - Caruana", 100, 0, 20, 0);
        txn.commit();

        assertEquals(0, testBase.getGame(4).getAnnotationOffset());

        assertEquals(2000, wastedAnnotationBytes());
    }

    @Test
    public void replaceGameContainingSameEntityTwice() {
        assertEquals(5, playerCount("Carlsen"));

        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 8, "Nepo - Caruana", 100, 0, 20, 0);
        txn.commit();

        assertEquals(3, playerCount("Carlsen"));
    }

    @Test
    public void addGameWithSameEntityTwice() {
        assertEquals(3, playerCount("Giri"));

        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 0, "Giri - Giri", 100, 0, 20, 0);
        txn.commit();

        assertEquals(5, playerCount("Giri"));
    }

    @Test
    public void addMultipleGamesInBatch() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 0, "Nepo - Caruana", 100, 0, 16, 0);
        putTestGame(txn, 0, "Carlsen - Mamedyarov", 120, 500, 17, 17);
        txn.commit();

        assertEquals(17, testBase.count());
    }

    @Test
    public void addGameAndThenReplaceOlderCausingInsertInBatch() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 0, "So - Ding", 100, 200, 16, 16);
        putTestGame(txn, 5, "Carlsen - Radjabov", 930, 2900, 17, 17);
        txn.commit();

        assertEquals(16, testBase.count());
        assertEquals(generateMovesBlob(100, 16), testBase.getGame(16).getMovesBlob());;
        assertEquals(generateAnnotationsBlob(200, 16), testBase.getGame(16).getAnnotationsBlob());;
    }

    @Test
    public void replaceSameGameMultipleTimesInBatch() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 3, "Nepo - Caruana", 130, 1500, 16, 16);
        putTestGame(txn, 3, "Carlsen - Mamedyarov", 90, 1700, 17, 17);
        txn.commit();

        assertEquals(5, playerCount("Nepo"));
        assertEquals(6, playerCount("Carlsen"));
        assertEquals("Carlsen", testBase.getGame(3).white().lastName());
        assertEquals(15, testBase.count());
        assertEquals(10, wastedMoveBytes());
        assertEquals(300, wastedAnnotationBytes());
    }

    @Test
    public void removeAndRestoreAnnotationsToSameGameInBatch() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 4, "Nepo - Caruana", 100, 0, 20, 0);
        putTestGame(txn, 4, "Nepo - Caruana", 100, 50, 20, 9);
        txn.commit();

        assertEquals(generateAnnotationsBlob(50, 9), testBase.getGame(4).getAnnotationsBlob());;
        assertEquals(1950, wastedAnnotationBytes());
    }

    @Test
    public void replaceAddedGameInBatch() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        int id = putTestGame(txn, 0, "Nepo - Caruana", 100, 0, 16, 0);
        assertEquals(16, id);
        putTestGame(txn, 16, "Carlsen - Mamedyarov", 90, 1700, 17, 17);
        txn.commit();

        assertEquals(5, playerCount("Nepo"));
        assertEquals(6, playerCount("Carlsen"));
        assertEquals(0, wastedMoveBytes());
    }

    @Test
    public void addGameWithAnnotationsAndReplaceLastGameWithAnnotations() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        int id = putTestGame(txn, 0, "Nepo - Caruana", 100, 1500, 16, 16);
        assertEquals(16, id);
        putTestGame(txn, 15, "Carlsen - Mamedyarov", 90, 1700, 17, 17);
        txn.commit();

        assertEquals(generateAnnotationsBlob(1500, 16), testBase.getGame(16).getAnnotationsBlob());;
    }

    @Test
    public void replaceGamesAndAddAnnotationsToThem() {
        // The annotations in both these games will want to go to the same annotation offset
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 11, "So - Mamedyarov", 100, 1300, 11, 11);
        putTestGame(txn, 10, "Ding - Giri", 100, 900, 10, 10);
        txn.commit();

        assertEquals(generateAnnotationsBlob(1300, 11), testBase.getGame(11).getAnnotationsBlob());;
        assertEquals(generateAnnotationsBlob(900, 10), testBase.getGame(10).getAnnotationsBlob());;
        assertEquals(generateAnnotationsBlob(2000, 13), testBase.getGame(13).getAnnotationsBlob());;
    }

    @Test
    public void addTwoGamesThenMakeFirstOneShorterInSameBatch() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 0, "Aronian - Mamedyarov", 120, 1300, 16, 16);
        putTestGame(txn, 0, "Giri - Caruana", 130, 900, 17, 17);
        putTestGame(txn, 16, "Carlsen - Grischuk", 110, 1200, 16, 16);
        txn.commit();

        assertEquals(0, wastedMoveBytes());
        assertEquals(0, wastedAnnotationBytes());
    }

    @Test
    public void multipleAddsAndReplacesInSameBatchInMixedOrder() {

    }

    @Test
    public void addGameCausingNewEntitiesOfAllTypesToBeAdded() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 0, "foo - bar", "foo", "foo", "foo", "foo - bar",
                "foo", 100, 0, 0, 100);
        txn.commit();

        Player fooPlayer = testBase.playerIndex().get(Player.ofFullName("foo"));
        Player barPlayer = testBase.playerIndex().get(Player.ofFullName("bar"));
        Tournament fooTournament = testBase.tournamentIndex().get(Tournament.of("foo", Date.unset()));
        Annotator fooAnnotator = testBase.annotatorIndex().get(Annotator.of("foo"));
        Source fooSource = testBase.sourceIndex().get(Source.of("foo"));
        Team fooTeam = testBase.teamIndex().get(Team.of("foo"));
        Team barTeam = testBase.teamIndex().get(Team.of("bar"));
        GameTag fooGameTag = testBase.gameTagIndex().get(1);
        assertEquals("foo", fooGameTag.englishTitle());

        assertEquals(1, fooPlayer.count());
        assertEquals(1, barPlayer.count());
        assertEquals(1, fooTournament.count());
        assertEquals(1, fooAnnotator.count());
        assertEquals(1, fooSource.count());
        assertEquals(1, fooTeam.count());
        assertEquals(1, barTeam.count());
        assertEquals(1, fooGameTag.count());
    }

    @Test
    public void addGameWithRenamedPlayerEntity() {
        // rename existing entity (key field), add new game using this entity
        DatabaseTransaction txn = new DatabaseTransaction(testBase);

        txn.updatePlayerById(0, Player.ofFullName("Carlsen, Magnus"));
        putTestGame(txn, 0, "Carlsen, Magnus - Grischuk", 100, 0, 16, 0);
        txn.commit();

        assertEquals(6, playerCount("Carlsen, Magnus"));
        assertEquals(0, playerCount("Carlsen"));
    }

    @Test
    public void addGameWithPreviouslyNamedPlayerEntity() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);

        txn.updatePlayerById(0, Player.ofFullName("Carlsen, Magnus"));
        putTestGame(txn, 0, "Carlsen - Grischuk", 100, 0, 16, 0);
        txn.commit();

        assertEquals(5, playerCount("Carlsen, Magnus"));
        assertEquals(1, playerCount("Carlsen"));
    }

    @Test
    public void addGameWithNewTournamentIncludingExtraFields() {

    }

    @Test
    public void addGameWithExistingTournamentHavingMoreDetails() {
        // If a game references an existing tournament and there are more details set,
        // it will _not_ get updated
    }

    @Test
    public void addGameWithUpdatedTournamentEntity() {
        // change metadata field for tournament (including extra storage fields),
        // add new game specifying the tournament but with fewer fields specified
    }

    @Test
    public void getEntityThatWasCreatedInSameBatch() {

    }

    @Test
    public void getUpdatedEntityInSameBatch() {
        // Change some additional fields
    }

    @Test
    public void transactionIsIsolated() {

    }

    @Test
    public void committingTwoTransactionsFromTheSameVersionFails() {

    }

}
