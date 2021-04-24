package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameModel;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.exceptions.MorphyNotSupportedException;
import se.yarin.morphy.games.*;
import se.yarin.morphy.games.annotations.AnnotationsSerializer;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.BiPredicate;

/**
 * Represents an in-memory transaction of operations done on a {@link Database}.
 *
 * Changes made in a transaction is not visible outside the transaction until they've been committed.
 * A transaction commit will be rejected if the database was changed after the transaction was opened.
 * It's the responsibility of the caller to ensure only one transaction of changes is in progress at the same time.
 *
 * When adding or replacing games, new entities (players, tournaments etc) will be created if missing
 * with as much metadata as is given. However, if the entities already exist, metadata will not be updated
 * as a side effect even it's different. Use the update* methods in the transaction to change metadata about entities.
 */
public class DatabaseTransaction {
    private static final Logger log = LoggerFactory.getLogger(DatabaseTransaction.class);

    private final @NotNull Database database;
    private final @NotNull GameAdapter gameAdapter = new GameAdapter();

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
    private final TournamentIndexTransaction tournamentTransaction;
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
        this.tournamentTransaction = database.tournamentIndex().beginTransaction(database.tournamentExtraStorage());
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

    // TODO: all entity types
    public void updatePlayerById(int id, @NotNull Player player) {
        Player oldPlayer = playerTransaction.get(id);
        // We must not update the count and firstGameId as it would get incorrect when committing the transaction
        player = ImmutablePlayer.builder()
                .from(player)
                .count(oldPlayer.count())
                .firstGameId(oldPlayer.firstGameId())
                .build();
        playerTransaction.putEntityById(id, player);
    }

    public void updateTournamentById(int id, @NotNull Tournament tournament, @NotNull TournamentExtra tournamentExtra) {
        Tournament oldTournament = tournamentTransaction.get(id);
        // We must not update the count and firstGameId as it would get incorrect when committing the transaction
        tournament = ImmutableTournament.builder()
                .from(tournament)
                .count(oldTournament.count())
                .firstGameId(oldTournament.firstGameId())
                .build();
        tournamentTransaction.putEntityById(id, tournament, tournamentExtra);
    }

    public void updateAnnotatorById(int id, @NotNull Annotator annotator) {
        Annotator oldAnnotator = annotatorTransaction.get(id);
        // We must not update the count and firstGameId as it would get incorrect when committing the transaction
        annotator = ImmutableAnnotator.builder()
                .from(annotator)
                .count(oldAnnotator.count())
                .firstGameId(oldAnnotator.firstGameId())
                .build();
        annotatorTransaction.putEntityById(id, annotator);
    }

    public void updateSourceById(int id, @NotNull Source source) {
        Source oldSource = sourceTransaction.get(id);
        // We must not update the count and firstGameId as it would get incorrect when committing the transaction
        source = ImmutableSource.builder()
                .from(source)
                .count(oldSource.count())
                .firstGameId(oldSource.firstGameId())
                .build();
        sourceTransaction.putEntityById(id, source);
    }

    public void updateTeamById(int id, @NotNull Team team) {
        Team oldTeam = teamTransaction.get(id);
        // We must not update the count and firstGameId as it would get incorrect when committing the transaction
        team = ImmutableTeam.builder()
                .from(team)
                .count(oldTeam.count())
                .firstGameId(oldTeam.firstGameId())
                .build();
        teamTransaction.putEntityById(id, team);
    }

