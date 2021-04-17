package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.*;
import se.yarin.chess.Date;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.exceptions.MorphyEntityIndexException;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.games.*;
import se.yarin.morphy.games.annotations.AnnotationStatistics;
import se.yarin.morphy.games.annotations.AnnotationsSerializer;
import se.yarin.morphy.games.annotations.StatisticalAnnotation;
import se.yarin.morphy.storage.BlobStorageHeader;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.BiPredicate;

import static se.yarin.morphy.games.GameLoader.*;

/**
 * Represents an in-memory transaction of operations done on a {@link Database}.
 * Changes made in a transaction is not visible outside the transaction until they've been committed.
 * A transaction commit will be rejected if the database was changed after the transaction was opened.
 * It's the responsibility of the caller to ensure only one transaction of changes is in progress at the same time.
 */
public class DatabaseTransaction {
    private static final Logger log = LoggerFactory.getLogger(DatabaseTransaction.class);

    private final @NotNull Database database;

    private int currentGameCount;

    class GameData {
        public ImmutableGameHeader.@NotNull Builder gameHeader;
        public ImmutableExtendedGameHeader.@NotNull Builder extendedGameHeader;
        public @NotNull ByteBuffer moveBlob;
        public @Nullable ByteBuffer annotationBlob;
/*
        public @NotNull Game game() {
            return new Game(database, gameHeader.build(), extendedGameHeader.build());
        }
*/
        public GameData(
                @NotNull ImmutableGameHeader.Builder gameHeader,
                @NotNull ImmutableExtendedGameHeader.Builder extendedGameHeader,
                @NotNull ByteBuffer moveBlob,
                @Nullable ByteBuffer annotationBlob) {
            this.gameHeader = gameHeader;
            this.extendedGameHeader = extendedGameHeader;
            this.moveBlob = moveBlob;
            this.annotationBlob = annotationBlob;
        }
    }

    private final Map<Integer, GameData> updatedGames = new TreeMap<>();

    private final EntityIndexTransaction<Player> playerTransaction;
    private final EntityIndexTransaction<Tournament> tournamentTransaction;
    private final EntityIndexTransaction<Annotator> annotatorTransaction;
    private final EntityIndexTransaction<Source> sourceTransaction;
    private final EntityIndexTransaction<Team> teamTransaction;
    private final EntityIndexTransaction<GameTag> gameTagTransaction;

    private final EntityDelta<Player> playerDelta;
    private final EntityDelta<Tournament> tournamentDelta;
    private final EntityDelta<Annotator> annotatorDelta;
    private final EntityDelta<Source> sourceDelta;
    private final EntityDelta<Team> teamDelta;
    private final EntityDelta<GameTag> gameTagDelta;

    public DatabaseTransaction(@NotNull Database database) {
        this.database = database;
        this.currentGameCount = database.gameHeaderIndex().count();

        this.playerTransaction = database.playerIndex().beginTransaction();
        this.tournamentTransaction = database.tournamentIndex().beginTransaction();
        this.annotatorTransaction = database.annotatorIndex().beginTransaction();
        this.sourceTransaction = database.sourceIndex().beginTransaction();
        this.teamTransaction = database.teamIndex().beginTransaction();
        this.gameTagTransaction = database.gameTagIndex().beginTransaction();

        this.playerDelta = new EntityDelta<>((game, playerId) -> game.whitePlayerId() == playerId || game.blackPlayerId() == playerId);
        this.tournamentDelta = new EntityDelta<>((game, tournamentId) -> game.tournamentId() == tournamentId);
        this.annotatorDelta = new EntityDelta<>((game, annotatorId) -> game.annotatorId() == annotatorId);
        this.sourceDelta = new EntityDelta<>((game, sourceId) -> game.sourceId() == sourceId);
        this.teamDelta = new EntityDelta<>((game, teamId) -> game.whiteTeamId() == teamId || game.blackTeamId() == teamId);
        this.gameTagDelta = new EntityDelta<>((game, gameTagId) -> game.gameTagId() == gameTagId);
    }

    public void updatePlayerById(int id, @NotNull Player player) {
        Player oldPlayer = playerTransaction.get(id);
        // We must not update the count and firstGameId as it would get incorrect when committing the transaction
        player = ImmutablePlayer.builder().from(player).count(oldPlayer.count()).firstGameId(oldPlayer.firstGameId()).build();
        playerTransaction.putEntityById(id, player);
    }

