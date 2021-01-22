package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.entities.*;
import se.yarin.cbhlib.exceptions.ChessBaseIOException;
import se.yarin.cbhlib.exceptions.ChessBaseInvalidDataException;
import se.yarin.cbhlib.games.ExtendedGameHeader;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.GameLoader;
import se.yarin.cbhlib.games.search.*;
import se.yarin.cbhlib.storage.EntityStorageException;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Contains the logic that coordinates the updates across the different database files when a game is added/replaced/deleted
 */
public class DatabaseUpdater {
    private static final Logger log = LoggerFactory.getLogger(DatabaseUpdater.class);

    private final Database database;
    private final GameLoader loader;

    public DatabaseUpdater(Database database, GameLoader loader) {
        this.database = database;
        this.loader = loader;
    }

    /**
     * Adds a new game to the database
     * @param model the model of the game to add
     * @return the added game
     * @throws ChessBaseInvalidDataException if the game model contained invalid data
     * @throws ChessBaseIOException if the game couldn't be stored due to an IO error
     */
    public Game addGame(@NonNull GameModel model) throws ChessBaseInvalidDataException {
        int gameId = database.getHeaderBase().getNextGameId();

        long annotationOfs = database.getAnnotationBase().putAnnotations(gameId, 0, model.moves());
        long movesOfs = database.getMovesBase().putMoves(0, model.moves());

        GameHeader gameHeader = loader.createGameHeader(model, movesOfs, annotationOfs);
        ExtendedGameHeader extendedGameHeader = loader.createExtendedGameHeader(model, gameId, movesOfs, annotationOfs);

        gameHeader = database.getHeaderBase().add(gameHeader);
        assert gameHeader.getId() == gameId;

        extendedGameHeader = database.getExtendedHeaderBase().add(extendedGameHeader);

        Game addedGame = new Game(database, gameHeader, extendedGameHeader);
        updateEntityStats(null, addedGame);

        return addedGame;
    }

    /**
     * Replaces a game in the database
     * @param gameId the id of the game to replace
     * @param model the model of the game to replace
     * @return the saved game
     * @throws ChessBaseInvalidDataException if the game model contained invalid data
     * @throws ChessBaseIOException if the game couldn't be stored due to an IO error
     */
    public Game replaceGame(int gameId, @NonNull GameModel model) throws ChessBaseInvalidDataException {
        Game oldGame = database.getGame(gameId);

        // If necessary, first insert space in the moves and annotation base
        // In case the previous game didn't have annotations, we will know where to store them
        long oldAnnotationOfs = prepareReplace(oldGame, model.moves());

        long oldMovesOffset = oldGame.getMovesOffset();

        long movesOfs = database.getMovesBase().putMoves(oldMovesOffset, model.moves());
        long annotationOfs = database.getAnnotationBase().putAnnotations(gameId, oldAnnotationOfs, model.moves());

        assert movesOfs == oldMovesOffset; // Since we inserted space above, we should get the same offset
        assert oldAnnotationOfs == 0 || annotationOfs == 0 || annotationOfs == oldAnnotationOfs;

        GameHeader gameHeader = loader.createGameHeader(model, oldGame.getMovesOffset(), annotationOfs);
        ExtendedGameHeader extendedGameHeader = loader.createExtendedGameHeader(model, gameId,
                oldGame.getMovesOffset(), annotationOfs);

        gameHeader = database.getHeaderBase().update(gameId, gameHeader);
        extendedGameHeader = database.getExtendedHeaderBase().update(gameId, extendedGameHeader);

        Game updatedGame = new Game(database, gameHeader, extendedGameHeader);

        updateEntityStats(oldGame, updatedGame);

        return updatedGame;
    }

    /**
     * Allocates enough space in the moves and annotation database to fit the new game
     * @param game the game to replace
     * @param moves the model containing the new moves and annotations
     * @return the new offset to store the annotations
     * @throws ChessBaseIOException if there was an IO error when preparing the replace
     */
    private long prepareReplace(@NonNull Game game, @NonNull GameMovesModel moves) {
        int gameId = game.getId();

        // This code is a bit messy. In the worst case, it does three sweeps over
        // the game header base to update all the information. It could be done
        // in one sweep, but then it gets even messier. Probably not worth the effort.

        // Also, if something goes wrong here, the database might be in an inconsistent state!
        // The game (and annotation) data is bulk moved first, then all game headers are updated.
        // This is fast, but unsafe...


        // Ensure there is enough room to fit the game data.
        // If not, insert bytes and update all game headers.
        int insertedGameBytes = database.getMovesBase().preparePutBlob(game.getMovesOffset(), moves);
        if (insertedGameBytes > 0) {
            database.getHeaderBase().adjustMovesOffset(gameId + 1, game.getMovesOffset(), insertedGameBytes);
            database.getExtendedHeaderBase().adjustMovesOffset(gameId + 1, game.getMovesOffset(), insertedGameBytes);
        }

        // Ensure there is enough room to fit the annotation data.
        // If not, insert bytes and update all game headers.
        // This is a bit trickier since the game might not have had annotations before,
        // in which case we must find the next game that did have annotations and use that offset.
        long insertedAnnotationBytes = 0, oldAnnotationOfs = game.getAnnotationOffset();
        if (oldAnnotationOfs == 0) {
            // This game has no annotations. Find first game after this one that does
            // and use that annotation offset.
            oldAnnotationOfs = database.getHeaderBase()
                    .stream(gameId + 1)
                    .map(GameHeader::getAnnotationOffset)
                    .filter(ofs -> ofs > 0)
                    .findFirst()
                    .orElse(0);
            if (oldAnnotationOfs != 0) {
                insertedAnnotationBytes = database.getAnnotationBase().preparePutBlob(0, oldAnnotationOfs, moves);
            }
        } else {
            // This game already has annotations, so we know the annotation offset
            insertedAnnotationBytes = database.getAnnotationBase().preparePutBlob(game.getAnnotationOffset(),
                    game.getAnnotationOffset(), moves);
        }
        if (insertedAnnotationBytes > 0) {
            database.getHeaderBase().adjustAnnotationOffset(gameId + 1, game.getAnnotationOffset(), insertedAnnotationBytes);
            database.getExtendedHeaderBase().adjustAnnotationOffset(gameId + 1, game.getAnnotationOffset(), insertedAnnotationBytes);
        }

        return oldAnnotationOfs;
    }

