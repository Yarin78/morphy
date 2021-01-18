package se.yarin.cbhlib.games;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.annotations.AnnotationStatistics;
import se.yarin.cbhlib.annotations.StatisticalAnnotation;
import se.yarin.cbhlib.entities.*;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.cbhlib.exceptions.ChessBaseInvalidDataException;
import se.yarin.cbhlib.storage.EntityStorageException;
import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotation;

import java.util.EnumSet;

/**
 * Contains methods for converting game data from the format as store din {@link se.yarin.cbhlib.Database}
 * and the generic {@link se.yarin.chess.GameModel}
 */
public class GameLoader {
    private static final Logger log = LoggerFactory.getLogger(GameLoader.class);

    public static final String DATABASE_ID = "databaseId";
    public static final String WHITE_ID = "whiteId";
    public static final String BLACK_ID = "blackId";
    public static final String EVENT_ID = "eventId";
    public static final String ANNOTATOR_ID = "annotatorId";
    public static final String SOURCE_ID = "sourceId";
    public static final String WHITE_TEAM_ID = "whiteTeamId";
    public static final String BLACK_TEAM_ID = "blackTeamId";

    private final Database database;

    public GameLoader(Database database) {
        this.database = database;
    }

    public GameHeaderModel getHeaderModel(GameHeader header, ExtendedGameHeader extendedHeader) {
        GameHeaderModel model = new GameHeaderModel();

        if (header.isGuidingText()) {
            throw new IllegalArgumentException("Can't get game header model for a guiding text (id " + header.getId() + ")");
        }

        PlayerEntity whitePlayer = database.getPlayerBase().get(header.getWhitePlayerId());
        PlayerEntity blackPlayer = database.getPlayerBase().get(header.getBlackPlayerId());
        AnnotatorEntity annotator = database.getAnnotatorBase().get(header.getAnnotatorId());
        SourceEntity source = database.getSourceBase().get(header.getSourceId());
        TournamentEntity tournament = database.getTournamentBase().get(header.getTournamentId());
        TeamEntity whiteTeam = database.getTeamBase().get(extendedHeader.getWhiteTeamId());
        TeamEntity blackTeam = database.getTeamBase().get(extendedHeader.getBlackTeamId());

        // TODO: Instead of duplicating each CBH specific field, it's probably better to keep a reference
        // to the raw header data. The GameHeaderModel should probably just contain the most generic fields,
        // while ID's and internal CBH stuff like "last updated", "version" etc should not be there,
        // but an opaque blob/reference to the CBH/CBJ data should be kept instead.
        // For data that can be deduced from the game moves (like eco, final material, endgame etc), perhaps
        // the version of the game can be used to deduce if it needs to be recalculated?

        model.setField(DATABASE_ID, database);

        model.setWhite(whitePlayer.getFullName());
        model.setField(WHITE_ID, whitePlayer.getId());
        if (header.getWhiteElo() > 0) {
            model.setWhiteElo(header.getWhiteElo());
        }
        model.setField(WHITE_TEAM_ID, whiteTeam.getId());
        model.setWhiteTeam(whiteTeam.getTitle());
        model.setBlack(blackPlayer.getFullName());
        model.setField(BLACK_ID, blackPlayer.getId());
        if (header.getBlackElo() > 0) {
            model.setBlackElo(header.getBlackElo());
        }
        model.setField(BLACK_TEAM_ID, blackTeam.getId());
        model.setBlackTeam(blackTeam.getTitle());
        model.setResult(header.getResult());
        model.setDate(header.getPlayedDate());
        model.setEco(header.getEco());
        if (header.getRound() > 0) {
            model.setRound(header.getRound());
        }
        if (header.getSubRound() > 0) {
            model.setSubRound(header.getRound());
        }

        model.setEvent(tournament.getTitle());
        model.setField(EVENT_ID, tournament.getId());
        model.setEventDate(tournament.getDate());
        model.setEventSite(tournament.getPlace());
        if (tournament.getNation() != Nation.NONE) {
            model.setEventCountry(tournament.getNation().getIocCode());
        }
        if (tournament.getType() != TournamentType.NONE) {
            model.setEventType(tournament.getType().getName());
        }
        if (tournament.getTimeControl() != TournamentTimeControl.NORMAL) {
            model.setEventTimeControl(tournament.getTimeControl().getName());
        }
        if (tournament.getCategory() > 0) {
            model.setEventCategory(tournament.getCategory());
        }
        if (tournament.getRounds() > 0) {
            model.setEventRounds(tournament.getRounds());
        }

        model.setSourceTitle(source.getTitle());
        model.setSource(source.getPublisher());
        model.setSourceDate(source.getPublication());
        model.setField(SOURCE_ID, source.getId());
        model.setAnnotator(annotator.getName());
        model.setField(ANNOTATOR_ID, annotator.getId());

        model.setLineEvaluation(header.getLineEvaluation());

        return model;
    }

    public GameModel getGameModel(int gameId) throws ChessBaseException {
        GameHeader gameHeader = database.getHeaderBase().getGameHeader(gameId);
        ExtendedGameHeader extendedGameHeader = database.getExtendedHeaderBase().getExtendedGameHeader(gameId);

        GameHeaderModel headerModel = getHeaderModel(gameHeader, extendedGameHeader);

        GameMovesModel moves = database.getMovesBase().getMoves(gameHeader.getMovesOffset(), gameId);
        database.getAnnotationBase().getAnnotations(moves, gameHeader.getAnnotationOffset());

        return new GameModel(headerModel, moves);
    }