    public <T extends Entity & Comparable<T>> void updateEntityByKey(T entity) {

    }

    public @NotNull Game getGame(int id) {
        GameData updatedGame = updatedGames.get(id);
        GameHeader gameHeader;
        ExtendedGameHeader extendedGameHeader;
        if (updatedGame != null) {
            gameHeader = updatedGame.gameHeader.build();
            extendedGameHeader = updatedGame.extendedGameHeader.build();
        } else {
            gameHeader = database.gameHeaderIndex().getGameHeader(id);
            ExtendedGameHeaderStorage storage = database.extendedGameHeaderStorage();
            // TODO: ext storage should either be empty (if cbj file is missing) or hold enough items
            extendedGameHeader = id <= storage.count() ? storage.get(id) : ExtendedGameHeader.empty(gameHeader);
        }
        assert extendedGameHeader != null;

        return new Game(database, gameHeader, extendedGameHeader);
    }

    /**
     * Adds a new game to the transaction
     * @param game the game to add (can be from the same or another database)
     * @return the id of the added game
     */
    public int addGame(@NotNull Game game) {
        return replaceGame(0, game);
    }

    /**
     * Replaces a game in the transaction
     * @param gameId the id of the game to replace
     * @param game the game to replace with (can be from the same or another database)
     * @return the id of the replaced game
     */
    public int replaceGame(int gameId, @NotNull Game game) {
        return putGame(
                gameId,
                createGameHeader(game),
                createExtendedGameHeader(game),
                game.getMovesBlob(),
                game.getAnnotationOffset() == 0 ? null : game.getAnnotationsBlob());
    }

    /**
     * Adds a new game to the transaction
     * @param model the model of the game to add
     * @return the added game
     */
    public int addGame(@NotNull GameModel model) {
        return replaceGame(0, model);
    }

    /**
     * Replaces a game in the transaction
     * @param gameId the id of the game to replace
     * @param model the model of the game to add
     * @return the id of the replaced game
     */
    public int replaceGame(int gameId, @NotNull GameModel model) {
        return putGame(
                gameId,
                createGameHeader(model),
                createExtendedGameHeader(model),
                database.moveRepository().getMoveSerializer().serializeMoves(model.moves()),
                model.moves().countAnnotations() > 0 ? AnnotationsSerializer.serializeAnnotations(gameId, model.moves()) : null);
    }

    /**
     * Internal method that adds/replaces a game in the transaction and updates the current state
     *
     * The move and annotations offsets that are set are not final; if needed, they will be adjusted
     * during commit if moves/annotations when replacing old games don't fit.
     */
    int putGame(
            int gameId,
            @NotNull ImmutableGameHeader.Builder gameHeaderBuilder,
            @NotNull ImmutableExtendedGameHeader.Builder extendedGameHeaderBuilder,
            @NotNull ByteBuffer movesBlob,
            @Nullable ByteBuffer annotationsBlob) {
        Game previousGame = null;
        if (gameId == 0) {
            // Adding a new game
            currentGameCount += 1;
            gameId = currentGameCount;

            // These will be set later during the commit phase
            gameHeaderBuilder.annotationOffset(0);
            extendedGameHeaderBuilder.annotationOffset(0);
        } else {
            // But entity statistics are updated based on the last version in this transaction
            previousGame = getGame(gameId);
        }

        updatedGames.put(gameId, new GameData(gameHeaderBuilder, extendedGameHeaderBuilder, movesBlob, annotationsBlob));
        updateEntityStats(gameId, previousGame, gameHeaderBuilder.build(), extendedGameHeaderBuilder.build());

        return gameId;
    }

    long findNextAnnotationOffset(int gameId) {
        // In practice it does not matter if we check the original games or the updates ones in the transaction
        // The same annotation offset will be deduce regardless
        int gameCount = database.gameHeaderIndex().count();
        // NOTE: This could be optimized with a low-level search
        while (gameId <= gameCount) {
            long annotationOffset = database.getGame(gameId).getAnnotationOffset();
            if (annotationOffset > 0) {
                return annotationOffset;
            }
            gameId += 1;
        }
        return 0;
    }


