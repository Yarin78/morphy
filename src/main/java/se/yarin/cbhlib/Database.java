package se.yarin.cbhlib;

import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.entities.Entity;
import se.yarin.cbhlib.entities.EntityStorageException;
import se.yarin.chess.*;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Represents a ChessBase database
 */
public final class Database {
    private static final Logger log = LoggerFactory.getLogger(Database.class);

    public static final String WHITE_ID = "whiteId";
    public static final String BLACK_ID = "blackId";
    public static final String EVENT_ID = "eventId";
    public static final String ANNOTATOR_ID = "annotatorId";
    public static final String SOURCE_ID = "sourceId";

    @Getter @NonNull private GameHeaderBase headerBase;
    @NonNull private MovesBase movesBase;
    @NonNull private AnnotationBase annotationBase;
    @Getter @NonNull private PlayerBase playerBase;
    @Getter @NonNull private TournamentBase tournamentBase;
    @Getter @NonNull private AnnotatorBase annotatorBase;
    @Getter @NonNull private SourceBase sourceBase;

    /**
     * Creates a new in-memory ChessBase database
     */
    public Database() {
        this(new GameHeaderBase(), new MovesBase(), new AnnotationBase(), new PlayerBase(),
                new TournamentBase(), new AnnotatorBase(), new SourceBase());
    }

    private Database(
            @NonNull GameHeaderBase headerBase,
            @NonNull MovesBase movesBase,
            @NonNull AnnotationBase annotationBase,
            @NonNull PlayerBase playerBase,
            @NonNull TournamentBase tournamentBase,
            @NonNull AnnotatorBase annotatorBase,
            @NonNull SourceBase sourceBase) {
        this.headerBase = headerBase;
        this.movesBase = movesBase;
        this.annotationBase = annotationBase;
        this.playerBase = playerBase;
        this.tournamentBase = tournamentBase;
        this.annotatorBase = annotatorBase;
        this.sourceBase = sourceBase;
    }

    private static void validateDatabaseName(File file) {
        String path = file.getPath();
        if (!path.toLowerCase().endsWith(".cbh")) {
            throw new IllegalArgumentException("The extension of the database file must be .cbh");
        }
    }

    public static Database open(@NonNull File file) throws IOException {
        validateDatabaseName(file);
        String base = file.getPath().substring(0, file.getPath().length() - 4);

        GameHeaderBase cbh = GameHeaderBase.open(file);
        MovesBase cbg = MovesBase.open(new File(base + ".cbg"));
        AnnotationBase cba = AnnotationBase.open(new File(base + ".cba"));
        PlayerBase cbp = PlayerBase.open(new File(base + ".cbp"));
        TournamentBase cbt = TournamentBase.open(new File(base + ".cbt"));
        AnnotatorBase cbc = AnnotatorBase.open(new File(base + ".cbc"));
        SourceBase cbs = SourceBase.open(new File(base + ".cbs"));

        return new Database(cbh, cbg, cba, cbp, cbt, cbc, cbs);
    }

    public static Database openInMemory(@NonNull File file) throws IOException {
        validateDatabaseName(file);
        String base = file.getPath().substring(0, file.getPath().length() - 4);

        GameHeaderBase cbh = GameHeaderBase.openInMemory(file);
        MovesBase cbg = MovesBase.openInMemory(new File(base + ".cbg"));
        AnnotationBase cba = AnnotationBase.openInMemory(new File(base + ".cba"));
        PlayerBase cbp = PlayerBase.openInMemory(new File(base + ".cbp"));
        TournamentBase cbt = TournamentBase.openInMemory(new File(base + ".cbt"));
        AnnotatorBase cbc = AnnotatorBase.openInMemory(new File(base + ".cbc"));
        SourceBase cbs = SourceBase.openInMemory(new File(base + ".cbs"));

        return new Database(cbh, cbg, cba, cbp, cbt, cbc, cbs);
    }

    public static Database create(@NonNull File file) throws IOException {
        validateDatabaseName(file);
        String base = file.getPath().substring(0, file.getPath().length() - 4);

        GameHeaderBase cbh = GameHeaderBase.create(file);
        MovesBase cbg = MovesBase.create(new File(base + ".cbg"));
        AnnotationBase cba = AnnotationBase.create(new File(base + ".cba"));
        PlayerBase cbp = PlayerBase.create(new File(base + ".cbp"));
        TournamentBase cbt = TournamentBase.create(new File(base + ".cbt"));
        AnnotatorBase cbc = AnnotatorBase.create(new File(base + ".cbc"));
        SourceBase cbs = SourceBase.create(new File(base + ".cbs"));

        return new Database(cbh, cbg, cba, cbp, cbt, cbc, cbs);
    }

    public void close() throws IOException {
        headerBase.close();
        movesBase.close();
        annotationBase.close();
        playerBase.close();
        tournamentBase.close();
        annotatorBase.close();
        sourceBase.close();
    }