    public ExtendedGameHeader createExtendedGameHeader(GameModel model, int gameId, long movesOfs, long annotationOfs)
            throws ChessBaseInvalidDataException {
        GameHeaderModel header = model.header();

        TeamEntity whiteTeam, blackTeam;

        // If the GameModel contains ID's from a different database, we can't use them
        boolean sameDb = database.getDatabaseId().equals(header.getField(DATABASE_ID));

        // Lookup or create the entities that are referenced in the game header
        // If the id field is set, use that one. Otherwise use the string field.
        // If no entity exists with the same name, create a new one.
        try {
            whiteTeam = resolveEntity(sameDb ? (Integer) header.getField(WHITE_TEAM_ID) : 0,
                    new TeamEntity(defaultName(header.getWhiteTeam())), database.getTeamBase());
            blackTeam = resolveEntity(sameDb ? (Integer) header.getField(BLACK_TEAM_ID) : 0,
                    new TeamEntity(defaultName(header.getBlackTeam())), database.getTeamBase());
        } catch (IllegalArgumentException e) {
            throw new ChessBaseInvalidDataException("Failed to create ExtendedGameHeader entry due to invalid entity data reference", e);
        }  catch (RuntimeException e) {
            throw new IllegalArgumentException("Some argument were not set in the game header model", e);
        }

        ExtendedGameHeader.ExtendedGameHeaderBuilder builder = ExtendedGameHeader.builder();

        builder.id(gameId); // TODO: Is this the actual game id?
        builder.movesOffset(movesOfs);
        builder.annotationOffset(annotationOfs);

        return builder.build();
    }

    public GameHeader createGameHeader(GameModel model, int movesOfs, int annotationOfs) throws ChessBaseInvalidDataException {
        GameHeaderModel header = model.header();

        PlayerEntity white, black;
        TournamentEntity tournament;
        AnnotatorEntity annotator;
        SourceEntity source;

        // If the GameModel contains ID's from a different database, we can't use them
        boolean sameDb = database.getDatabaseId().equals(header.getField(DATABASE_ID));

        // Lookup or create the entities that are referenced in the game header
        // If the id field is set, use that one. Otherwise use the string field.
        // If no entity exists with the same name, create a new one.
        // TODO: Would be nice to create these entities in a transaction and wait with the commit
        try {
            white = resolveEntity(sameDb ? (Integer) header.getField(WHITE_ID) : 0,
                    PlayerEntity.fromFullName(defaultName(header.getWhite())), database.getPlayerBase());
            black = resolveEntity(sameDb ? (Integer) header.getField(BLACK_ID) : 0,
                    PlayerEntity.fromFullName(defaultName(header.getBlack())), database.getPlayerBase());
            tournament = resolveEntity(sameDb ? (Integer) header.getField(EVENT_ID) : 0,
                    new TournamentEntity(defaultName(header.getEvent()), Date.today()), database.getTournamentBase());
            annotator = resolveEntity(sameDb ? (Integer) header.getField(ANNOTATOR_ID) : 0,
                    new AnnotatorEntity(defaultName(header.getAnnotator())), database.getAnnotatorBase());
            source = resolveEntity(sameDb ? (Integer) header.getField(SOURCE_ID) : 0,
                    new SourceEntity(defaultName(header.getSourceTitle())), database.getSourceBase());
        } catch (IllegalArgumentException e) {
           throw new ChessBaseInvalidDataException("Failed to create GameHeader entry due to invalid entity data reference", e);
        }  catch (RuntimeException e) {
            throw new IllegalArgumentException("Some argument were not set in the game header model", e);
        }

        GameHeader.GameHeaderBuilder builder = GameHeader.builder();

        builder.game(true);
        builder.whitePlayerId(white.getId());
        builder.blackPlayerId(black.getId());
        builder.tournamentId(tournament.getId());
        builder.annotatorId(annotator.getId());
        builder.sourceId(source.getId());
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

        int moves = (model.moves().countPly(false) + 1) / 2;
        builder.noMoves(moves > 255 ? -1 : moves);

        NAG eval = header.getLineEvaluation();
        builder.lineEvaluation(eval == null ? NAG.NONE : eval);

        setInferredData(builder, model.moves());

        builder.annotationOffset(annotationOfs);
        builder.movesOffset(movesOfs);
        return builder.build();
    }

    private void setInferredData(GameHeader.GameHeaderBuilder builder, GameMovesModel model) {
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

    private @NonNull String defaultName(String name) {
        return name == null ? "" : name;
    }

    <T extends Entity & Comparable<T>> T resolveEntity(Integer id, T key, EntityBase<T> base) {
        T entity;
        if (id != null && id > 0) {
            entity = base.get(id);
            if (entity == null) {
                throw new IllegalArgumentException("No entity with id " + id + " in " + base.getClass().getSimpleName());
            }
        } else {
            entity = base.getAny(key);
            if (entity == null) {
                try {
                    entity = base.add(key);
                } catch (EntityStorageException e) {
                    // Shouldn't happen since we tried to lookup the player first
                    throw new RuntimeException("Internal error");
                }
            }
        }
        return entity;
    }
}