    public void commit() {
        // TODO: Get write lock

        // Before inserting any games, remove old blobs and if necessary make room in moves and annotations repository
        int oldGameCount = database.gameHeaderIndex().count();
        List<Integer> updatedGameIds = new ArrayList<>(updatedGames.keySet());
        for (int gameId : updatedGameIds) {
            if (gameId > oldGameCount) {
                // Only replaced games could be the cause of having to insert bytes in the moves/annotations repositories
                break;
            }
            GameData updatedGameData = updatedGames.get(gameId);
            Game originalGame = database.getGame(gameId);

            // Check if the new moves and annotations data will fit
            // Note: We're actually checking if the data is less than or equal to the game it's replacing;
            // if there is a gap behind this game we're not taking advantage of that
            // (would be easy to do for moves, a bit harder/less efficient for annotations)

            int oldMovesBlobSize = database.moveRepository().removeMovesBlob(originalGame.getMovesOffset());
            int newMovesBlobSize = updatedGameData.moveBlob.limit();
            int movesBlobSizeDelta = newMovesBlobSize - oldMovesBlobSize;

            int oldAnnotationsBlobSize = database.annotationRepository().removeAnnotationsBlob(originalGame.getAnnotationOffset());
            int newAnnotationsBlobSize = updatedGameData.annotationBlob != null ? updatedGameData.annotationBlob.limit() : 0;
            int annotationsBlobSizeDelta = newAnnotationsBlobSize - oldAnnotationsBlobSize;

            // Update the game data with the actual moves and annotation offsets
            long movesOffset = originalGame.getMovesOffset();
            long annotationOffset = originalGame.getAnnotationOffset();
            if (newAnnotationsBlobSize > 0 && annotationOffset == 0) {
                annotationOffset = findNextAnnotationOffset(gameId);
            }
            if (newAnnotationsBlobSize == 0) {
                annotationOffset = 0;
            }

            updatedGameData.gameHeader
                .movesOffset((int) movesOffset)
                .annotationOffset((int) annotationOffset);
            updatedGameData.extendedGameHeader
                .movesOffset(movesOffset)
                .annotationOffset((int) annotationOffset);

            if (movesBlobSizeDelta > 0 || annotationsBlobSizeDelta > 0) {
                // It doesn't fit, we need to shift the entire move and/or annotation repository :(
                // This also means we need to update the offset in all game headers after this game,
                // both those outside the transaction and those within the transaction

                if (movesBlobSizeDelta > 0) {
                    log.info(String.format("Move blob in game %d is %d bytes longer; adjusting", gameId, movesBlobSizeDelta));
                    database.moveRepository().insert(movesOffset, movesBlobSizeDelta);
                }
                if (annotationsBlobSizeDelta > 0 && annotationOffset > 0) {
                    log.info(String.format("Annotation blob in game %d is %d bytes longer; adjusting", gameId, annotationsBlobSizeDelta));
                    database.annotationRepository().insert(annotationOffset, annotationsBlobSizeDelta);
                }

                // Shift all move and annotations offsets in the persistent storage
                // Note: This could be done much more efficiently with low-level operations
                // Note: Only process the games until the next game in the transaction, and accumulate the shifted offsets
                for (int i = gameId + 1; i <= oldGameCount; i++) {
                    GameHeader oldHeader = database.gameHeaderIndex().getGameHeader(i);
                    ExtendedGameHeader oldExtendedHeader = database.extendedGameHeaderStorage().get(i);
                    int newMovesOffset = oldHeader.movesOffset() + Math.max(0, movesBlobSizeDelta);
                    int newAnnotationOffset = oldHeader.annotationOffset() == 0 ? 0 : (oldHeader.annotationOffset() + Math.max(0, annotationsBlobSizeDelta));
                    database.gameHeaderIndex().put(i,
                        ImmutableGameHeader.builder()
                                .from(oldHeader)
                                .movesOffset(newMovesOffset)
                                .annotationOffset(newAnnotationOffset)
                                .build());
                    database.extendedGameHeaderStorage().put(i,
                            ImmutableExtendedGameHeader.builder()
                                    .from(oldExtendedHeader)
                                    .movesOffset(newMovesOffset)
                                    .annotationOffset(newAnnotationOffset)
                                    .build());
                }
            }
        }

        // TODO: Merge this for-loop with the previous one, should be possible
        int gameCount = database.gameHeaderIndex().count();
        for (int gameId : updatedGames.keySet()) {
            GameData updatedGameData = updatedGames.get(gameId);
            if (gameId > gameCount) {
                long movesOffset = database.moveRepository().putMovesBlob(0, updatedGameData.moveBlob);
                long annotationsOffset = updatedGameData.annotationBlob != null ?
                    database.annotationRepository().putAnnotationsBlob(0, updatedGameData.annotationBlob) : 0;

                ImmutableGameHeader gameHeader = updatedGameData.gameHeader
                        .movesOffset((int) movesOffset)
                        .annotationOffset((int) annotationsOffset)
                        .build();
                ImmutableExtendedGameHeader extendedGameHeader = updatedGameData.extendedGameHeader
                        .movesOffset(movesOffset)
                        .annotationOffset((int) annotationsOffset)
                        .build();

                assert gameId == gameCount + 1;
                int id = database.gameHeaderIndex().add(gameHeader);
                assert gameId == id;
                database.extendedGameHeaderStorage().put(gameId, extendedGameHeader);
                gameCount += 1;
            } else {
                ImmutableGameHeader gameHeader = updatedGameData.gameHeader.build();
                ImmutableExtendedGameHeader extendedGameHeader = updatedGameData.extendedGameHeader.build();

                database.gameHeaderIndex().put(gameId, gameHeader);
                database.extendedGameHeaderStorage().put(gameId, extendedGameHeader);

                database.moveRepository().putMovesBlob(extendedGameHeader.movesOffset(), updatedGameData.moveBlob);
                if (updatedGameData.annotationBlob != null) {
                    database.annotationRepository().putAnnotationsBlob(extendedGameHeader.annotationOffset(), updatedGameData.annotationBlob);
                }
            }
        }

        playerDelta.apply(playerTransaction);
        tournamentDelta.apply(tournamentTransaction);
        annotatorDelta.apply(annotatorTransaction);
        sourceDelta.apply(sourceTransaction);
        teamDelta.apply(teamTransaction);
        gameTagDelta.apply(gameTagTransaction);

        playerTransaction.commit();
        tournamentTransaction.commit();
        annotatorTransaction.commit();
        sourceTransaction.commit();
        teamTransaction.commit();
        gameTagTransaction.commit();
    }

