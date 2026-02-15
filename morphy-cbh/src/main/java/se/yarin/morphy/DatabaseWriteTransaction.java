package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameModel;
import se.yarin.morphy.boosters.GameEntityIndex;
import se.yarin.morphy.boosters.GameEvents;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.games.*;
import se.yarin.morphy.text.TextHeaderModel;
import se.yarin.morphy.text.TextModel;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.BiPredicate;

/**
 * Represents an in-memory transaction of operations done on a {@link Database}.
 *
 * <p>Changes made in a transaction are not visible outside the transaction until they've been
 * committed. A transaction commit will be rejected if the database was changed after the
 * transaction was opened. It's the responsibility of the caller to ensure only one transaction of
 * changes is in progress at the same time.
 *
 * <p>When adding or replacing games, new entities (players, tournaments etc) will be created if
 * missing with as much metadata as is given. However, if the entities already exist, metadata will
 * not be updated as a side effect even it's different. Use the update* methods in the transaction
 * to change metadata about entities.
 */
public class DatabaseWriteTransaction extends DatabaseTransaction {
  private static final Logger log = LoggerFactory.getLogger(DatabaseWriteTransaction.class);

  // Track last used creation timestamp to ensure uniqueness across all transactions
  private static long lastCreationTimestamp = 0;

  class GameData {
    public ImmutableGameHeader.@NotNull Builder gameHeader;
    public ImmutableExtendedGameHeader.@NotNull Builder extendedGameHeader;
    public @NotNull ByteBuffer moveBlob;
    public @Nullable ByteBuffer annotationBlob;
    public @Nullable TopGamesStorage.TopGameStatus topGameStatus;
    public @Nullable GameEvents events;

    /*
            public @NotNull Game game() {
                return new Game(database, gameHeader.build(), extendedGameHeader.build());
            }
    */
    public GameData(
        @NotNull ImmutableGameHeader.Builder gameHeader,
        @NotNull ImmutableExtendedGameHeader.Builder extendedGameHeader,
        @NotNull ByteBuffer moveBlob,
        @Nullable ByteBuffer annotationBlob,
        @Nullable TopGamesStorage.TopGameStatus topGameStatus,
        @Nullable GameEvents events) {
      this.gameHeader = gameHeader;
      this.extendedGameHeader = extendedGameHeader;
      this.moveBlob = moveBlob;
      this.annotationBlob = annotationBlob;
      this.topGameStatus = topGameStatus;
      this.events = events;
    }
  }

  private int currentGameCount;
  private int version; // The version of the database the transaction starts from

  private final Map<Integer, GameData> updatedGames = new TreeMap<>();

  private final EntityIndexWriteTransaction<Player> playerTransaction;
  private final TournamentIndexWriteTransaction tournamentTransaction;
  private final EntityIndexWriteTransaction<Annotator> annotatorTransaction;
  private final EntityIndexWriteTransaction<Source> sourceTransaction;
  private final EntityIndexWriteTransaction<Team> teamTransaction;
  private final EntityIndexWriteTransaction<GameTag> gameTagTransaction;

  public int version() {
    return version;
  }

  /**
   * Checks whether the transaction has uncommitted changes.
   *
   * <p>Returns true if there are any pending changes that haven't been committed to the database,
   * including:
   * <ul>
   *   <li>Modified, added, or replaced games</li>
   *   <li>Changes to player entities</li>
   *   <li>Changes to tournament entities</li>
   *   <li>Changes to annotator entities</li>
   *   <li>Changes to source entities</li>
   *   <li>Changes to team entities</li>
   *   <li>Changes to game tag entities</li>
   * </ul>
   *
   * @return true if there are uncommitted changes; false otherwise
   */
  public boolean hasUncommittedChanges() {
    return !updatedGames.isEmpty()
        || playerTransaction.hasUncommittedChanges()
        || tournamentTransaction.hasUncommittedChanges()
        || annotatorTransaction.hasUncommittedChanges()
        || sourceTransaction.hasUncommittedChanges()
        || teamTransaction.hasUncommittedChanges()
        || gameTagTransaction.hasUncommittedChanges();
  }

  @Override
  public EntityIndexWriteTransaction<Player> playerTransaction() {
    return playerTransaction;
  }

  @Override
  public TournamentIndexWriteTransaction tournamentTransaction() {
    return tournamentTransaction;
  }

  @Override
  public EntityIndexWriteTransaction<Annotator> annotatorTransaction() {
    return annotatorTransaction;
  }

  @Override
  public EntityIndexWriteTransaction<Source> sourceTransaction() {
    return sourceTransaction;
  }

  @Override
  public EntityIndexWriteTransaction<Team> teamTransaction() {
    return teamTransaction;
  }

  @Override
  public EntityIndexWriteTransaction<GameTag> gameTagTransaction() {
    return gameTagTransaction;
  }

  private final EntityDelta<Player> playerDelta;
  private final EntityDelta<Tournament> tournamentDelta;
  private final EntityDelta<Annotator> annotatorDelta;
  private final EntityDelta<Source> sourceDelta;
  private final EntityDelta<Team> teamDelta;
  private final EntityDelta<GameTag> gameTagDelta;

