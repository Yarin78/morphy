package se.yarin.morphy.games;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;
import se.yarin.morphy.Database;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.exceptions.MorphyException;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.text.*;

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
    public static final String GAME_TAG_ID = "gameTagId";

    private final Database database;

    public GameLoader(Database database) {
        this.database = database;
    }

    /**
     * Creates a mutable model of the game header
     * @param game the game to get the header model for
     * @return a mutable game header model
     * @throws MorphyInvalidDataException if an entity couldn't be resolved
     */
    public GameHeaderModel getGameHeaderModel(@NotNull Game game) throws MorphyInvalidDataException {
        GameHeader header = game.header();
        ExtendedGameHeader extendedHeader = game.extendedHeader();

        GameHeaderModel model = new GameHeaderModel();

        if (header.guidingText()) {
            throw new IllegalArgumentException("Can't get game header model for a guiding text (id " + header.id() + ")");
        }

        Player whitePlayer = resolveEntity(database.playerIndex(), "white player", header.whitePlayerId(), game, false);
        Player blackPlayer = resolveEntity(database.playerIndex(), "black player", header.blackPlayerId(), game, false);
        Annotator annotator = resolveEntity(database.annotatorIndex(), "annotator", header.annotatorId(), game, false);
        Source source = resolveEntity(database.sourceIndex(), "source", header.sourceId(), game, false);
        Tournament tournament = resolveEntity(database.tournamentIndex(), "tournament", header.tournamentId(), game, false);
        Team whiteTeam = resolveEntity(database.teamIndex(), "white team", extendedHeader.whiteTeamId(), game, true);
        Team blackTeam = resolveEntity(database.teamIndex(), "black team", extendedHeader.blackTeamId(), game, true);
        GameTag gameTag = resolveEntity(database.gameTagIndex(), "game tag", extendedHeader.gameTagId(), game, true);

        assert whitePlayer != null;
        assert blackPlayer != null;
        assert annotator != null;
        assert source != null;
        assert tournament != null;

        // TODO: Instead of duplicating each CBH specific field, it's probably better to keep a reference
        // to the raw header data. The GameHeaderModel should probably just contain the most generic fields,
        // while ID's and internal CBH stuff like "last updated", "version" etc should not be there,
        // but an opaque blob/reference to the CBH/CBJ data should be kept instead.
        // For data that can be deduced from the game moves (like eco, final material, endgame etc), perhaps
        // the version of the game can be used to deduce if it needs to be recalculated?

        model.setField(DATABASE_ID, database);

        model.setWhite(whitePlayer.getFullName());
        model.setField(WHITE_ID, whitePlayer.id());
        if (header.whiteElo() > 0) {
            model.setWhiteElo(header.whiteElo());
        }
        if (whiteTeam != null) {
            model.setField(WHITE_TEAM_ID, whiteTeam.id());
            model.setWhiteTeam(whiteTeam.title());
        }
        model.setBlack(blackPlayer.getFullName());
        model.setField(BLACK_ID, blackPlayer.id());
        if (header.blackElo() > 0) {
            model.setBlackElo(header.blackElo());
        }
        if (blackTeam != null) {
            model.setField(BLACK_TEAM_ID, blackTeam.id());
            model.setBlackTeam(blackTeam.title());
        }
        model.setResult(header.result());
        model.setDate(header.playedDate());
        model.setEco(header.eco());
        if (header.round() > 0) {
            model.setRound(header.round());
        }
        if (header.subRound() > 0) {
            model.setSubRound(header.subRound());
        }

        model.setEvent(tournament.title());
        model.setField(EVENT_ID, tournament.id());
        model.setEventDate(tournament.date());
        model.setEventSite(tournament.place());
        if (tournament.nation() != Nation.NONE) {
            model.setEventCountry(tournament.nation().getIocCode());
        }
        if (tournament.type() != TournamentType.NONE) {
            model.setEventType(tournament.type().getName());
        }
        if (tournament.timeControl() != TournamentTimeControl.NORMAL) {
            model.setEventTimeControl(tournament.timeControl().getName());
        }
        if (tournament.category() > 0) {
            model.setEventCategory(tournament.category());
        }
        if (tournament.rounds() > 0) {
            model.setEventRounds(tournament.rounds());
        }

        model.setSourceTitle(source.title());
        model.setSource(source.publisher());
        model.setSourceDate(source.publication());
        model.setField(SOURCE_ID, source.id());
        model.setAnnotator(annotator.name());
        model.setField(ANNOTATOR_ID, annotator.id());
        if (gameTag != null) {
            model.setField(GAME_TAG_ID, gameTag.id());
        }

        model.setLineEvaluation(header.lineEvaluation());

        return model;
    }

    /**
     * Creates a mutable model of the game, header and moves
     * @param game the game to get the model for
     * @return a mutable game model
     * @throws MorphyInvalidDataException if the model couldn't be created due to broken references
     * If the move data is broken, a model is still returned with as many moves as could be decoded
     */
    public GameModel getGameModel(Game game) throws MorphyInvalidDataException {
        assert game.database() == this.database;
        GameHeaderModel headerModel = getGameHeaderModel(game);

        GameMovesModel moves = database.moveRepository().getMoves(game.getMovesOffset(), game.id());
        database.annotationRepository().getAnnotations(moves, game.getAnnotationOffset());

        return new GameModel(headerModel, moves);
    }

    /**
     * Creates a mutable model of the game, header and moves
     * @param gameId the id of the game to get the model for
     * @return a mutable game model
     * @throws MorphyInvalidDataException if the model couldn't be created due to broken references
     * If the move data is broken, a model is still returned with as many moves as could be decoded
     */
    public GameModel getGameModel(int gameId) throws MorphyInvalidDataException {
        return getGameModel(database.getGame(gameId));
    }

    public TextHeaderModel getTextHeaderModel(Game game) {
        GameHeader header = game.header();
        if (!header.guidingText()) {
            throw new IllegalArgumentException("Can't get text header model for a game (id " + header.id() + ")");
        }

        Annotator annotator = database.annotatorIndex().get(header.annotatorId());
        Source source = database.sourceIndex().get(header.sourceId());
        Tournament tournament = database.tournamentIndex().get(header.tournamentId());

        return ImmutableTextHeaderModel.builder()
            .round(header.round())
            .subRound(header.round())
            .tournament(tournament == null ? "" : tournament.title())
            .tournamentYear(tournament == null ? 0 : tournament.date().year())
            .source(source == null ? "" : source.publisher())
            .annotator(annotator == null ? "" : annotator.name())
            .build();
    }

    public TextModel getTextModel(Game game) throws MorphyException {
        assert game.database() == this.database;

        TextHeaderModel header = getTextHeaderModel(game);
        TextContentsModel contents = database.moveRepository().getText(game.getMovesOffset(), game.id());
        return ImmutableTextModel.builder().header(header).contents(contents).build();
    }

    public TextModel getTextModel(int gameId) throws MorphyException {
        return getTextModel(database.getGame(gameId));
    }

    private <T extends Entity & Comparable<T>> T resolveEntity(EntityIndex<T> index, String entityType, int id, Game game, boolean allowNone)
            throws MorphyInvalidDataException {
        if (id == -1 && allowNone) {
            return null;
        }
        T entity = index.get(id);
        if (entity == null) {
            throw new MorphyInvalidDataException(String.format("Invalid %s in game %d (id %d does not exist)", entityType, game.id(), id));
        }
        return entity;
    }
}