    public ImmutableGameHeader.Builder createGameHeader(Game game)
            throws MorphyInvalidDataException {
        boolean sameDb = database == game.database();

        ImmutableGameHeader.Builder builder = ImmutableGameHeader.builder().from(game.header());

        if (!sameDb) {
            // If we're adding the game to a different database (usually the case),
            // the ids of all entities may be different so we can't just copy them
            // If no entity exists with the same name, create a new one.
            builder.whitePlayerId(resolveEntityId(playerTransaction, game.white()));
            builder.blackPlayerId(resolveEntityId(playerTransaction, game.black()));
            builder.tournamentId(resolveEntityId(tournamentTransaction, game.tournament())); // TODO: tournamentExtra
            builder.annotatorId(resolveEntityId(annotatorTransaction, game.annotator()));
            builder.sourceId(resolveEntityId(sourceTransaction, game.source()));
        }

        return builder;
    }

    public ImmutableGameHeader.Builder createGameHeader(@NotNull GameModel model) throws MorphyInvalidDataException {
        ImmutableGameHeader.Builder builder = createGameHeader(model.header());
        int moves = (model.moves().countPly(false) + 1) / 2;
        builder.noMoves(moves > 255 ? -1 : moves);

        setInferredData(builder, model.moves());

        return builder;
    }