  private boolean createGameEvents() {
    return database().gameEventStorage() != null;
  }

  /**
   * Generates the next unique creation timestamp.
   * Ensures no two games have the same creation timestamp by incrementing if necessary.
   *
   * @return a unique creation timestamp
   */
  private static synchronized long getNextCreationTimestamp() {
    long timestamp = ExtendedGameHeader.currentCreationTimestamp();

    // Ensure uniqueness - if calculated timestamp is not greater than last, increment
    if (timestamp <= lastCreationTimestamp) {
      timestamp = lastCreationTimestamp + 1;
    }
    lastCreationTimestamp = timestamp;

    return timestamp;
  }

  /**
   * Creates a new database write transaction.
   *
   * <p>This will acquire the database update lock. It's up to the caller to ensure that either
   * {@link #commit()} or {@link #rollback()} is called so the lock gets released.
   *
   * @param database the target database
   */
  public DatabaseWriteTransaction(@NotNull Database database) {
    super(DatabaseContext.DatabaseLock.UPDATE, database);
    this.currentGameCount = database.gameHeaderIndex().count();
    this.version = database.context().currentVersion();

    this.playerTransaction = database.playerIndex().beginWriteTransaction();
    this.tournamentTransaction =
        database.tournamentIndex().beginWriteTransaction(database.tournamentExtraStorage());
    this.annotatorTransaction = database.annotatorIndex().beginWriteTransaction();
    this.sourceTransaction = database.sourceIndex().beginWriteTransaction();
    this.teamTransaction = database.teamIndex().beginWriteTransaction();
    this.gameTagTransaction = database.gameTagIndex().beginWriteTransaction();

    this.playerDelta =
        new EntityDelta<>(
            EntityType.PLAYER,
            (game, playerId) ->
                game.whitePlayerId() == playerId || game.blackPlayerId() == playerId);
    this.tournamentDelta =
        new EntityDelta<>(
            EntityType.TOURNAMENT, (game, tournamentId) -> game.tournamentId() == tournamentId);
    this.annotatorDelta =
        new EntityDelta<>(
            EntityType.ANNOTATOR, (game, annotatorId) -> game.annotatorId() == annotatorId);
    this.sourceDelta =
        new EntityDelta<>(EntityType.SOURCE, (game, sourceId) -> game.sourceId() == sourceId);
    this.teamDelta =
        new EntityDelta<>(
            EntityType.TEAM,
            (game, teamId) -> game.whiteTeamId() == teamId || game.blackTeamId() == teamId);
    this.gameTagDelta =
        new EntityDelta<>(EntityType.GAME_TAG, (game, gameTagId) -> game.gameTagId() == gameTagId);
  }

  /**
   * Generic helper to update an entity while preserving count and firstGameId.
   * We must not update the count and firstGameId as it would get incorrect when committing the
   * transaction.
   */
  private <T extends Entity & Comparable<T>> void updateEntityById(
      @NotNull EntityIndexWriteTransaction<T> transaction,
      int id,
      @NotNull T entity) {
    T oldEntity = transaction.get(id);
    @SuppressWarnings("unchecked")
    T preservedEntity = (T) entity.withCountAndFirstGameId(
        oldEntity.count(),
        oldEntity.firstGameId());
    transaction.putEntityById(id, preservedEntity);
  }

  public void updatePlayerById(int id, @NotNull Player player) {
    updateEntityById(playerTransaction, id, player);
  }

  public void updateAnnotatorById(int id, @NotNull Annotator annotator) {
    updateEntityById(annotatorTransaction, id, annotator);
  }

  public void updateSourceById(int id, @NotNull Source source) {
    updateEntityById(sourceTransaction, id, source);
  }

  public void updateTeamById(int id, @NotNull Team team) {
    updateEntityById(teamTransaction, id, team);
  }

  public void updateGameTagById(int id, @NotNull GameTag gameTag) {
    updateEntityById(gameTagTransaction, id, gameTag);
  }

  public void updateTournamentById(
      int id, @NotNull Tournament tournament, @NotNull TournamentExtra tournamentExtra) {
    Tournament oldTournament = tournamentTransaction.get(id);
    Tournament preservedTournament = (Tournament) tournament.withCountAndFirstGameId(
        oldTournament.count(),
        oldTournament.firstGameId());
    tournamentTransaction.putEntityById(id, preservedTournament, tournamentExtra);
  }

  public @NotNull Game getGame(int id) {
    GameData updatedGame = updatedGames.get(id);
    GameHeader gameHeader;
    ExtendedGameHeader extendedGameHeader;
    if (updatedGame != null) {
      gameHeader = updatedGame.gameHeader.build();
      extendedGameHeader = updatedGame.extendedGameHeader.build();
      return new Game(this, gameHeader, extendedGameHeader);
    } else {
      Game game = super.getGame(id);
      // Wrap it in this transaction instead of the original database
      // to ensure entities are correctly resolved from transactions
      return new Game(this, game.header(), game.extendedHeader());
    }
  }

  /**
   * Adds a new game to the transaction
   *
   * @param game the game to add (can be from the same or another database)
   * @return the added game
   */
  public @NotNull Game addGame(@NotNull Game game) {
    return replaceGame(0, game);
  }

