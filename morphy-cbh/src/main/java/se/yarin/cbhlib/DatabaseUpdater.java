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
import se.yarin.cbhlib.games.IGameHeader;
import se.yarin.cbhlib.storage.EntityStorageException;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

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

        int annotationOfs = database.getAnnotationBase().putAnnotations(gameId, 0, model.moves());
        int movesOfs = database.getMovesBase().putMoves(0, model.moves());

        GameHeader gameHeader = loader.createGameHeader(model, movesOfs, annotationOfs);
        ExtendedGameHeader extendedGameHeader = loader.createExtendedGameHeader(model, gameId, movesOfs, annotationOfs);

        gameHeader = database.getHeaderBase().add(gameHeader);
        assert gameHeader.getId() == gameId;

        extendedGameHeader = database.getExtendedHeaderBase().add(extendedGameHeader);

        updateEntityStats(null, null, gameHeader, extendedGameHeader);

        return new Game(database, gameHeader, extendedGameHeader);
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
        GameHeader oldGameHeader = database.getHeaderBase().getGameHeader(gameId);
        ExtendedGameHeader oldExtendedGameHeader = database.getExtendedHeaderBase().getExtendedGameHeader(gameId);
        if (oldGameHeader == null) {
            throw new IllegalArgumentException("There is no game with game id " + gameId);
        }
        if (oldExtendedGameHeader == null) {
            // TODO: This shouldn't fail
            throw new IllegalArgumentException("Extended game header missing for game id " + gameId);
        }

        // If necessary, first insert space in the moves and annotation base
        // In case the previous game didn't have annotations, we will know where to store them
        int oldAnnotationOfs = prepareReplace(oldGameHeader, oldExtendedGameHeader, model.moves());

        int oldMovesOffset = oldGameHeader.getMovesOffset();
        // TODO: Resolve cases when oldGameHeader and oldExtendedGameHeader differ due to 32 bit limits

        int movesOfs = database.getMovesBase().putMoves(oldMovesOffset, model.moves());
        int annotationOfs = database.getAnnotationBase().putAnnotations(gameId, oldAnnotationOfs, model.moves());

        assert movesOfs == oldMovesOffset; // Since we inserted space above, we should get the same offset
        assert oldAnnotationOfs == 0 || annotationOfs == 0 || annotationOfs == oldAnnotationOfs;

        GameHeader gameHeader = loader.createGameHeader(model, oldGameHeader.getMovesOffset(), annotationOfs);
        ExtendedGameHeader extendedGameHeader = loader.createExtendedGameHeader(model, gameId,
                oldExtendedGameHeader.getMovesOffset(), annotationOfs);

        gameHeader = database.getHeaderBase().update(gameId, gameHeader);
        extendedGameHeader = database.getExtendedHeaderBase().update(gameId, extendedGameHeader);
        updateEntityStats(oldGameHeader, oldExtendedGameHeader, gameHeader, extendedGameHeader);

        return new Game(database, gameHeader, extendedGameHeader);
    }

    /**
     * Allocates enough space in the moves and annotation database to fit the new game
     * @param gameHeader the header of the game to replace
     * @param extendedGameHeader the extended header of the game to replace
     * @param moves the model containing the new moves and annotations
     * @return the new offset to store the annotations
     * @throws ChessBaseIOException if there was an IO error when preparing the replace
     */
    private int prepareReplace(@NonNull GameHeader gameHeader, @NonNull ExtendedGameHeader extendedGameHeader, @NonNull GameMovesModel moves) {
        int gameId = gameHeader.getId();

        // This code is a bit messy. In the worst case, it does three sweeps over
        // the game header base to update all the information. It could be done
        // in one sweep, but then it gets even messier. Probably not worth the effort.

        // Also, if something goes wrong here, the database might be in an inconsistent state!
        // The game (and annotation) data is bulk moved first, then all game headers are updated.
        // This is fast, but unsafe...


        // Ensure there is enough room to fit the game data.
        // If not, insert bytes and update all game headers.
        int insertedGameBytes = database.getMovesBase().preparePutBlob(gameHeader.getMovesOffset(), moves);
        if (insertedGameBytes > 0) {
            database.getHeaderBase().adjustMovesOffset(gameId + 1, gameHeader.getMovesOffset(), insertedGameBytes);
            database.getExtendedHeaderBase().adjustMovesOffset(gameId + 1, extendedGameHeader.getMovesOffset(), insertedGameBytes);
        }

        // Ensure there is enough room to fit the annotation data.
        // If not, insert bytes and update all game headers.
        // This is a bit trickier since the game might not have had annotations before,
        // in which case we must find the next game that did have annotations and use that offset.
        int insertedAnnotationBytes = 0, oldAnnotationOfs = gameHeader.getAnnotationOffset();
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
            insertedAnnotationBytes = database.getAnnotationBase().preparePutBlob(gameHeader.getAnnotationOffset(),
                    gameHeader.getAnnotationOffset(), moves);
        }
        if (insertedAnnotationBytes > 0) {
            database.getHeaderBase().adjustAnnotationOffset(gameId + 1, gameHeader.getAnnotationOffset(), insertedAnnotationBytes);
            database.getExtendedHeaderBase().adjustAnnotationOffset(gameId + 1, extendedGameHeader.getAnnotationOffset(), insertedAnnotationBytes);
        }

        return oldAnnotationOfs;
    }

    private class GameHeaderDeltaMap<T extends Entity & Comparable<T>> extends DeltaMap<T, GameHeader> {

        public GameHeaderDeltaMap(EntityBase<T> base, GameHeader newGameHeader, BiPredicate<GameHeader, Integer> predicate) {
            super(base, newGameHeader, predicate);
        }

        @Override
        protected Stream<GameHeader> headerStream() {
            return database.getHeaderBase().stream();
        }
    }

    private class ExtendedGameHeaderDeltaMap<T extends Entity & Comparable<T>> extends DeltaMap<T, ExtendedGameHeader> {

        public ExtendedGameHeaderDeltaMap(EntityBase<T> base, ExtendedGameHeader newGameHeader, BiPredicate<ExtendedGameHeader, Integer> predicate) {
            super(base, newGameHeader, predicate);
        }

        @Override
        protected Stream<ExtendedGameHeader> headerStream() {
            return database.getExtendedHeaderBase().stream();
        }
    }

    private abstract class DeltaMap<T extends Entity & Comparable<T>, U extends IGameHeader> {
        private final Map<Integer, Integer> map = new HashMap<>();
        private final EntityBase<T> base;
        private final BiPredicate<U, Integer> predicate;
        private final U newGameHeader;

        public DeltaMap(EntityBase<T> base, U newGameHeader, BiPredicate<U, Integer> predicate) {
            this.base = base;
            this.newGameHeader = newGameHeader;
            this.predicate = predicate;
        }

        private void update(int id, int diff) {
            if (map.containsKey(id)) {
                map.put(id, map.get(id) + diff);
            } else {
                map.put(id, diff);
            }
        }

        protected abstract Stream<U> headerStream();

        // TODO: Replace this with a proper search implementation in the EntityBase
        private int findFirstGame(int entityId) {
            return headerStream()
                    .map(gameHeader -> gameHeader.getId() == newGameHeader.getId() ? newGameHeader : gameHeader)
                    // When checking the game we're actually updating, don't pick the one from the db
                    .filter(actual -> predicate.test(actual, entityId))
                    .map(IGameHeader::getId)
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
                                    newGameHeader.getId() : Math.min(newFirstGameId, newGameHeader.getId());
                        } else if (newGameHeader.getId() == entity.getFirstGameId()) {
                            newFirstGameId = findFirstGame(entity.getId());
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

    private void updateEntityStats(
            GameHeader oldHeader,
            ExtendedGameHeader oldExtendedHeader,
            @NonNull GameHeader newHeader,
            @NonNull ExtendedGameHeader newExtendedHeader) {
        assert oldHeader == null || oldHeader.getId() != 0;
        assert newHeader.getId() != 0;

        DeltaMap<PlayerEntity, GameHeader> playerDelta = new GameHeaderDeltaMap<>(database.getPlayerBase(), newHeader,
                (header, entityId) -> header.getWhitePlayerId() == entityId || header.getBlackPlayerId() == entityId);
        DeltaMap<TournamentEntity, GameHeader> tournamentDelta = new GameHeaderDeltaMap<>(database.getTournamentBase(), newHeader,
                (header, entityId) -> header.getTournamentId() == entityId);
        DeltaMap<AnnotatorEntity, GameHeader> annotatorDelta = new GameHeaderDeltaMap<>(database.getAnnotatorBase(), newHeader,
                (header, entityId) -> header.getAnnotatorId() == entityId);
        DeltaMap<SourceEntity, GameHeader> sourceDelta = new GameHeaderDeltaMap<>(database.getSourceBase(), newHeader,
                (header, entityId) -> header.getSourceId() == entityId);
        DeltaMap<TeamEntity, ExtendedGameHeader> teamDelta = new ExtendedGameHeaderDeltaMap<>(database.getTeamBase(), newExtendedHeader,
                (extendedHeader, entityId) -> extendedHeader.getWhiteTeamId() == entityId || extendedHeader.getBlackTeamId() == entityId);

        if (oldHeader != null) {
            playerDelta.update(oldHeader.getWhitePlayerId(), -1);
            playerDelta.update(oldHeader.getBlackPlayerId(), -1);
            tournamentDelta.update(oldHeader.getTournamentId(), -1);
            annotatorDelta.update(oldHeader.getAnnotatorId(), -1);
            sourceDelta.update(oldHeader.getSourceId(), -1);
        }
        if (oldExtendedHeader != null) {
            teamDelta.update(oldExtendedHeader.getWhiteTeamId(), -1);
            teamDelta.update(oldExtendedHeader.getBlackTeamId(), -1);
        }
        playerDelta.update(newHeader.getWhitePlayerId(), 1);
        playerDelta.update(newHeader.getBlackPlayerId(), 1);
        tournamentDelta.update(newHeader.getTournamentId(), 1);
        annotatorDelta.update(newHeader.getAnnotatorId(), 1);
        sourceDelta.update(newHeader.getSourceId(), 1);
        teamDelta.update(newExtendedHeader.getWhiteTeamId(), 1);
        teamDelta.update(newExtendedHeader.getBlackTeamId(), 1);

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