    public ImmutableGameHeader.Builder createGameHeader(@NotNull GameHeaderModel header) throws MorphyInvalidDataException {
        int whiteId, blackId, tournamentId, annotatorId, sourceId;

        // If the GameModel contains ID's from a different database, we can't use them
        // TODO: Fix this, and add tests when copying games between database with entities with same name but different id
        boolean sameDb = false; //database.getDatabaseId().equals(header.getField(DATABASE_ID));

        // Lookup or create the entities that are referenced in the game header
        // If the id field is set, use that one. Otherwise use the string field.
        // If no entity exists with the same name, create a new one.
        try {
            whiteId = resolveOrCreateEntity(sameDb ? (Integer) header.getField(WHITE_ID) : -1,
                    Player.ofFullName(defaultName(header.getWhite())), playerTransaction);
            blackId = resolveOrCreateEntity(sameDb ? (Integer) header.getField(BLACK_ID) : -1,
                    Player.ofFullName(defaultName(header.getBlack())), playerTransaction);
            tournamentId = resolveOrCreateEntity(sameDb ? (Integer) header.getField(EVENT_ID) : -1,
                    Tournament.of(defaultName(header.getEvent()), header.getEventDate() == null ? Date.unset() : header.getEventDate()), tournamentTransaction);
            annotatorId = resolveOrCreateEntity(sameDb ? (Integer) header.getField(ANNOTATOR_ID) : -1,
                    Annotator.of(defaultName(header.getAnnotator())), annotatorTransaction);
            sourceId = resolveOrCreateEntity(sameDb ? (Integer) header.getField(SOURCE_ID) : -1,
                    Source.of(defaultName(header.getSourceTitle())), sourceTransaction);
        } catch (IllegalArgumentException e) {
            throw new MorphyInvalidDataException("Failed to create GameHeader entry due to invalid entity data reference", e);
        }  catch (RuntimeException e) {
            throw new IllegalArgumentException("Some argument were not set in the game header model", e);
        }

        ImmutableGameHeader.Builder builder = ImmutableGameHeader.builder();

        builder.whitePlayerId(whiteId);
        builder.blackPlayerId(blackId);
        builder.tournamentId(tournamentId);
        builder.annotatorId(annotatorId);
        builder.sourceId(sourceId);
        builder.playedDate(header.getDate() == null ? Date.today() : header.getDate());
        builder.result(header.getResult() == null ? GameResult.NOT_FINISHED : header.getResult());
        if (header.getRound() != null) {
            builder.round(header.getRound());
        }
        if (header.getSubRound() != null) {
            builder.subRound(header.getSubRound());
        }
        if (header.getWhiteElo() != null) {
            builder.whiteElo(header.getWhiteElo());
        }
        if (header.getBlackElo() != null) {
            builder.blackElo(header.getBlackElo());
        }
        builder.eco(header.getEco() == null ? Eco.unset() : header.getEco());

        NAG eval = header.getLineEvaluation();
        builder.lineEvaluation(eval == null ? NAG.NONE : eval);
        return builder;
    }

    public ImmutableExtendedGameHeader.Builder createExtendedGameHeader(Game game)
            throws MorphyInvalidDataException {
        boolean sameDb = database == game.database();

        ImmutableExtendedGameHeader.Builder builder = ImmutableExtendedGameHeader.builder().from(game.extendedHeader());

        if (!sameDb) {
            // If we're adding the game to a different database (usually the case),
            // the ids of all entities may be different so we can't just copy them
            // If no entity exists with the same name, create a new one.
            builder.whiteTeamId(resolveEntityId(teamTransaction, game.whiteTeam()));
            builder.blackTeamId(resolveEntityId(teamTransaction, game.blackTeam()));
            builder.gameTagId(resolveEntityId(gameTagTransaction, game.gameTag()));
        }

        builder.lastChangedTimestamp(0); // TODO

        return builder;
    }

    public ImmutableExtendedGameHeader.Builder createExtendedGameHeader(GameModel model)
            throws MorphyInvalidDataException {
        ImmutableExtendedGameHeader.Builder builder = createExtendedGameHeader(model.header());
        // finalMaterial and endGameInfo needs to be updated here
        return builder;
    }

    public ImmutableExtendedGameHeader.Builder createExtendedGameHeader(GameHeaderModel header)
            throws MorphyInvalidDataException {

        int whiteTeamId, blackTeamId, gameTagId;

        // If the GameModel contains ID's from a different database, we can't use them
        boolean sameDb = false; // database.getDatabaseId().equals(header.getField(DATABASE_ID));

        // Lookup or create the entities that are referenced in the game header
        // If the id field is set, use that one. Otherwise use the string field.
        // If no entity exists with the same name, create a new one.
        try {
            whiteTeamId = resolveOrCreateEntity(sameDb ? (Integer) header.getField(WHITE_TEAM_ID) : -1,
                    Team.of(defaultName(header.getWhiteTeam())), teamTransaction);
            blackTeamId = resolveOrCreateEntity(sameDb ? (Integer) header.getField(BLACK_TEAM_ID) : -1,
                    Team.of(defaultName(header.getBlackTeam())), teamTransaction);
            gameTagId = resolveOrCreateEntity(sameDb ? (Integer) header.getField(GAME_TAG_ID) : -1,
                    GameTag.of(defaultName(header.getGameTag())), gameTagTransaction);
        } catch (IllegalArgumentException e) {
            throw new MorphyInvalidDataException("Failed to create ExtendedGameHeader entry due to invalid entity data reference", e);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Some argument were not set in the extended game header model", e);
        }

        return ImmutableExtendedGameHeader.builder()
                .finalMaterial(false)
                .whiteTeamId(whiteTeamId)
                .blackTeamId(blackTeamId)
                .whiteRatingType(RatingType.international(TournamentTimeControl.NORMAL))
                .blackRatingType(RatingType.international(TournamentTimeControl.NORMAL))
                .creationTimestamp(0) // TODO
                .endgameInfo(EndgameInfo.empty())
                .lastChangedTimestamp(0) // TODO
                .gameTagId(gameTagId);
    }