    private class DeltaMap<T extends Entity & Comparable<T>> {
        private final Map<Integer, Integer> map = new HashMap<>();
        private final EntityBase<T> base;
        private final Game newGame;
        private final Function<T, SearchFilter> searchFilterFactory;

        public DeltaMap(@NonNull EntityBase<T> base, @NonNull Game newGame, @NonNull Function<T, SearchFilter> searchFilterFactory) {
            this.base = base;
            this.newGame = newGame;
            this.searchFilterFactory = searchFilterFactory;
        }

        private void update(int id, int diff) {
            if (map.containsKey(id)) {
                map.put(id, map.get(id) + diff);
            } else {
                map.put(id, diff);
            }
        }

        private int findFirstGame(T entity) {
            GameSearcher gameSearcher = new GameSearcher(database);
            gameSearcher.addFilter(searchFilterFactory.apply(entity));
            return gameSearcher
                    .streamSearch()
                    .map(Game::getId)
                    .findFirst()
                    .orElse(0);
        }

        private void applyChanges() throws EntityStorageException {
            for (Map.Entry<Integer, Integer> delta : map.entrySet()) {
                if (delta.getValue() != 0) {
                    T entity = base.get(delta.getKey());
                    int newCount = entity.getCount() + delta.getValue();
                    if (newCount == 0) {
                        base.delete(entity);
                    } else {
                        int newFirstGameId = entity.getFirstGameId();
                        if (delta.getValue() > 0) {
                            newFirstGameId = entity.getFirstGameId() == 0 ?
                                    newGame.getId() : Math.min(newFirstGameId, newGame.getId());
                        } else if (newGame.getId() == entity.getFirstGameId()) {
                            newFirstGameId = findFirstGame(entity);
                        }

                        T newEntity = entity.withNewStats(newCount, newFirstGameId);
                        try {
                            base.put(entity.getId(), newEntity);
                        } catch (EntityStorageException e) {
                            // Shouldn't happen since we're not changing the key
                            log.error("Internal error when updating entity stats", e);
                        }
                    }
                }
            }
        }
    }

    /**
     * Updates the entity statistics for all involved entities in the
     * old game that was replaced and the new game that was added.
     * Assumes the game change in the actual database has already been done.
     * @param oldGame the replaced game (if any)
     * @param newGame the newly added game
     */
    private void updateEntityStats(Game oldGame, @NonNull Game newGame) {
        assert oldGame == null || oldGame.getId() != 0;
        assert newGame.getId() != 0;

        DeltaMap<PlayerEntity> playerDelta = new DeltaMap<>(
                database.getPlayerBase(),
                newGame,
                entity -> new PlayerFilter(database, entity, PlayerFilter.PlayerColor.ANY));
        DeltaMap<TournamentEntity> tournamentDelta = new DeltaMap<>(
                database.getTournamentBase(),
                newGame,
                entity -> new TournamentFilter(database, entity));
        DeltaMap<AnnotatorEntity> annotatorDelta = new DeltaMap<>(
                database.getAnnotatorBase(),
                newGame,
                entity -> new AnnotatorFilter(database, entity));
        DeltaMap<SourceEntity> sourceDelta = new DeltaMap<>(
                database.getSourceBase(),
                newGame,
                entity -> new SourceFilter(database, entity));
        DeltaMap<TeamEntity> teamDelta = new DeltaMap<>(
                database.getTeamBase(),
                newGame,
                entity -> new TeamFilter(database, entity));

        if (oldGame != null) {
            playerDelta.update(oldGame.getWhitePlayerId(), -1);
            playerDelta.update(oldGame.getBlackPlayerId(), -1);
            tournamentDelta.update(oldGame.getTournamentId(), -1);
            annotatorDelta.update(oldGame.getAnnotatorId(), -1);
            sourceDelta.update(oldGame.getSourceId(), -1);
            teamDelta.update(oldGame.getWhiteTeamId(), -1);
            teamDelta.update(oldGame.getBlackTeamId(), -1);
        }
        playerDelta.update(newGame.getWhitePlayerId(), 1);
        playerDelta.update(newGame.getBlackPlayerId(), 1);
        tournamentDelta.update(newGame.getTournamentId(), 1);
        annotatorDelta.update(newGame.getAnnotatorId(), 1);
        sourceDelta.update(newGame.getSourceId(), 1);
        teamDelta.update(newGame.getWhiteTeamId(), 1);
        teamDelta.update(newGame.getBlackTeamId(), 1);

        try {
            playerDelta.applyChanges();
            tournamentDelta.applyChanges();
            annotatorDelta.applyChanges();
            sourceDelta.applyChanges();
            teamDelta.applyChanges();
        } catch (EntityStorageException e) {
            throw new ChessBaseIOException("Entity storage is in an inconsistent state. Please run repair.", e);
        }
    }
}