  /**
   * Replaces a game in the transaction
   *
   * @param gameId the id of the game to replace
   * @param game the game to replace with (can be from the same or another database)
   * @return the replaced game
   */
  public @NotNull Game replaceGame(int gameId, @NotNull Game game) {
    // We can use most of the metadata from the old game header
    ImmutableGameHeader.Builder header = ImmutableGameHeader.builder().from(game.header());
    ImmutableExtendedGameHeader.Builder extendedHeader =
        ImmutableExtendedGameHeader.builder().from(game.extendedHeader());

    // But all entity references may need to be updated, if we're copying the game from another
    // database
    resolveEntities(header, extendedHeader, game);

    GameEvents gameEvents = game.gameEvents();
    if ((gameEvents == null || gameEvents.isEmpty()) && createGameEvents()) {
      gameEvents = game.guidingText() ? new GameEvents() : new GameEvents(game.getModel().moves());
    }

    return putGame(
        gameId,
        header,
        extendedHeader,
        game.getMovesBlob(),
        game.getAnnotationOffset() == 0 ? null : game.getAnnotationsBlob(),
        game.topGameStatus(),
        gameEvents);
  }

  /**
   * Adds a new game to the transaction
   *
   * @param model the model of the game to add
   * @return the added game
   */
  public @NotNull Game addGame(@NotNull GameModel model) {
    return replaceGame(0, model);
  }

  /**
   * Adds a new text to the transaction
   *
   * @param model the model of the text to add
   * @return the added text
   */
  public @NotNull Game addText(@NotNull TextModel model) {
    return replaceText(0, model);
  }

  /**
   * Replaces a game in the transaction
   *
   * @param gameId the id of the game to replace
   * @param model the model of the game to add
   * @return the replaced game
   */
  public @NotNull Game replaceGame(int gameId, @NotNull GameModel model) {
    ImmutableGameHeader.Builder header = ImmutableGameHeader.builder();
    ImmutableExtendedGameHeader.Builder extendedHeader = ImmutableExtendedGameHeader.builder();

    gameAdapter().setGameData(header, extendedHeader, model);
    resolveEntities(header, extendedHeader, model.header());

    return putGame(
        gameId,
        header,
        extendedHeader,
        database().moveRepository().moveSerializer().serializeMoves(model.moves()),
        model.moves().countAnnotations() > 0
            ? database()
                .annotationRepository()
                .annotationSerializer()
                .serializeAnnotations(gameId, model.moves())
            : null,
        TopGamesStorage.TopGameStatus.UNKNOWN, // TODO: Not sure how this works
        createGameEvents() ? new GameEvents(model.moves()) : null);
  }

  /**
   * Resolves an entity ID from the GameModel. If the ID is set (not -1) and valid in the current
   * database, uses it. Otherwise creates/looks up the entity from model data.
   *
   * @param headerModel the game header model
   * @param currentId the entity ID from the model's internal fields (-1 if not set)
   * @param entityBuilder function to build entity from model data
   * @param transaction the entity transaction to validate/create in
   * @param idSetter consumer to set the resolved ID
   * @throws MorphyInvalidDataException if currentId is set but references a non-existent entity
   */
  private <T extends Entity & Comparable<T>> void doEntity(
      @NotNull GameHeaderModel headerModel,
      int currentId,
      @NotNull java.util.function.Function<GameHeaderModel, T> entityBuilder,
      @NotNull EntityIndexWriteTransaction<T> transaction,
      @NotNull java.util.function.Consumer<Integer> idSetter) {

    int resolvedId;

    if (currentId == -1) {
      // ID not set, create/lookup from model data
      T entity = entityBuilder.apply(headerModel);
      resolvedId = transaction.getOrCreate(entity);
    } else {
      // ID is set, validate it exists in current database
      try {
        transaction.get(currentId); // Throws IllegalArgumentException if doesn't exist
        // ID is valid, use it as-is
        resolvedId = currentId;
      } catch (IllegalArgumentException e) {
        // Invalid ID reference
        throw new MorphyInvalidDataException(
            String.format("Game model contains invalid entity reference id %d which doesn't exist", currentId));
      }
    }

    idSetter.accept(resolvedId);
  }