    private void setInferredData(ImmutableGameHeader.Builder builder, GameMovesModel model) {
        // TODO: Add tests!!
        AnnotationStatistics stats = new AnnotationStatistics();

        collectStats(model.root(), stats);

        EnumSet<GameHeaderFlags> gameFlags = stats.getFlags();

        int v = model.countPly(true) - model.countPly(false);
        if (v > 0) {
            gameFlags.add(GameHeaderFlags.VARIATIONS);
            builder.variationsMagnitude(v > 1000 ? 4 : v > 300 ? 3 : v > 50 ? 2 : 1);
        }
        if (model.isSetupPosition()) {
            gameFlags.add(GameHeaderFlags.SETUP_POSITION);
        }
        if (!model.root().position().isRegularChess()) {
            gameFlags.add(GameHeaderFlags.UNORTHODOX);
        }

        // TODO: Stream flag (if it should be kept here!?)
        builder.medals(stats.getMedals());
        builder.flags(gameFlags);
        builder.commentariesMagnitude(stats.getCommentariesMagnitude());
        builder.symbolsMagnitude(stats.getSymbolsMagnitude());
        builder.graphicalSquaresMagnitude(stats.getGraphicalSquaresMagnitude());
        builder.graphicalArrowsMagnitude(stats.getGraphicalArrowsMagnitude());
        builder.trainingMagnitude(stats.getTrainingMagnitude());
        builder.timeSpentMagnitude(stats.getTimeSpentMagnitude());
    }

    private void collectStats(GameMovesModel.Node node, AnnotationStatistics stats) {
        for (Annotation annotation : node.getAnnotations()) {
            if (annotation instanceof StatisticalAnnotation) {
                ((StatisticalAnnotation) annotation).updateStatistics(stats);
            }
        }
        for (GameMovesModel.Node child : node.children()) {
            collectStats(child, stats);
        }
    }

    private @NotNull String defaultName(String name) {
        return name == null ? "" : name;
    }

    private <T extends Entity & Comparable<T>> int resolveEntityId(EntityIndexTransaction<T> transaction, @Nullable T entityKey) {
        if (entityKey == null) {
            return -1;
        }
        T entity = transaction.get(entityKey);
        if (entity == null) {
            return transaction.addEntity(entityKey);
        }
        return entity.id();
    }

    <T extends Entity & Comparable<T>> int resolveOrCreateEntity(Integer id, T key, EntityIndexTransaction<T> transaction) {
        T entity;
        if (id != null && id >= 0) {
            entity = transaction.get(id);
            if (entity == null) {
                throw new IllegalArgumentException("No entity with id " + id + " in " + transaction.getClass().getSimpleName());
            }
        } else {
            entity = transaction.get(key);
            if (entity == null) {
                try {
                    return transaction.addEntity(key);
                } catch (MorphyEntityIndexException e) {
                    // Shouldn't happen since we tried to lookup the player first
                    throw new RuntimeException("Internal error");
                }
            }
        }
        return entity.id();
    }

    /**
     *
     * @param <T>
     */
    private class EntityDelta<T extends Entity & Comparable<T>> {
        // Mapping of entityId to game ids that it has been included in resp excluded from
        // TODO: List<Integer> should be multi-sets (insertion order is not important) for better performance
        private final Map<Integer, List<Integer>> includes = new HashMap<>();
        private final Map<Integer, List<Integer>> excludes = new HashMap<>();
        private final BiPredicate<Game, Integer> hasEntity;

