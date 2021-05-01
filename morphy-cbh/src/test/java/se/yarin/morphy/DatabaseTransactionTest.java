package se.yarin.morphy;

import org.junit.Test;
import se.yarin.chess.Date;
import se.yarin.chess.GameModel;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class DatabaseTransactionTest extends DatabaseTestSetup {

    @Test
    public void newAddSingleGameToEmptyDatabase() {
        Database db = new Database();

        assertEquals(db.count(), 0);
        assertEquals(db.playerIndex().count(), 0);

        DatabaseTransaction txn = new DatabaseTransaction(db);
        putTestGame(txn, 0, "Aronian - Ding", "tour2", null, null, "t1 - t2", null, 50, 0, 20, 0);
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
        putTestGame(txn, 0, "Aronian - Ding", "tour2", null, null, null, null, 50, 0, 20, 0);
        txn.commit();

        assertEquals(16, testBase.count());
        assertEquals(2, testBase.playerIndex().get(Player.of("Aronian", "")).count());
    }

    @Test
    public void addSingleGameWithNewPlayer() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 0, "Mardell, Jimmy - Carlsen", null, null, null, null, null, 50, 0, 20, 0);
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
        putTestGame(txn, 13, "Aronian - So", "tour2", "ann2", null, null, null, 80, 500, 0, 0);
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
        putTestGame(txn, 15, "Carlsen - So", "tour1", null, null, null, null, 90, 0, 15, 0);
        txn.commit();

        assertEquals(10, wastedMoveBytes());
    }

    @Test
    public void replaceLastGameWithAnnotations() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 15, "Carlsen - So", "tour1", null, null, null, null, 100, 500, 15, 15);
        txn.commit();

        assertEquals(0, wastedAnnotationBytes());
    }

    @Test
    public void replaceSingleGameCausingFirstGameToChange() {
        assertEquals(3, playerFirstGameId("Mamedyarov"));
        assertEquals(3, playerFirstGameId("So"));
        assertEquals(4, playerFirstGameId("Caruana"));

        DatabaseTransaction txn = new DatabaseTransaction(testBase);
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
        int id = putTestGame(txn, 0, "Nepo - Caruana", 100, 0, 16, 0).id();
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
        int id = putTestGame(txn, 0, "Nepo - Caruana", 100, 1500, 16, 16).id();
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
    public void addGameCausingNewEntitiesOfAllTypesToBeAdded() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 0, "foo - bar", "foo", "foo", "foo", "foo - bar",
                "foo", 100, 100, 0, 0);
        txn.commit();

        Player fooPlayer = testBase.playerIndex().get(Player.ofFullName("foo"));
        Player barPlayer = testBase.playerIndex().get(Player.ofFullName("bar"));
        Tournament fooTournament = testBase.tournamentIndex().get(Tournament.of("foo", Date.unset()));
        Annotator fooAnnotator = testBase.annotatorIndex().get(Annotator.of("foo"));
        Source fooSource = testBase.sourceIndex().get(Source.of("foo"));
        Team fooTeam = testBase.teamIndex().get(Team.of("foo"));
        Team barTeam = testBase.teamIndex().get(Team.of("bar"));
        GameTag fooGameTag = testBase.gameTagIndex().get(0);
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
    public void addGameWithNoEntitiesSpecified() {
        // test correct defaults used in GameHeaderModel
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        int id = txn.addGame(new GameModel()).id();
        txn.commit();

        Game game = testBase.getGame(id);
        assertEquals("", game.white().getFullName());
        assertEquals("", game.black().getFullName());
        assertEquals("", game.annotator().name());
        assertEquals("", game.source().title());
        assertNull(game.whiteTeam());
        assertNull(game.blackTeam());
        assertNull(game.gameTag());
    }

    @Test
    public void addGameWithRenamedPlayerEntity() {
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
    public void updateNewlyCreatedEntityInSameBatch() {
        GameModel gameModel = new GameModel();
        gameModel.header().setEvent("mytour");

        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        int id = txn.addGame(gameModel).id();
        Game game = txn.getGame(id);
        txn.updateTournamentById(
                game.tournamentId(),
                Tournament.of("renamed tour", Date.unset()),
                ImmutableTournamentExtra.builder().longitude(50).build());
        txn.commit();

        Game addedGame = testBase.getGame(id);
        assertEquals("renamed tour", addedGame.tournament().title());
        assertEquals(50, addedGame.tournamentExtra().longitude(), 1e-6);

        assertNull(testBase.tournamentIndex().get(Tournament.of("mytour", Date.unset())));
    }

    @Test
    public void addGameWithNewEntityThenReplaceWithExistingEntity() {
        // The temporary new entity shouldn't exist after commit,
        // but capacity should be increased (this is implementation dependent)
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 0, "foo - Carlsen", 100, 0, 16, 0);
        putTestGame(txn, 16, "Caruana - Carlsen", 100, 0, 16, 0);
        txn.commit();

        assertEquals(11, testBase.playerIndex().capacity());
        assertEquals(10, testBase.playerIndex().count());
        assertNull(testBase.playerIndex().get(Player.ofFullName("foo")));
    }

    @Test
    public void addGameFromSameDatabasePreferExplicitId() {
        GameModel gameModel = testBase.getGameModel(1);
        assertEquals("Carlsen", gameModel.header().getWhite());
        gameModel.header().setWhite("foo");

        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        txn.addGame(gameModel);
        txn.commit();

        assertEquals("Carlsen", testBase.getGame(16).white().getFullName());
    }

    @Test
    public void addGameFromDifferentDatabaseDoesNotUseExplicitId() {
        GameModel gameModel = testBase.getGameModel(1);
        assertEquals("Carlsen", gameModel.header().getWhite());
        gameModel.header().setWhite("foo");

        Database database = new Database();
        DatabaseTransaction txn = new DatabaseTransaction(database);
        txn.addGame(gameModel);
        txn.commit();

        assertEquals("foo", database.getGame(1).white().getFullName());
    }

    @Test(expected = MorphyInvalidDataException.class)
    public void addGameWithInvalidExplicitEntityIdSet() {
        // WHITE_ID is set in a game model but pointing to a missing player
        GameModel gameModel = testBase.getGameModel(1);
        assertEquals("Carlsen", gameModel.header().getWhite());
        gameModel.header().setField(GameAdapter.WHITE_ID, 100);

        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        try {
            txn.addGame(gameModel);
            txn.commit();
        }
        finally {
            txn.rollback();
        }
    }

    @Test
    public void addGameWithNewSource() {
        // Check that if a new source is created when a game is added, all the metadata fields are also set
        GameModel gameModel = new GameModel();
        gameModel.header().setSourceTitle("my source");
        gameModel.header().setSource("publisher");
        gameModel.header().setSourceDate(new Date(2021, 4, 1));

        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        int id = txn.addGame(gameModel).id();
        txn.commit();

        Game game = testBase.getGame(id);
        Source source = game.source();

        assertEquals("my source", source.title());
        assertEquals("publisher", source.publisher());
        assertEquals(new Date(2021, 4, 1), source.date());
    }

    @Test
    public void addGameWithNewTournamentIncludingExtraFields() {
        // Check that if a new event is created when a game is added, all the metadata fields are also set
        // For tournaments it's a bit special since there's TournamentExtra
        GameModel gameModel = new GameModel();
        gameModel.header().setEvent("tour");
        gameModel.header().setEventSite("London");
        gameModel.header().setEventCategory(10);
        gameModel.header().setEventCountry("SWE");
        gameModel.header().setEventTimeControl("blitz");
        gameModel.header().setEventDate(new Date(2021, 4, 1));
        gameModel.header().setEventEndDate(new Date(2021, 4, 5));

        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        int id = txn.addGame(gameModel).id();
        txn.commit();

        Game game = testBase.getGame(id);
        Tournament tournament = game.tournament();
        TournamentExtra tournamentExtra = game.tournamentExtra();

        assertEquals("tour", tournament.title());
        assertEquals("London", tournament.place());
        assertEquals(10, tournament.category());
        assertEquals(Nation.SWEDEN, tournament.nation());
        assertEquals(TournamentTimeControl.BLITZ, tournament.timeControl());
        assertEquals(new Date(2021, 4, 1), tournament.date());
        assertEquals(new Date(2021, 4, 5), tournamentExtra.endDate());
    }

    @Test
    public void addGameWithExistingTournamentHavingMoreDetails() {
        // If a game references an existing tournament and there are more details set,
        // it will _not_ get updated

        GameModel gameModel1 = new GameModel();
        gameModel1.header().setEvent("tour1");  // exists
        gameModel1.header().setEventRounds(5);  // will not be set
        gameModel1.header().setEventEndDate(new Date(2021, 4, 10));

        GameModel gameModel2 = new GameModel();
        gameModel2.header().setEvent("newtour"); // doesn't exist
        gameModel2.header().setEventRounds(8);   // so this will be set
        gameModel2.header().setEventEndDate(new Date(2021, 4, 15));

        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        txn.addGame(gameModel1);
        txn.addGame(gameModel2);
        txn.commit();

        Tournament tour1 = testBase.tournamentIndex().get(Tournament.of("tour1", Date.unset()));
        assertEquals(0, tour1.rounds());
        assertEquals(Date.unset(), testBase.tournamentExtraStorage().get(tour1.id()).endDate());

        Tournament tour2 = testBase.tournamentIndex().get(Tournament.of("newtour", Date.unset()));
        assertEquals(8, tour2.rounds());
        assertEquals(new Date(2021, 4, 15), testBase.tournamentExtraStorage().get(tour2.id()).endDate());
    }

    @Test
    public void useEntityFromEntityTransaction() {
        GameModel gameModel = new GameModel();
        gameModel.header().setWhite("Carlsen, Magnus");

        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        txn.updatePlayerById(0, Player.ofFullName("Carlsen, Magnus"));
        int id = txn.addGame(gameModel).id();
        txn.commit();

        assertEquals("Carlsen, Magnus", testBase.getGame(id).white().getFullName());
    }

    @Test
    public void getEntityInTransaction() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        Player player = txn.getPlayer(0);
        assertEquals("Carlsen", player.getFullName());
        txn.rollback();
    }

    @Test(expected = IllegalArgumentException.class)
    public void getInvalidEntityInTransactionNegativeId() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        try {
            txn.getPlayer(-1);
        } finally {
            txn.rollback();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void getInvalidEntityInTransactionTooHighId() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        try {
            txn.getTournament(1000);
        } finally {
            txn.rollback();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateInvalidPlayer() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        try {
            txn.updatePlayerById(-1, Player.ofFullName("Carlsen, Magnus"));
        } finally {
            txn.rollback();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateInvalidAnnotator() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        try {
            txn.updateAnnotatorById(1000, Annotator.of("foo"));
        } finally {
            txn.rollback();
        }
    }

    @Test
    public void entityCountAndFirstGameIsIncorrectInTransaction() {
        // We don't keep the first game and count statistics updated during the transaction
        // If this is fixed, then update this test.
        int aronianId = testBase.playerIndex().get(Player.ofFullName("Aronian")).id();

        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        Game game = putTestGame(txn, 5, "Aronian - Mardell", 100, 0, 1, 0);

        Player aronian = txn.getPlayer(aronianId);
        assertEquals(12, aronian.firstGameId());
        assertEquals(1, aronian.count());

        Player mardell = game.black();
        int mardellId = mardell.id();
        assertEquals("Mardell", mardell.getFullName());
        assertEquals(0, mardell.firstGameId());
        assertEquals(0, mardell.count());

        txn.commit();
        aronian = testBase.playerIndex().get(aronianId);
        assertEquals(5, aronian.firstGameId());
        assertEquals(2, aronian.count());

        mardell = testBase.playerIndex().get(mardellId);
        assertEquals(5, mardell.firstGameId());
        assertEquals(1, mardell.count());
    }

    @Test
    public void getEntityAfterLogicalDeleteInTransaction() {
        int playerId = testBase.playerIndex().get(Player.ofFullName("Aronian")).id();
        assertTrue(playerId >= 0);

        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        // This removes Aronian
        putTestGame(txn, 12, "Nepo - Caruana", 100, 0, 1, 0);

        // Can still get player in transaction, with old count
        Player player = txn.getPlayer(playerId);
        assertEquals(1, player.count());

        txn.rollback();
    }

    @Test(expected = IllegalArgumentException.class)
    public void getEntityAfterLogicalDeleteAfterCommit() {
        int playerId = testBase.playerIndex().get(Player.ofFullName("Aronian")).id();
        assertTrue(playerId >= 0);

        // Update a game in the database so that one entity is deleted
        // Then get that entity in the same transaction
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        // This removes Aronian
        putTestGame(txn, 12, "Nepo - Caruana", 100, 0, 1, 0);
        txn.commit();

        testBase.playerIndex().get(playerId);
    }

    @Test
    public void setEntityAfterGameUpdate() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        Game game = putTestGame(txn, 0, "Kasparov - Carlsen", "new tour", "new anno", "new source", "q1 - q2", "tag", 100, 200, 1, 2);
        txn.updatePlayerById(game.whitePlayerId(), Player.ofFullName("Kasparov, Garry"));
        txn.updateTournamentById(game.tournamentId(), Tournament.of("updated tour", "place", new Date(2021, 1, 2)),
                ImmutableTournamentExtra.builder().longitude(50).build());
        txn.updateAnnotatorById(game.annotatorId(), Annotator.of("updated annotator"));
        txn.updateSourceById(game.sourceId(), ImmutableSource.builder().title("updated source").publisher("publisher").build());
        txn.updateTeamById(game.whiteTeamId(), Team.of("updated team", 2, true, 2021, Nation.SWEDEN));
        txn.updateGameTagById(game.gameTagId(), ImmutableGameTag.builder().englishTitle("eng").germanTitle("ger").build());
        txn.commit();

        Game committedGame = testBase.getGame(game.id());
        assertEquals(Player.ofFullName("Kasparov, Garry"), committedGame.white());
        assertEquals(Tournament.of("updated tour", "place", new Date(2021, 1, 2)), committedGame.tournament());
        assertEquals(ImmutableTournamentExtra.builder().longitude(50).build(), committedGame.tournamentExtra());
        assertEquals(Annotator.of("updated annotator"), committedGame.annotator());
        assertEquals(ImmutableSource.builder().title("updated source").publisher("publisher").build(), committedGame.source());
        assertEquals(Team.of("updated team", 2, true, 2021, Nation.SWEDEN), committedGame.whiteTeam());
        assertEquals(ImmutableGameTag.builder().englishTitle("eng").germanTitle("ger").build(), committedGame.gameTag());
    }

    @Test
    public void multipleAddsAndReplacesInSameBatchInMixedOrder() {
        List<Consumer<DatabaseTransaction>> operations = Arrays.asList(
                // #16 Add new game with new player, no annotations
                (DatabaseTransaction txn) -> putTestGame(txn, 0, "Ding - Mardell", 120, 0, 16, 0),
                // Update old game, changing first game id of one player, creating new entites
                (DatabaseTransaction txn) -> putTestGame(txn, 5, "Carlsen - So", "new tour", "new annotator", "new source", "q1 - q2", "taggy", 190, 130, 5, 16),
                // Update old game, adding annotations to existing game
                (DatabaseTransaction txn) -> putTestGame(txn, 11, "So - Mamedyarov", 130, 500, 16, 11),
                // #17 Add new game, same players, with annotations
                (DatabaseTransaction txn) -> putTestGame(txn, 0, "Giri - Giri", 150, 300, 17, 17),
                // Change old game, causing player to not exist
                (DatabaseTransaction txn) -> putTestGame(txn, 12, "Carlsen - Grischuk", 120, 200, 18, 18),
                // #18 Add new game with new player
                (DatabaseTransaction txn) -> putTestGame(txn, 0, "Kramnik - Kasparov", 200, 2000, 18, 18),
                // Update last game so new player no longer exists
                (DatabaseTransaction txn) -> putTestGame(txn, 18, "Kasparov - Carlsen", 200, 0, 18, 18),
                // Rename player
                (DatabaseTransaction txn) -> txn.updatePlayerById(txn.getGame(18).whitePlayerId(), Player.ofFullName("Kasparov, Garry")),
                // #19 Add with newly renamed entity
                (DatabaseTransaction txn) -> putTestGame(txn, 0, "Caruana - Kasparov, Garry", 30, 100, 19, 19),
                // Update annotations to old game
                (DatabaseTransaction txn) -> putTestGame(txn, 9, "Radjabov - Nepo", 900, 20000, 18, 18)
        );

        // The database resulting resulting from committing after each operation will be the expected result
        for (Consumer<DatabaseTransaction> operation : operations) {
            DatabaseTransaction txn = new DatabaseTransaction(testBase);
            operation.accept(txn);
            txn.commit();
        }

        // Try split up the operations into three batches, to make sure all subsets combinations are tried in a batch
        for (int i = 0; i < operations.size(); i++) {
            for (int j = i + 1; j < operations.size(); j++) {
                Database db = new Database();
                populateDatabase(db);

                // PRE
                final DatabaseTransaction txn1 = new DatabaseTransaction(db);
                operations.subList(0, i).forEach(x -> x.accept(txn1));
                txn1.commit();

                // MAIN
                final DatabaseTransaction txn2 = new DatabaseTransaction(db);
                operations.subList(i, j).forEach(x -> x.accept(txn2));
                txn2.commit();

                // POST
                final DatabaseTransaction txn3 = new DatabaseTransaction(db);
                operations.subList(j, operations.size()).forEach(x -> x.accept(txn3));
                txn3.commit();

                validate(db);

                // Check that the games in the resulting database matches the expected database
                assertEquals(db.count(), testBase.count());
                for (int gameId = 1; gameId <= db.count(); gameId++) {
                    Game actualGame = db.getGame(gameId);
                    Game expectedGame = testBase.getGame(gameId);

                    // The id's of the entities may differ depending on when the commits were done
                    // so we have to check the actual entity names
                    assertEquals(expectedGame.white().getFullName(), actualGame.white().getFullName());
                    assertEquals(expectedGame.black().getFullName(), actualGame.black().getFullName());
                    assertEquals(expectedGame.tournament().title(), actualGame.tournament().title());
                    assertEquals(expectedGame.annotator().name(), actualGame.annotator().name());
                    assertEquals(expectedGame.source().title(), actualGame.source().title());
                    assertEquals(expectedGame.getMovesBlob(), actualGame.getMovesBlob());
                    if (expectedGame.getAnnotationOffset() > 0 || actualGame.getAnnotationOffset() > 0) {
                        assertEquals(expectedGame.getAnnotationsBlob(), actualGame.getAnnotationsBlob());
                    }
                }
            }
        }
    }

    @Test
    public void transactionIsIsolated() {
        int numGames = testBase.count();
        int numPlayers = testBase.playerIndex().count();
        int numTournaments = testBase.tournamentIndex().count();

        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 0, "Kasparov - Carlsen", "new tour", "new anno", "new source", "q1 - q2", "tag", 100, 200, 1, 2);

        assertEquals(numGames, testBase.count());
        assertEquals(numPlayers, testBase.playerIndex().count());
        assertEquals(numTournaments, testBase.tournamentIndex().count());

        txn.commit();

        assertNotEquals(numGames, testBase.count());
        assertNotEquals(numPlayers, testBase.playerIndex().count());
        assertNotEquals(numTournaments, testBase.tournamentIndex().count());
    }

    @Test(expected = IllegalStateException.class)
    public void committingTwoDatabaseTransactionsFromSameVersionFails() {
        DatabaseTransaction txn1 = new DatabaseTransaction(testBase);
        putTestGame(txn1, 1, "Giri - Carlsen", 100, 0, 10, 0);

        DatabaseTransaction txn2 = new DatabaseTransaction(testBase);
        putTestGame(txn2, 5, "So - Mamedyarov", 100, 0, 10, 0);

        txn1.commit();
        try {
            txn2.commit();
        } finally {
            txn2.rollback();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void committingDatabaseAndEntityTransactionsFromTheSameVersionFails() {
        DatabaseTransaction txn1 = new DatabaseTransaction(testBase);
        putTestGame(txn1, 1, "Giri - Carlsen", 100, 0, 10, 0);

        EntityIndexWriteTransaction<Player> txn2 = testBase.playerIndex().beginWriteTransaction();
        txn2.addEntity(Player.ofFullName("Kasparov"));

        txn1.commit();
        txn2.commit();
    }

    @Test
    public void committingEntityAndDatabaseTransactionsFromTheSameVersionFails() {
        int numTournaments = testBase.tournamentIndex().count();
        int numAnnotators = testBase.annotatorIndex().count();
        int numSources = testBase.sourceIndex().count();
        int numTeams = testBase.teamIndex().count();
        int numTags = testBase.gameTagIndex().count();;

        DatabaseTransaction txn1 = new DatabaseTransaction(testBase);
        putTestGame(txn1, 1, "Giri - Carlsen", "new tour", "new anno", "new source", "q1 - q2", "new tag", 100, 0, 10, 0);

        EntityIndexWriteTransaction<Player> txn2 = testBase.playerIndex().beginWriteTransaction();
        txn2.putEntityById(2, ImmutablePlayer.builder().from(txn2.get(2)).lastName("Kasparov").build());

        // Different order compared to previous test
        txn2.commit();
        boolean illegalState = false;
        try {
            txn1.commit();
        } catch(IllegalStateException e) {
            illegalState = true;
            txn1.rollback();
        }
        // The second transaction should not partially have been applied
        assertTrue(illegalState);
        assertEquals(15, testBase.count()); // No games should have been committed
        assertEquals(numTournaments, testBase.tournamentIndex().count());
        assertEquals(numAnnotators, testBase.annotatorIndex().count());
        assertEquals(numSources, testBase.sourceIndex().count());
        assertEquals(numTeams, testBase.teamIndex().count());
        assertEquals(numTags, testBase.gameTagIndex().count());
    }

    @Test(expected = IllegalStateException.class)
    public void commitSameTransactionMultipleTimesFails() {
        DatabaseTransaction txn = new DatabaseTransaction(testBase);
        putTestGame(txn, 1, "Giri - Carlsen", 100, 0, 10, 0);
        txn.commit();
        txn.commit();
    }

    @Test
    public void commitNewTransactionAfterRollback() {
        DatabaseTransaction txn1 = new DatabaseTransaction(testBase);
        putTestGame(txn1, 1, "Giri - Carlsen", 100, 0, 10, 0);

        DatabaseTransaction txn2 = new DatabaseTransaction(testBase);
        putTestGame(txn2, 3, "So - Aronian", 100, 0, 10, 0);

        txn1.rollback();
        txn2.commit();

        assertEquals("So", testBase.getGame(3).white().lastName()); // new game
        assertEquals("Carlsen", testBase.getGame(1).white().lastName()); // same game as before
    }

    @Test(expected = IllegalStateException.class)
    public void testBeginTwoWriteTransactionsInDifferentThreads() throws InterruptedException {
        Database db = new Database(new DatabaseContext(-1, -1));
        CountDownLatch latch = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            new DatabaseTransaction(db);
            latch.countDown();
        });
        thread.start();

        latch.await();
        new DatabaseTransaction(db);
    }
}