  /**
   * Resolves a tournament ID (special case with TournamentExtra).
   *
   * @param model the game model
   * @param currentId the tournament ID from the model's internal fields (-1 if not set)
   * @param tournamentBuilder function to build Tournament from model data
   * @param extraBuilder function to build TournamentExtra from model data
   * @param idSetter consumer to set the resolved ID
   * @throws MorphyInvalidDataException if currentId is set but references a non-existent tournament
   */
  private void doTournament(
      @NotNull GameHeaderModel model,
      int currentId,
      @NotNull java.util.function.BiFunction<GameHeaderModel, GameAdapter, Tournament> tournamentBuilder,
      @NotNull java.util.function.BiFunction<GameHeaderModel, GameAdapter, TournamentExtra> extraBuilder,
      @NotNull java.util.function.Consumer<Integer> idSetter) {

    int resolvedId;

    if (currentId == -1) {
      // ID not set, create/lookup from model data
      Tournament tournament = tournamentBuilder.apply(model, gameAdapter());
      TournamentExtra extra = extraBuilder.apply(model, gameAdapter());
      resolvedId = tournamentTransaction().getOrCreate(tournament, extra);
    } else {
      // ID is set, validate it exists in current database
      try {
        tournamentTransaction().get(currentId); // Throws IllegalArgumentException if doesn't exist
        // ID is valid, use it as-is
        resolvedId = currentId;
      } catch (IllegalArgumentException e) {
        // Invalid ID reference
        throw new MorphyInvalidDataException(
            String.format("Game model contains invalid tournament reference id %d which doesn't exist", currentId));
      }
    }

    idSetter.accept(resolvedId);
  }
  /**
   * Replaces a text in the transaction
   *
   * @param gameId the id of the text to replace
   * @param model the model of the text to add
   * @return the replaced text
   */
  public @NotNull Game replaceText(int gameId, @NotNull TextModel model) {
    ImmutableGameHeader.Builder header = ImmutableGameHeader.builder();
    ImmutableExtendedGameHeader.Builder extendedHeader = ImmutableExtendedGameHeader.builder();

    gameAdapter().setTextData(header, extendedHeader, model);
    resolveEntities(header, model.header());

    return putGame(
        gameId,
        header,
        extendedHeader,
        model.contents().serialize(),
        null,
        TopGamesStorage.TopGameStatus.UNKNOWN,
        createGameEvents() ? new GameEvents() : null);
  }

  /**
   * Internal method that adds/replaces a game in the transaction and updates the current state
   *
   * <p>The move and annotations offsets that are set are not final; if needed, they will be
   * adjusted during commit if moves/annotations when replacing old games don't fit.
   */
  @NotNull
  Game putGame(
      int gameId,
      @NotNull ImmutableGameHeader.Builder gameHeaderBuilder,
      @NotNull ImmutableExtendedGameHeader.Builder extendedGameHeaderBuilder,
      @NotNull ByteBuffer movesBlob,
      @Nullable ByteBuffer annotationsBlob,
      @Nullable TopGamesStorage.TopGameStatus topGameStatus,
      @Nullable GameEvents events) {
    Game previousGame = null;
    if (gameId == 0) {
      // Adding a new game
      currentGameCount += 1;
      gameId = currentGameCount;

      extendedGameHeaderBuilder.gameVersion(1);
      extendedGameHeaderBuilder.creationTimestamp(getNextCreationTimestamp());

      // These will be set later during the commit phase
      gameHeaderBuilder.annotationOffset(0);
      extendedGameHeaderBuilder.annotationOffset(0);
    } else {
      // Replacing an existing game
      // But entity statistics are updated based on the last version in this transaction
      previousGame = getGame(gameId);

      extendedGameHeaderBuilder.gameVersion(previousGame.gameVersion() + 1);
      extendedGameHeaderBuilder.creationTimestamp(previousGame.creationTimestamp());
    }
    extendedGameHeaderBuilder.lastChangedTimestamp(ExtendedGameHeader.currentLastChangedTimestamp());

    gameHeaderBuilder.id(gameId);
    updatedGames.put(
        gameId,
        new GameData(
            gameHeaderBuilder,
            extendedGameHeaderBuilder,
            movesBlob,
            annotationsBlob,
            topGameStatus,
            events));
    ImmutableGameHeader header = gameHeaderBuilder.build();
    ImmutableExtendedGameHeader extendedHeader = extendedGameHeaderBuilder.build();
    updateEntityStats(gameId, previousGame, header, extendedHeader);

    Game game = new Game(this, header, extendedHeader);
    assert game.id() > 0;
    return game;
  }

  long findNextAnnotationOffset(int gameId) {
    // In practice it does not matter if we check the original games or the updates ones in the
    // transaction
    // The same annotation offset will be deduce regardless
    int gameCount = database().gameHeaderIndex().count();
    // NOTE: This could be optimized with a low-level search
    while (gameId <= gameCount) {
      long annotationOffset = super.getGame(gameId).getAnnotationOffset();
      if (annotationOffset > 0) {
        return annotationOffset;
      }
      gameId += 1;
    }
    return 0;
  }

  /**
   * Checks that the commit is not outdated (already committed or based on a version that's not the
   * current version)
   *
   * @throws IllegalStateException if the commit can't be committed because it's outdated
   */
  public void validateCommit() {
    // Need to check this in case there are two active transactions in the same thread
    if (database().context().currentVersion() != version()) {
      throw new IllegalStateException("The database has changed since the transaction started");
    }
    playerTransaction.validateCommit();
    tournamentTransaction.validateCommit();
    annotatorTransaction.validateCommit();
    sourceTransaction.validateCommit();
    teamTransaction.validateCommit();
    gameTagTransaction.validateCommit();
  }