        private EntityDelta(BiPredicate<Game, Integer> hasEntity) {
            this.hasEntity = hasEntity;
        }

        private void remove(int gameId, int entityId) {
            if (entityId < 0) {
                return;
            }
            List<Integer> includeGames = includes.get(entityId);
            if (includeGames != null && includeGames.contains(gameId)) {
                includeGames.remove((Object) gameId);
                return;
            }
            excludes.computeIfAbsent(entityId, k -> new ArrayList<>()).add(gameId);
        }

        private void add(int gameId, int entityId) {
            if (entityId < 0) {
                return;
            }
            List<Integer> excludeGames = excludes.get(entityId);
            if (excludeGames != null && excludeGames.contains(gameId)) {
                excludeGames.remove((Object) gameId);
                return;
            }
            includes.computeIfAbsent(entityId, k -> new ArrayList<>()).add(gameId);
        }

        public void apply(EntityIndexTransaction<T> entityTransaction) {
            HashSet<Integer> entityIds = new HashSet<>();
            entityIds.addAll(includes.keySet());
            entityIds.addAll(excludes.keySet());

            for (int entityId : entityIds) {
                T entity = entityTransaction.get(entityId);
                int newFirstGameId = entity.firstGameId();
                boolean firstIdSearch = false;
                int count = entity.count();
                if (excludes.containsKey(entityId)) {
                    count -= excludes.get(entityId).size();
                    if (excludes.get(entityId).contains(newFirstGameId)) {
                        firstIdSearch = true;
                    }
                }
                List<Integer> includeGames = includes.get(entityId);
                if (includeGames != null && includeGames.size() > 0) {
                    count += includeGames.size();
                    int includeMinGameId = Collections.min(includeGames);
                    newFirstGameId = newFirstGameId == 0 ? includeMinGameId : Math.min(newFirstGameId, includeMinGameId);
                }
                if (count == 0) {
                    entityTransaction.deleteEntity(entityId);
                } else {
                    if (firstIdSearch) {
                        // TODO: With the search boosters in place, they should already be updated and this turned into a simple lookup
                        GameHeaderIndex ix = DatabaseTransaction.this.database.gameHeaderIndex();
                        newFirstGameId = 0;
                        for (int i = 1; i <= ix.count(); i++) {
                            if (hasEntity.test(DatabaseTransaction.this.database.getGame(i), entityId)) {
                                newFirstGameId = i;
                                break;
                            }
                        }
                        assert newFirstGameId > 0;
                    }
                    if (count != entity.count() || newFirstGameId != entity.firstGameId()) {
                        T newEntity = (T) entity.withCountAndFirstGameId(count, newFirstGameId);
                        entityTransaction.putEntityByKey(newEntity);
                    }
                }
            }
        }
    }

    /**
     * Updates the entity statistics for all involved entities in the
     * old game that was replaced and the new game that was added.
     */
    private void updateEntityStats(
            int gameId,
            @Nullable Game oldGame,
            @NotNull GameHeader newGameHeader,
            @NotNull ExtendedGameHeader newExtendedGameHeader) {

        if (oldGame != null) {
            playerDelta.remove(gameId, oldGame.header().whitePlayerId());
            playerDelta.remove(gameId, oldGame.header().blackPlayerId());
            tournamentDelta.remove(gameId, oldGame.header().tournamentId());
            annotatorDelta.remove(gameId, oldGame.header().annotatorId());
            sourceDelta.remove(gameId, oldGame.header().sourceId());
            teamDelta.remove(gameId, oldGame.extendedHeader().whiteTeamId());
            teamDelta.remove(gameId, oldGame.extendedHeader().blackTeamId());
            gameTagDelta.remove(gameId, oldGame.extendedHeader().gameTagId());
        }

        playerDelta.add(gameId, newGameHeader.whitePlayerId());
        playerDelta.add(gameId, newGameHeader.blackPlayerId());
        tournamentDelta.add(gameId, newGameHeader.tournamentId());
        annotatorDelta.add(gameId, newGameHeader.annotatorId());
        sourceDelta.add(gameId, newGameHeader.sourceId());
        teamDelta.add(gameId, newExtendedGameHeader.whiteTeamId());
        teamDelta.add(gameId, newExtendedGameHeader.blackTeamId());
        gameTagDelta.add(gameId, newExtendedGameHeader.gameTagId());
    }

}