    public void updateGameTagById(int id, @NotNull GameTag gameTag) {
        GameTag oldGameTag = gameTagTransaction.get(id);
        // We must not update the count and firstGameId as it would get incorrect when committing the transaction
        gameTag = ImmutableGameTag.builder()
                .from(gameTag)
                .count(oldGameTag.count())
                .firstGameId(oldGameTag.firstGameId())
                .build();
        gameTagTransaction.putEntityById(id, gameTag);
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
        // We can use most of the metadata from the old game header
        ImmutableGameHeader.Builder header = ImmutableGameHeader.builder().from(game.header());
        ImmutableExtendedGameHeader.Builder extendedHeader = ImmutableExtendedGameHeader.builder().from(game.extendedHeader());

        // But all entity references may need to be updated, if we're copying the game from another database
        buildEntities(header, extendedHeader, game);

        return putGame(
                gameId,
                header,
                extendedHeader,
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
        ImmutableGameHeader.Builder header = ImmutableGameHeader.builder();
        ImmutableExtendedGameHeader.Builder extendedHeader = ImmutableExtendedGameHeader.builder();

        gameAdapter.setGameData(header, extendedHeader, model);
        buildEntities(header, extendedHeader, model.header());

        return putGame(
                gameId,
                header,
                extendedHeader,
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

    /**
     * Assigns entity id's to the game header builds based on data in the model.
     * References to new entities will be created in their respective entity index transaction.
     * @param header the game header being created
     * @param extendedHeader the extended header being created
     * @param headerModel the source data for the entities in the game being created
     */
    void buildEntities(
            @NotNull ImmutableGameHeader.Builder header,
            @NotNull ImmutableExtendedGameHeader.Builder extendedHeader,
            @NotNull GameHeaderModel headerModel) {
        int whitePlayerId = -1, blackPlayerId = -1, tournamentId = -1, annotatorId = -1, sourceId = -1;
        int whiteTeamId = -1, blackTeamId = -1, gameTagId = -1;

        if (headerModel.getField(GameAdapter.DATABASE_ID) == this.database) {
            // If the game model contains references within the same database, then prefer those
            whitePlayerId = validIdReference(playerTransaction, headerModel, GameAdapter.WHITE_ID);
            blackPlayerId = validIdReference(playerTransaction, headerModel, GameAdapter.BLACK_ID);
            tournamentId = validIdReference(tournamentTransaction, headerModel, GameAdapter.EVENT_ID);
            annotatorId = validIdReference(annotatorTransaction, headerModel, GameAdapter.ANNOTATOR_ID);
            sourceId = validIdReference(sourceTransaction, headerModel, GameAdapter.SOURCE_ID);
            whiteTeamId = validIdReference(teamTransaction, headerModel, GameAdapter.WHITE_TEAM_ID);
            blackTeamId = validIdReference(teamTransaction, headerModel, GameAdapter.BLACK_TEAM_ID);
            gameTagId = validIdReference(gameTagTransaction, headerModel, GameAdapter.GAME_TAG_ID);
        }

        // Tournament is a special case, since if it's missing we need to create a TournamentExtra entry as well
        if (tournamentId < 0) {
            Tournament tournamentKey = gameAdapter.toTournament(headerModel);
            Tournament existingTournament = tournamentTransaction.get(tournamentKey);
            if (existingTournament == null) {
                tournamentId = tournamentTransaction.addEntity(tournamentKey, gameAdapter.toTournamentExtra(headerModel));
            } else {
                tournamentId = existingTournament.id();
            }
        }

        header
            .whitePlayerId(whitePlayerId >= 0 ? whitePlayerId : playerTransaction.getOrCreate(Player.ofFullName(headerModel.getWhite())))
            .blackPlayerId(blackPlayerId >= 0 ? blackPlayerId : playerTransaction.getOrCreate(Player.ofFullName(headerModel.getBlack())))
            .tournamentId(tournamentId)
            .annotatorId(annotatorId >= 0 ? annotatorId : annotatorTransaction.getOrCreate(Annotator.of(headerModel.getAnnotator())))
            .sourceId(sourceId >= 0 ? sourceId : sourceTransaction.getOrCreate(gameAdapter.toSource(headerModel)));

        if (whiteTeamId >= 0 || headerModel.getWhiteTeam() != null) {
            extendedHeader.whiteTeamId(whiteTeamId >= 0 ? whiteTeamId : teamTransaction.getOrCreate(Team.of(headerModel.getWhiteTeam())));
        }
        if (blackTeamId >= 0 || headerModel.getBlackTeam() != null) {
            extendedHeader.blackTeamId(blackTeamId >= 0 ? blackTeamId : teamTransaction.getOrCreate(Team.of(headerModel.getBlackTeam())));
        }
        if (gameTagId >= 0 || headerModel.getGameTag() != null) {
            extendedHeader.gameTagId(gameTagId >= 0 ? gameTagId : gameTagTransaction.getOrCreate(GameTag.of(headerModel.getGameTag())));
        }
    }

    public void buildEntities(
            @NotNull ImmutableGameHeader.Builder header,
            @NotNull ImmutableExtendedGameHeader.Builder extendedHeader,
            @NotNull Game game) {

        throw new MorphyNotSupportedException("not done yet");
    }

    private <T extends Entity & Comparable<T>> int validIdReference(
            @NotNull EntityIndexTransaction<T> entityIndexTransaction,
            @NotNull GameHeaderModel headerModel,
            @NotNull String fieldName) {
        Object idRef = headerModel.getField(fieldName);
        if (!(idRef instanceof Integer)) {
            return -1;
        }
        int id = (int) idRef;
        T entity = null;
        try {
            entity = entityIndexTransaction.get(id);
        } catch (IllegalArgumentException e) {
            // Ignore, exception will be thrown below
        }
        if (entity == null) {
            throw new MorphyInvalidDataException(String.format("Game model contains reference %s %d which doesn't exist",
                    fieldName, id));
        }
        return id;
    }

    /**
     * Class representing which games in the transaction entities are being added to or removed from.
     * Also contains the logic for updating the entity index once the database transaction is committed.
     * @param <T> the type of entity
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