  /** Commits the transaction to the database */
  public void commit() {
    // Don't attempt to grab the write lock before ensuring that we still have the update lock
    ensureTransactionIsOpen();

    acquireLock(DatabaseContext.DatabaseLock.WRITE);

    MoveOffsetStorage moveOffsetStorage = database().moveOffsetStorage();
    try {
      validateCommit();

      // Before inserting any games, remove old blobs and if necessary make room in moves and
      // annotations repository
      int oldGameCount = database().gameHeaderIndex().count();
      List<Integer> updatedGameIds = new ArrayList<>(updatedGames.keySet());
      for (int gameId : updatedGameIds) {
        if (gameId > oldGameCount) {
          // Only replaced games could be the cause of having to insert bytes in the
          // moves/annotations repositories
          break;
        }
        GameData updatedGameData = updatedGames.get(gameId);
        Game originalGame = super.getGame(gameId);

        // Check if the new moves and annotations data will fit
        // Note: We're actually checking if the data is less than or equal to the game it's
        // replacing;
        // if there is a gap behind this game we're not taking advantage of that
        // (would be easy to do for moves, a bit harder/less efficient for annotations)

        int oldMovesBlobSize =
            database().moveRepository().removeMovesBlob(originalGame.getMovesOffset());
        int newMovesBlobSize = updatedGameData.moveBlob.limit();
        int movesBlobSizeDelta = newMovesBlobSize - oldMovesBlobSize;

        int oldAnnotationsBlobSize =
            database()
                .annotationRepository()
                .removeAnnotationsBlob(originalGame.getAnnotationOffset());
        int newAnnotationsBlobSize =
            updatedGameData.annotationBlob != null ? updatedGameData.annotationBlob.limit() : 0;
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

        updatedGameData
            .gameHeader
            .movesOffset((int) movesOffset)
            .annotationOffset((int) annotationOffset);
        updatedGameData
            .extendedGameHeader
            .movesOffset(movesOffset)
            .annotationOffset((int) annotationOffset);

        if (movesBlobSizeDelta > 0 || annotationsBlobSizeDelta > 0) {
          // It doesn't fit, we need to shift the entire move and/or annotation repository :(
          // This also means we need to update the offset in all game headers after this game,
          // both those outside the transaction and those within the transaction

          if (movesBlobSizeDelta > 0) {
            log.info(
                String.format(
                    "Move blob in game %d is %d bytes longer; adjusting",
                    gameId, movesBlobSizeDelta));
            database().moveRepository().insert(movesOffset, movesBlobSizeDelta);
          }
          if (annotationsBlobSizeDelta > 0 && annotationOffset > 0) {
            log.info(
                String.format(
                    "Annotation blob in game %d is %d bytes longer; adjusting",
                    gameId, annotationsBlobSizeDelta));
            database().annotationRepository().insert(annotationOffset, annotationsBlobSizeDelta);
          }

          // Shift all move and annotations offsets in the persistent storage
          // Note: This could be done much more efficiently with low-level operations
          // Note: Only process the games until the next game in the transaction, and accumulate the
          // shifted offsets
          for (int i = gameId + 1; i <= oldGameCount; i++) {
            GameHeader oldHeader = database().gameHeaderIndex().getGameHeader(i);
            ExtendedGameHeader oldExtendedHeader = database().extendedGameHeaderStorage().get(i);
            int newMovesOffset = oldHeader.movesOffset() + Math.max(0, movesBlobSizeDelta);
            int newAnnotationOffset =
                oldHeader.annotationOffset() == 0
                    ? 0
                    : (oldHeader.annotationOffset() + Math.max(0, annotationsBlobSizeDelta));
            database()
                .gameHeaderIndex()
                .put(
                    i,
                    ImmutableGameHeader.builder()
                        .from(oldHeader)
                        .movesOffset(newMovesOffset)
                        .annotationOffset(newAnnotationOffset)
                        .build());
            database()
                .extendedGameHeaderStorage()
                .put(
                    i,
                    ImmutableExtendedGameHeader.builder()
                        .from(oldExtendedHeader)
                        .movesOffset(newMovesOffset)
                        .annotationOffset(newAnnotationOffset)
                        .build());
            if (moveOffsetStorage != null) {
              moveOffsetStorage.putOffset(i, newMovesOffset);
            }
          }
        }
      }

      // TODO: Merge this for-loop with the previous one, should be possible
      int gameCount = database().gameHeaderIndex().count();
      HashMap<Integer, Integer> updatedMoveOffsets = new HashMap<>();
      HashMap<Integer, TopGamesStorage.TopGameStatus> updatedTopGameStatuses = new HashMap<>();
      for (int gameId : updatedGames.keySet()) {
        GameData updatedGameData = updatedGames.get(gameId);
        ImmutableGameHeader gameHeader;
        if (gameId > gameCount) {
          long movesOffset = database().moveRepository().putMovesBlob(0, updatedGameData.moveBlob);
          long annotationsOffset =
              updatedGameData.annotationBlob != null
                  ? database()
                      .annotationRepository()
                      .putAnnotationsBlob(0, updatedGameData.annotationBlob)
                  : 0;

          gameHeader =
              updatedGameData
                  .gameHeader
                  .movesOffset((int) movesOffset)
                  .annotationOffset((int) annotationsOffset)
                  .build();
          ImmutableExtendedGameHeader extendedGameHeader =
              updatedGameData
                  .extendedGameHeader
                  .movesOffset(movesOffset)
                  .annotationOffset((int) annotationsOffset)
                  .build();

          assert gameId == gameCount + 1;
          int id = database().gameHeaderIndex().add(gameHeader);
          assert gameId == id;
          database().extendedGameHeaderStorage().put(gameId, extendedGameHeader);
          gameCount += 1;
        } else {
          gameHeader = updatedGameData.gameHeader.build();
          ImmutableExtendedGameHeader extendedGameHeader =
              updatedGameData.extendedGameHeader.build();

          database().gameHeaderIndex().put(gameId, gameHeader);
          database().extendedGameHeaderStorage().put(gameId, extendedGameHeader);

          database()
              .moveRepository()
              .putMovesBlob(extendedGameHeader.movesOffset(), updatedGameData.moveBlob);
          if (updatedGameData.annotationBlob != null) {
            database()
                .annotationRepository()
                .putAnnotationsBlob(
                    extendedGameHeader.annotationOffset(), updatedGameData.annotationBlob);
          }
        }

        if (database().gameEventStorage() != null) {
          database()
              .gameEventStorage()
              .put(
                  gameId,
                  updatedGameData.events == null ? new GameEvents() : updatedGameData.events);
        }
        updatedTopGameStatuses.put(gameId, updatedGameData.topGameStatus);
        updatedMoveOffsets.put(gameId, gameHeader.movesOffset());
      }
      if (moveOffsetStorage != null) {
        moveOffsetStorage.putOffsets(updatedMoveOffsets);
      }
      database().topGamesStorage().putGameStatuses(updatedTopGameStatuses);

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

      playerDelta.updateGameEntityIndex();
      tournamentDelta.updateGameEntityIndex();
      annotatorDelta.updateGameEntityIndex();
      sourceDelta.updateGameEntityIndex();
      teamDelta.updateGameEntityIndex();
      gameTagDelta.updateGameEntityIndex();

      database().context().bumpVersion();

      // Clear transaction, enabling further commits
      clearChanges();
    } finally {
      releaseLock(DatabaseContext.DatabaseLock.WRITE);
    }
  }

