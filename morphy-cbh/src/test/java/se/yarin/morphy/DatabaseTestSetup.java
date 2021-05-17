package se.yarin.morphy;

import org.junit.After;
import org.junit.Before;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Date;
import se.yarin.chess.GameHeaderModel;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Team;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.games.ImmutableExtendedGameHeader;
import se.yarin.morphy.games.ImmutableGameHeader;
import se.yarin.morphy.validation.Validator;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static se.yarin.morphy.validation.Validator.Checks.*;

public abstract class DatabaseTestSetup {
    protected Database testBase;

    // Players:
    //   Carlsen 5
    //   Caruana 2
    //   Ding 4
    //   Nepo 5
    //   Aronian 1
    //   Grischuk 2
    //   Giri 3
    //   Mamedyarov 2
    //   So 4
    //   Radjabov 2

    // Tournaments: tour1, tour2, tour3
    // Annotators: ann1, ann2, ann3
    // Source: src1, src2, src3
    // Teams: team1, team2, team3
    // GameTag: tag1, tag2, tag3

    // Creates a small database containing the following games
    // 1. Carlsen - Ding, tour2
    // 2. Nepo - Giri, tour1
    // 3. Mamedyarov - So, tour3, ann2
    // 4. Nepo - Caruana, tour2, ann1
    // 5. Caruana - Radjabov, tour1
    // 6. Nepo - Grischuk, tour1, ann1
    // 7. So - Nepo, tour2
    // 8. Carlsen - Carlsen, tour3, ann2
    // 9. Radjabov - Nepo, tour2, ann3
    // 10. Ding - Giri, tour1
    // 11. So - Mamedyarov, tour2
    // 12. Aronian - Giri, tour3
    // 13. Ding - Carlsen, tour3, ann2
    // 14. Grischuk - Ding, tour1
    // 15. Carlsen - So, tour1
    // All move blobs are 100 bytes; annotation blobs are 2000 bytes where there is an annotator (non-existent otherwise)

    @Before
    public void setupTestDatabase() {
        this.testBase = new Database();
        populateDatabase(this.testBase);
        assertEquals(10, this.testBase.playerIndex().count());
        assertEquals(5, playerCount("Carlsen"));
        assertEquals(6, tournamentCount("tour1", Date.unset()));
    }

    public void populateDatabase(@NotNull Database database) {
        try (var txn = new DatabaseWriteTransaction(database)) {
            putTestGame(txn, 0, "Carlsen - Ding", "tour2", null, null, null, null, 100, 0, 1, 0);
            putTestGame(txn, 0, "Nepo - Giri", "tour1", null, null, null, null, 100, 0, 2, 0);
            putTestGame(txn, 0, "Mamedyarov - So", "tour3", "ann2", null, null, null, 100, 2000, 3, 3);
            putTestGame(txn, 0, "Nepo - Caruana", "tour2", "ann1", null, null, null, 100, 2000, 4, 4);
            putTestGame(txn, 0, "Caruana - Radjabov", "tour1", null, null, null, null, 100, 0, 5, 0);
            putTestGame(txn, 0, "Nepo - Grischuk", "tour1", "ann1", null, null, null, 100, 2000, 6, 6);
            putTestGame(txn, 0, "So - Nepo", "tour2", null, null, null, null, 100, 0, 7, 0);
            putTestGame(txn, 0, "Carlsen - Carlsen", "tour3", "ann2", null, null, null, 100, 2000, 8, 8);
            putTestGame(txn, 0, "Radjabov - Nepo", "tour2", "ann3", null, null, null, 100, 2000, 9, 9);
            putTestGame(txn, 0, "Ding - Giri", "tour1", null, null, null, null, 100, 0, 10, 0);
            putTestGame(txn, 0, "So - Mamedyarov", "tour2", null, null, null, null, 100, 0, 11, 0);
            putTestGame(txn, 0, "Aronian - Giri", "tour3", null, null, null, null, 100, 0, 12, 0);
            putTestGame(txn, 0, "Ding - Carlsen", "tour3", "ann2", null, null, null, 100, 2000, 13, 13);
            putTestGame(txn, 0, "Grischuk - Ding", "tour1", null, null, null, null, 100, 0, 14, 0);
            putTestGame(txn, 0, "Carlsen - So", "tour1", null, null, null, null, 100, 0, 15, 0);
            txn.commit();
        }
    }

    @After
    public void validateState() {
        validate(testBase);
    }