    private GameHeaderModel getHeaderModel(GameHeader header) throws IOException {
        GameHeaderModel model = new GameHeaderModel();

        PlayerEntity whitePlayer = playerBase.get(header.getWhitePlayerId());
        PlayerEntity blackPlayer = playerBase.get(header.getBlackPlayerId());
        AnnotatorEntity annotator = annotatorBase.get(header.getAnnotatorId());
        SourceEntity source = sourceBase.get(header.getSourceId());
        TournamentEntity tournament = tournamentBase.get(header.getTournamentId());

        model.setField("white", whitePlayer.getFullName());
        model.setField("whiteId", whitePlayer.getId());
        if (header.getWhiteElo() > 0) {
            model.setField("whiteElo", header.getWhiteElo());
        }
        // TODO: whiteTeam
        model.setField("black", blackPlayer.getFullName());
        model.setField("blackId", blackPlayer.getId());
        if (header.getBlackElo() > 0) {
            model.setField("blackElo", header.getBlackElo());
        }
        // TODO: blackTeam
        model.setField("result", header.getResult());
        model.setField("date", header.getPlayedDate());
        model.setField("eco", header.getEco());
        if (header.getRound() > 0) {
            model.setField("round", header.getRound());
        }
        if (header.getSubRound() > 0) {
            model.setField("subRound", header.getRound());
        }

        model.setField("event", tournament.getTitle());
        model.setField("eventId", tournament.getId());
        model.setField("eventDate", tournament.getDate());
        model.setField("eventSite", tournament.getPlace());
        if (tournament.getNation() != Nation.NONE) {
            model.setField("eventCountry", tournament.getNation().getName());
        }

        model.setField("source", source.getTitle());
        model.setField("sourceId", source.getId());
        model.setField("annotator", annotator.getName());
        model.setField("annotatorId", annotator.getId());

        return model;
    }


    public GameModel getGameModel(int gameId) throws IOException, ChessBaseException {
        GameHeader gameHeader = headerBase.getGameHeader(gameId);

        GameHeaderModel headerModel = getHeaderModel(gameHeader);

        GameMovesModel moves = movesBase.getMoves(gameHeader.getMovesOffset());
        annotationBase.getAnnotations(moves, gameHeader.getAnnotationOffset());

        GameModel model = new GameModel(headerModel, moves);

        return model;
    }

    private @NonNull String defaultName(String name) {
        return name == null ? "" : name;
    }