  /** Clears all changes in the transaction. The transaction will remain open. */
  public void rollback() {
    ensureTransactionIsOpen();

    clearChanges();

    playerTransaction.rollback();
    tournamentTransaction.rollback();
    annotatorTransaction.rollback();
    sourceTransaction.rollback();
    teamTransaction.rollback();
    gameTagTransaction.rollback();
  }

  private void clearChanges() {
    this.updatedGames.clear();
    this.currentGameCount = database().gameHeaderIndex().count();
    this.version = database().context().currentVersion();
    this.playerDelta.clear();
    this.tournamentDelta.clear();
    this.annotatorDelta.clear();
    this.sourceDelta.clear();
    this.teamDelta.clear();
    this.gameTagDelta.clear();
  }

  /**
   * Validates entity references and/or creates new entities base on the model data.
   *
   * @param headerBuilder the game header being created
   * @param extendedHeaderBuilder the extended header being created
   * @param headerModel the source data for the entities in the game being created
   */
  void resolveEntities(
      @NotNull ImmutableGameHeader.Builder headerBuilder,
      @NotNull ImmutableExtendedGameHeader.Builder extendedHeaderBuilder,
      @NotNull GameHeaderModel headerModel) {

    // Resolve entity IDs: use existing ID if valid, otherwise create/lookup from header model
    ImmutableGameHeader build = headerBuilder.build();
    doEntity(
            headerModel,
            build.whitePlayerId(),
            m -> Player.ofFullName(m.getWhite()),
            playerTransaction(),
            headerBuilder::whitePlayerId);

    doEntity(
            headerModel,
            build.blackPlayerId(),
            m -> Player.ofFullName(m.getBlack()),
            playerTransaction(),
            headerBuilder::blackPlayerId);

    doTournament(
            headerModel,
            build.tournamentId(),
            (m, adapter) -> adapter.toTournament(m),
            (m, adapter) -> adapter.toTournamentExtra(m),
            headerBuilder::tournamentId);

    doEntity(
            headerModel,
            build.annotatorId(),
            m -> Annotator.of(m.getAnnotator()),
            annotatorTransaction(),
            headerBuilder::annotatorId);

    doEntity(
            headerModel,
            build.sourceId(),
            m -> gameAdapter().toSource(m),
            sourceTransaction(),
            headerBuilder::sourceId);

    if (headerModel.getWhiteTeam() != null) {
      doEntity(
              headerModel,
              extendedHeaderBuilder.build().whiteTeamId(),
              m -> Team.of(m.getWhiteTeam()),
              teamTransaction(),
              extendedHeaderBuilder::whiteTeamId);
    }

    if (headerModel.getBlackTeam() != null) {
      doEntity(
              headerModel,
              extendedHeaderBuilder.build().blackTeamId(),
              m -> Team.of(m.getBlackTeam()),
              teamTransaction(),
              extendedHeaderBuilder::blackTeamId);
    }

    if (headerModel.getGameTag() != null) {
      doEntity(
              headerModel,
              extendedHeaderBuilder.build().gameTagId(),
              m -> GameTag.of(m.getGameTag()),
              gameTagTransaction(),
              extendedHeaderBuilder::gameTagId);
    }
  }