    protected int playerCount(String fullName) {
        Player player = testBase.playerIndex().get(Player.ofFullName(fullName));
        return player == null ? 0 : player.count();
    }

    protected int tournamentCount(String title, Date date) {
        Tournament tournament = testBase.tournamentIndex().get(Tournament.of(title, date));
        return tournament == null ? 0 : tournament.count();
    }

    protected int teamCount(String title) {
        Team team = testBase.teamIndex().get(Team.of(title));
        return team == null ? 0 : team.count();
    }

    protected int playerFirstGameId(String fullName) {
        Player player = testBase.playerIndex().get(Player.ofFullName(fullName));
        return player == null ? -1 : player.firstGameId();
    }

    protected long wastedMoveBytes() {
        return testBase.moveRepository().getStorage().getWastedBytes();
    }

    protected long wastedAnnotationBytes() { return testBase.annotationRepository().getStorage().getWastedBytes(); }

    protected static void validate(Database db) {
        Validator validator = new Validator();
        // Don't check GAMES_LOAD since we're using phony blobs
        validator.validate(db, EnumSet.of(
                ENTITY_DB_INTEGRITY,
                ENTITY_SORT_ORDER,
                ENTITY_STATISTICS,
                ENTITY_PLAYERS,
                ENTITY_TOURNAMENTS,
                ENTITY_ANNOTATORS,
                ENTITY_SOURCES,
                ENTITY_TEAMS,
                ENTITY_GAME_TAGS,
                GAME_ENTITY_INDEX,
                GAMES), true, true, false);
    }


    protected @NotNull ByteBuffer generateMovesBlob(int size, int seed) {
        assert size >= 4;
        ByteBuffer buf = ByteBuffer.allocate(size);
        Random random = new Random(seed);
        random.nextBytes(buf.array());
        ByteBufferUtil.putIntB(buf, size);
        buf.position(0);
        return buf;
    }

    protected @Nullable ByteBuffer generateAnnotationsBlob(int size, int seed) {
        if (size == 0) {
            return null;
        }
        assert size >= 14;
        ByteBuffer buf = ByteBuffer.allocate(size);
        Random random = new Random(seed);
        random.nextBytes(buf.array());
        buf.position(10);
        ByteBufferUtil.putIntB(buf, size);
        buf.position(0);
        return buf;
    }

    protected Game putTestGame(@NotNull DatabaseWriteTransaction txn, int gameId, @NotNull String players,
                               int movesSize, int annotationsSize, int movesSeed, int annotationsSeed) {
        return putTestGame(txn, gameId,
                players, null, null, null, null,
                null, movesSize, annotationsSize, movesSeed, annotationsSeed);
    }

    protected Game putTestGame(
            @NotNull DatabaseWriteTransaction txn, int gameId,
            @NotNull String players, @Nullable String tournament, @Nullable String annotator, @Nullable String source, @Nullable String teams,
            @Nullable String gameTag, int movesSize, int annotationsSize, int movesSeed, int annotationsSeed) {
        String[] twoPlayers = players.split(" - ");
        assert twoPlayers.length == 2;
        GameHeaderModel headerModel = new GameHeaderModel();
        headerModel.setWhite(twoPlayers[0]);
        headerModel.setBlack(twoPlayers[1]);
        if (tournament != null) {
            headerModel.setEvent(tournament);
        }
        if (annotator != null) {
            headerModel.setAnnotator(annotator);
        }
        if (source != null) {
            headerModel.setSourceTitle(source);
        }
        if (teams != null) {
            String[] twoTeams = teams.split(" - ");
            assert twoTeams.length == 2;
            headerModel.setWhiteTeam(twoTeams[0]);
            headerModel.setBlackTeam(twoTeams[1]);
        }
        if (gameTag != null) {
            headerModel.setGameTag(gameTag);
        }

        ImmutableGameHeader.Builder header = ImmutableGameHeader.builder();
        ImmutableExtendedGameHeader.Builder extendedHeader = ImmutableExtendedGameHeader.builder();

        txn.buildEntities(header, extendedHeader, headerModel);
        GameAdapter gameAdapter = new GameAdapter();
        gameAdapter.setHeaderGameData(header, extendedHeader, headerModel);

        return txn.putGame(
                gameId,
                header,
                extendedHeader,
                generateMovesBlob(movesSize, movesSeed),
                annotationsSize == 0 ? null : generateAnnotationsBlob(annotationsSize, annotationsSeed));
    }
}
