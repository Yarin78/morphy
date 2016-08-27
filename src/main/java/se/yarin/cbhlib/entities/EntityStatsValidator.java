package se.yarin.cbhlib.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.ChessBaseException;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.GameHeader;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityStatsValidator {
    private static final Logger log = LoggerFactory.getLogger(EntityStatsValidator.class);

    private Database db;

    public EntityStatsValidator(@NonNull Database db) {
        this.db = db;
    }

    public void readAllGames() throws IOException, ChessBaseException {
        for (GameHeader gameHeader : db.getHeaderBase()) {
            db.getGameModel(gameHeader.getId());
        }
    }

    public void validateMovesAndAnnotationOffsets() throws ChessBaseException {
        int lastMovesOfs = 0, lastAnnotationOfs = 0;
        for (GameHeader gameHeader : db.getHeaderBase()) {
            if (gameHeader.getMovesOffset() <= lastMovesOfs) {
                throw new ChessBaseException(String.format("Game %d has moves at offset %d while the previous game had moves at offset %d",
                        gameHeader.getId(), gameHeader.getMovesOffset(), lastMovesOfs));
            }
            lastMovesOfs = gameHeader.getMovesOffset();
/*
            if (gameHeader.getAnnotationOffset() > 0) {
                if (gameHeader.getAnnotationOffset() <= lastAnnotationOfs) {
                    throw new ChessBaseException(String.format("Game %d has annotations at offset %d while the last annotated game had annotations at offset %d",
                            gameHeader.getId(), gameHeader.getAnnotationOffset(), lastAnnotationOfs));
                }
                lastAnnotationOfs = gameHeader.getAnnotationOffset();
            }
*/
        }
    }

    @AllArgsConstructor
    @Data
    private static class EntityStats {
        private int count, firstGameId;
    }

    private Map<Integer, EntityStats> players = new HashMap<>();
    private Map<Integer, EntityStats> tournaments = new HashMap<>();
    private Map<Integer, EntityStats> annotators = new HashMap<>();
    private Map<Integer, EntityStats> sources = new HashMap<>();

    private void update(Map<Integer, EntityStats> map, int entityId, int gameId) {
        EntityStats stats = map.get(entityId);
        if (stats == null) {
            map.put(entityId, new EntityStats(1, gameId));
        } else {
            stats.setCount(stats.getCount() + 1);
        }
    }

    private <T extends Entity> void validate(List<T> all, Map<Integer, EntityStats> stats, boolean throwOnError) throws EntityStorageException {
        int errorCnt = 0;
        for (T entity : all) {
            EntityStats expected = stats.get(entity.getId());
            if (expected == null) {
                String msg = String.format("Entity %d (%s) occurs in 0 games but stats says %d games and first game %d",
                        entity.getId(), entity.toString(), entity.getCount(), entity.getFirstGameId());
                if (throwOnError) {
                    throw new EntityStorageException(msg);
                }
                log.warn(msg);
                errorCnt++;
            } else {
                boolean error = false;
                if (expected.getCount() != entity.getCount()) {
                    String msg = String.format("Entity %d (%s) has %d games but stats says %d",
                            entity.getId(), entity.toString(), expected.getCount(), entity.getCount());
                    if (throwOnError) {
                        throw new EntityStorageException(msg);
                    }
                    log.warn(msg);
                    error = true;
                }

                if (expected.getFirstGameId() != entity.getFirstGameId()) {
                    String msg = String.format("Entity %d (%s) first game is %d but stats says %d",
                            entity.getId(), entity.toString(), expected.getFirstGameId(), entity.getFirstGameId());
                    if (throwOnError) {
                        throw new EntityStorageException(msg);
                    }
                    log.warn(msg);
                    error = true;
                }
                if (error) errorCnt++;
            }
            if (errorCnt > 20) {
                log.warn("Too many errors, aborting validation");
                break;
            }
        }
    }

    /**
     * Checks that the count and firstGameId matches for all entities in the database
     * @param throwOnError if true, throw {@link EntityStorageException} if the stats
     *                     doesn't match; otherwise just log mismatches
     */
    public void validateEntityStatistics(boolean throwOnError)
            throws IOException, EntityStorageException {

        players.clear();
        tournaments.clear();
        annotators.clear();
        sources.clear();

        for (GameHeader gameHeader : db.getHeaderBase()) {
            int gameId = gameHeader.getId();
            update(players, gameHeader.getWhitePlayerId(), gameId);
            update(players, gameHeader.getBlackPlayerId(), gameId);
            update(tournaments, gameHeader.getTournamentId(), gameId);
            update(annotators, gameHeader.getAnnotatorId(), gameId);
            update(sources, gameHeader.getSourceId(), gameId);
        }

        validate(db.getPlayerBase().getAll(), players, throwOnError);
        validate(db.getTournamentBase().getAll(), tournaments, throwOnError);
        validate(db.getAnnotatorBase().getAll(), annotators, throwOnError);
        validate(db.getSourceBase().getAll(), sources, throwOnError);
    }
}