  /**
   * Assigns entity id's to the game header builds based on data in the model. References to new
   * entities will be created in their respective entity index transaction.
   *
   * @param header the game header being created
   * @param headerModel the source data for the entities in the game being created
   */
  void resolveEntities(
      @NotNull ImmutableGameHeader.Builder header, @NotNull TextHeaderModel headerModel) {
    header
        .whitePlayerId(-1)
        .blackPlayerId(-1)
        .tournamentId(
            tournamentTransaction.getOrCreate(
                Tournament.of(headerModel.tournament(), headerModel.tournamentDate())))
        .annotatorId(annotatorTransaction.getOrCreate(Annotator.of(headerModel.annotator())))
        .sourceId(sourceTransaction.getOrCreate(Source.of(headerModel.source())));
  }

  public void resolveEntities(
      @NotNull ImmutableGameHeader.Builder header,
      @NotNull ImmutableExtendedGameHeader.Builder extendedHeader,
      @NotNull Game game) {
    if (game.database() == database()) {
      // Same database, just copy the entity id's
      header.whitePlayerId(game.whitePlayerId());
      header.blackPlayerId(game.blackPlayerId());
      header.tournamentId(game.tournamentId());
      header.annotatorId(game.annotatorId());
      header.sourceId(game.sourceId());
      extendedHeader.whiteTeamId(game.whiteTeamId());
      extendedHeader.blackTeamId(game.blackTeamId());
      extendedHeader.gameTagId(game.gameTagId());
    } else {
      if (game.guidingText()) {
        header.tournamentId(
            tournamentTransaction.getOrCreate(game.tournament(), game.tournamentExtra()));
        header.annotatorId(annotatorTransaction.getOrCreate(game.annotator()));
        header.sourceId(sourceTransaction.getOrCreate(game.source()));
      } else {
        header.whitePlayerId(playerTransaction.getOrCreate(game.white()));
        header.blackPlayerId(playerTransaction.getOrCreate(game.black()));
        header.tournamentId(
            tournamentTransaction.getOrCreate(game.tournament(), game.tournamentExtra()));
        header.annotatorId(annotatorTransaction.getOrCreate(game.annotator()));
        header.sourceId(sourceTransaction.getOrCreate(game.source()));

        extendedHeader.whiteTeamId(
            game.whiteTeam() == null ? -1 : teamTransaction.getOrCreate(game.whiteTeam()));
        extendedHeader.blackTeamId(
            game.blackTeam() == null ? -1 : teamTransaction.getOrCreate(game.blackTeam()));
        extendedHeader.gameTagId(
            game.gameTag() == null ? -1 : gameTagTransaction.getOrCreate(game.gameTag()));
      }
    }
  }

  /**
   * Class representing which games in the transaction entities are being added to or removed from.
   * Also contains the logic for updating the entity index once the database transaction is
   * committed.
   *
   * @param <T> the type of entity
   */
  private class EntityDelta<T extends Entity & Comparable<T>> {
    // entityId -> gameId -> number of occurrences in the game
    // Entities need to be in order
    private final @NotNull Map<Integer, TreeMap<Integer, Integer>> newEntityGameCount =
        new TreeMap<>();
    // entityId -> change in num occurrences
    private final @NotNull Map<Integer, Integer> newEntityGameDelta = new HashMap<>();
    private final @NotNull BiPredicate<Game, Integer> hasEntity;
    private final @NotNull EntityType entityType;

    private EntityDelta(
        @NotNull EntityType entityType, @NotNull BiPredicate<Game, Integer> hasEntity) {
      this.entityType = entityType;
      this.hasEntity = hasEntity;
    }

    private void clear() {
      newEntityGameCount.clear();
      newEntityGameDelta.clear();
    }

    private boolean isEmpty() {
      return newEntityGameCount.isEmpty() && newEntityGameDelta.isEmpty();
    }

    private void update(int gameId, int addEntityId, int removeEntityId) {
      update(gameId, addEntityId, -1, removeEntityId, -1);
    }

    private void update(
        int gameId, int addEntityId1, int addEntityId2, int removeEntityId1, int removeEntityId2) {
      boolean isSameEntities =
          (addEntityId1 == removeEntityId1 && addEntityId2 == removeEntityId2)
              || (addEntityId2 == removeEntityId1 && addEntityId1 == removeEntityId2);
      // If it's the same entity references in the game before and after, then there is no need to
      // update any statistics
      if (!isSameEntities) {
        HashMap<Integer, Integer> deltaCount = new HashMap<>();
        deltaCount.put(removeEntityId1, 0);
        deltaCount.put(removeEntityId2, 0);
        deltaCount.put(addEntityId1, deltaCount.getOrDefault(addEntityId1, 0) + 1);
        deltaCount.put(addEntityId2, deltaCount.getOrDefault(addEntityId2, 0) + 1);

        for (Map.Entry<Integer, Integer> entry : deltaCount.entrySet()) {
          newEntityGameCount
              .computeIfAbsent(entry.getKey(), integer -> new TreeMap<>())
              .put(gameId, entry.getValue());
        }

        newEntityGameDelta.put(addEntityId1, newEntityGameDelta.getOrDefault(addEntityId1, 0) + 1);
        newEntityGameDelta.put(addEntityId2, newEntityGameDelta.getOrDefault(addEntityId2, 0) + 1);
        newEntityGameDelta.put(
            removeEntityId1, newEntityGameDelta.getOrDefault(removeEntityId1, 0) - 1);
        newEntityGameDelta.put(
            removeEntityId2, newEntityGameDelta.getOrDefault(removeEntityId2, 0) - 1);
      }
    }