    private GameHeader createGameHeader(GameModel model, int movesOfs, int annotationOfs)
            throws IOException {
        GameHeaderModel header = model.header();

        PlayerEntity white, black;
        TournamentEntity tournament;
        AnnotatorEntity annotator;
        SourceEntity source;

        // Lookup or create the entities that are referenced in the game header
        // If the id field is set, use that one. Otherwise use the string field.
        // If no entity exists with the same name, create a new one.
        // TODO: Would be nice to create these entities in a transaction and wait with the commit
        try {
            white = resolveEntity((Integer) header.getField(WHITE_ID),
                    PlayerEntity.fromFullName(defaultName(header.getWhite())), playerBase);
            black = resolveEntity((Integer) header.getField(BLACK_ID),
                    PlayerEntity.fromFullName(defaultName(header.getBlack())), playerBase);
            tournament = resolveEntity((Integer) header.getField(EVENT_ID),
                    new TournamentEntity(defaultName(header.getEvent())), tournamentBase);
            annotator = resolveEntity((Integer) header.getField(ANNOTATOR_ID),
                    new AnnotatorEntity(defaultName(header.getAnnotator())), annotatorBase);
            source = resolveEntity((Integer) header.getField(SOURCE_ID),
                    new SourceEntity(defaultName(header.getSource())), sourceBase);
        } catch (RuntimeException e) {
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

        // TODO: Make it possible to set these fields
        // TODO: builder.lineEvaluation(??);
        builder.lineEvaluation(LineEvaluation.NO_EVALUATION);
        // TODO: builder.medals(??);
        builder.medals(EnumSet.noneOf(Medal.class));
        // TODO: builder.flags(??);
        builder.flags(EnumSet.noneOf(GameHeaderFlags.class));
        // TODO: builder.magnitudes

        builder.annotationOffset(annotationOfs);
        builder.movesOffset(movesOfs);
        return builder.build();
    }

    /**
     * Adds a new game to the database
     * @param model the model of the game to add
     * @return the game header of the saved game
     * @throws IOException if the game couldn't be stored due to an IO error
     */
    public GameHeader addGame(@NonNull GameModel model) throws IOException {
        int gameId = headerBase.getNextGameId();

        int annotationOfs = annotationBase.putAnnotations(gameId, 0, model.moves());
        int movesOfs = movesBase.putMoves(0, model.moves());

        GameHeader gameHeader = createGameHeader(model, movesOfs, annotationOfs);

        gameHeader = headerBase.add(gameHeader);
        assert gameHeader.getId() == gameId;

        updateEntityStats(null, gameHeader);

        return gameHeader;
    }

    /**
     * Replaces a game in the database
     * @param gameId the id of the game to replace
     * @param model the model of the game to replace
     * @return the game header of the saved game
     * @throws IOException if the game couldn't be stored due to an IO error
     */
    public GameHeader replaceGame(int gameId, @NonNull GameModel model) throws IOException {
        GameHeader oldGameHeader = headerBase.getGameHeader(gameId);
        if (oldGameHeader == null) {
            throw new IllegalArgumentException("There is no game with game id " + gameId);
        }

        // Ensure there is enough room to fit the game data
        // If not, insert bytes and update all game headers
        int insertedBytes = movesBase.preparePutBlob(oldGameHeader.getMovesOffset(), model.moves());
        if (insertedBytes > 0) {
            headerBase.adjustMovesOffset(gameId + 1, oldGameHeader.getMovesOffset(), insertedBytes);
        }

        // TODO: Implement this for annotations as well (although it's not necessary for CB13 integrity checker to happy)
        // This is a bit trickier since if there are no annotations for the game,
        // it doesn't know what offset to put it at (scan to next game having annotations?)

        int annotationOfs = annotationBase.putAnnotations(gameId, oldGameHeader.getAnnotationOffset(), model.moves());
        int oldMovesOffset = oldGameHeader.getMovesOffset();
        int movesOfs = movesBase.putMoves(oldMovesOffset, model.moves());
        assert movesOfs == oldMovesOffset; // Since we inserted space above, we should get the same offset

        GameHeader gameHeader = createGameHeader(model, movesOfs, annotationOfs);

        gameHeader = headerBase.update(gameId, gameHeader);
        updateEntityStats(oldGameHeader, gameHeader);

        return gameHeader;
    }

    private class DeltaMap<T extends Entity & Comparable<T>> {
        private Map<Integer, Integer> map = new HashMap<>();
        private EntityBase<T> base;
        private BiPredicate<GameHeader, Integer> predicate;
        private GameHeader newGame;

        public DeltaMap(EntityBase<T> base, GameHeader newGame, BiPredicate<GameHeader, Integer> predicate) {
            this.base = base;
            this.newGame = newGame;
            this.predicate = predicate;
        }

        private void update(int id, int diff) {
            if (map.containsKey(id)) {
                map.put(id, map.get(id) + diff);
            } else {
                map.put(id, diff);
            }
        }

        // TODO: Replace this with a proper search implementation in the EntityBase
        private int findFirstGame(int entityId) {
            for (GameHeader gameHeader : headerBase) {
                // When checking the game we're actually updating, don't pick the one from the db
                GameHeader actual = gameHeader.getId() == newGame.getId() ? newGame : gameHeader;
                if (predicate.test(actual, entityId)) {
                    return gameHeader.getId();
                }
            }
            return 0;
        }

        private void applyChanges() throws IOException, EntityStorageException {
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

    private void updateEntityStats(GameHeader oldGame, @NonNull GameHeader newGame)
            throws IOException {
        assert oldGame == null || oldGame.getId() != 0;
        assert newGame.getId() != 0;

        DeltaMap<PlayerEntity> playerDelta = new DeltaMap<>(playerBase, newGame,
                (header, entityId) -> header.getWhitePlayerId() == entityId || header.getBlackPlayerId() == entityId);
        DeltaMap<TournamentEntity> tournamentDelta = new DeltaMap<>(tournamentBase, newGame,
                (header, entityId) -> header.getTournamentId() == entityId);
        DeltaMap<AnnotatorEntity> annotatorDelta = new DeltaMap<>(annotatorBase, newGame,
                (header, entityId) -> header.getAnnotatorId() == entityId);
        DeltaMap<SourceEntity> sourceDelta = new DeltaMap<>(sourceBase, newGame,
                (header, entityId) -> header.getSourceId() == entityId);

        if (oldGame != null) {
            playerDelta.update(oldGame.getWhitePlayerId(), -1);
            playerDelta.update(oldGame.getBlackPlayerId(), -1);
            tournamentDelta.update(oldGame.getTournamentId(), -1);
            annotatorDelta.update(oldGame.getAnnotatorId(), -1);
            sourceDelta.update(oldGame.getSourceId(), -1);
        }
        playerDelta.update(newGame.getWhitePlayerId(), 1);
        playerDelta.update(newGame.getBlackPlayerId(), 1);
        tournamentDelta.update(newGame.getTournamentId(), 1);
        annotatorDelta.update(newGame.getAnnotatorId(), 1);
        sourceDelta.update(newGame.getSourceId(), 1);

        try {
            playerDelta.applyChanges();
            tournamentDelta.applyChanges();
            annotatorDelta.applyChanges();
            sourceDelta.applyChanges();
        } catch (EntityStorageException e) {
            throw new IOException("Entity storage is in an inconsistent state. Please run repair.", e);
        }
    }

    private <T extends Entity & Comparable<T>> T resolveEntity(Integer id, T key, EntityBase<T> base)
            throws IOException {
        T entity;
        if (id != null && id > 0) {
            entity = base.get(id);
            if (entity == null) {
                throw new IllegalArgumentException("No entity with id " + id);
            }
        } else {
            entity = base.get(key);
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