    public void apply(@NotNull EntityIndexWriteTransaction<T> entityTransaction) {
      GameEntityIndex gameEntityIndex = database().gameEntityIndex(entityType);

      for (int entityId : newEntityGameCount.keySet()) {
        if (entityId < 0) {
          // In the updates we might have set the "no entity" -1, just ignore it here
          continue;
        }

        T entity = entityTransaction.get(entityId);

        // Find the first game in the transaction where entityId is used
        int firstTransactionGameId = 0;
        for (Map.Entry<Integer, Integer> entry : newEntityGameCount.get(entityId).entrySet()) {
          if (entry.getValue() > 0 && entry.getKey() > 0) {
            firstTransactionGameId = entry.getKey();
            break;
          }
        }

        int newFirstGameId = entity.firstGameId();
        boolean firstIdSearch = false;
        if (newFirstGameId == 0
            || (firstTransactionGameId > 0 && firstTransactionGameId < newFirstGameId)) {
          // Entity didn't exist before, or we have a new first game
          newFirstGameId = firstTransactionGameId;
        } else {
          // The old first game reference is gone, we'll have to search for the new first game
          // (it might not even be in this transaction)
          firstIdSearch = true;
        }

        int newCount = entity.count() + newEntityGameDelta.get(entityId);

        if (newCount == 0) {
          entityTransaction.deleteEntity(entityId);
        } else {
          if (firstIdSearch) {
            if (gameEntityIndex != null) {
              // This is the default case, use the entity index to find the new first game.
              // Some care needs to be taken since we haven't yet updated this index to reflect this
              // commit!
              newFirstGameId =
                  firstTransactionGameId; // it may be another game in this transaction!
              for (int gameId : gameEntityIndex.iterable(entityId, entityType, false)) {
                if (newFirstGameId > 0 && newFirstGameId < gameId) break;
                if (hasEntity.test(DatabaseWriteTransaction.super.getGame(gameId), entityId)) {
                  newFirstGameId = gameId;
                  break;
                }
              }
            } else {
              // TODO: there are no test for this now, parametrize tests in
              // DatabaseWriteTransactionTest that checks firstGame
              // With no entity index in place, we do a slow iterative scan to find the new
              // firstGameId
              GameHeaderIndex ix = DatabaseWriteTransaction.this.database().gameHeaderIndex();
              newFirstGameId = 0;
              for (int i = 1; i <= ix.count(); i++) {
                if (hasEntity.test(DatabaseWriteTransaction.super.getGame(i), entityId)) {
                  newFirstGameId = i;
                  break;
                }
              }
            }

            assert newFirstGameId > 0;
          }
          if (newCount != entity.count() || newFirstGameId != entity.firstGameId()) {
            T newEntity = (T) entity.withCountAndFirstGameId(newCount, newFirstGameId);
            entityTransaction.putEntityByKey(newEntity);
          }
        }
      }
    }

    public void updateGameEntityIndex() {
      GameEntityIndex gameEntityIndex = database().gameEntityIndex(entityType);
      if (gameEntityIndex != null) {
        for (Map.Entry<Integer, TreeMap<Integer, Integer>> entry : newEntityGameCount.entrySet()) {
          int entityId = entry.getKey();
          if (entityId >= 0) {
            gameEntityIndex.updateEntity(entityId, entityType, entry.getValue());
          }
        }
      }
    }
  }

  /**
   * Updates the entity statistics for all involved entities in the old game that was replaced and
   * the new game that was added.
   */
  private void updateEntityStats(
      int gameId,
      @Nullable Game oldGame,
      @NotNull GameHeader newGameHeader,
      @NotNull ExtendedGameHeader newExtendedGameHeader) {
    // oldGame is the game, if any, that we're replacing
    // It may be a game that's already been updated in this transaction
    playerDelta.update(
        gameId,
        newGameHeader.whitePlayerId(),
        newGameHeader.blackPlayerId(),
        oldGame == null ? -1 : oldGame.header().whitePlayerId(),
        oldGame == null ? -1 : oldGame.header().blackPlayerId());
    tournamentDelta.update(
        gameId,
        newGameHeader.tournamentId(),
        oldGame == null ? -1 : oldGame.header().tournamentId());
    annotatorDelta.update(
        gameId, newGameHeader.annotatorId(), oldGame == null ? -1 : oldGame.header().annotatorId());
    sourceDelta.update(
        gameId, newGameHeader.sourceId(), oldGame == null ? -1 : oldGame.header().sourceId());
    teamDelta.update(
        gameId,
        newExtendedGameHeader.whiteTeamId(),
        newExtendedGameHeader.blackTeamId(),
        oldGame == null ? -1 : oldGame.extendedHeader().whiteTeamId(),
        oldGame == null ? -1 : oldGame.extendedHeader().blackTeamId());
    gameTagDelta.update(
        gameId,
        newExtendedGameHeader.gameTagId(),
        oldGame == null ? -1 : oldGame.extendedHeader().gameTagId());
  }
}
