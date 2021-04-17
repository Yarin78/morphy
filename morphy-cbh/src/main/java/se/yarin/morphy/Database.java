package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.GameModel;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.exceptions.MorphyException;
import se.yarin.morphy.games.*;
import se.yarin.morphy.text.TextModel;
import se.yarin.morphy.util.CBUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a ChessBase database.
 *
 * A ChessBase database consists of multiple files on disk, storing different types of data
 * such as Game Headers, encoded moves and annotations, Player index, Tournament index etc
 *
 * The {@link Database} class is a facade, exposing a simple API for
 * common database operations that underneath performs complex logic to update the database structures.
 *
 * More low-level functionality can be performed by invoking methods on the classes representing
 * the different indexes, such as {@link GameHeaderIndex}, {@link PlayerIndex} etc
 * But beware that invoking write operations on these may cause the database to end up in an inconsistent state.
 */
public class Database {
    private static final Logger log = LoggerFactory.getLogger(Database.class);

    // Not a complete list, but the files supported
    public static final List<String> MANDATORY_EXTENSIONS = List.of(".cbh", ".cbg", ".cba", ".cbp", ".cbt", ".cbc", ".cbs");
    public static final List<String> ADDITIONAL_EXTENSIONS = List.of(".cbtt", ".cbj", ".cbe", ".cbl", ".cbm");
    public static final List<String> SEARCH_BOOSTER_EXTENSIONS = List.of(".cbb", ".cbgi", ".cit", ".cib", ".cit2", ".cib2");

    public static final List<String> ALL_EXTENSIONS = Stream.concat(Stream.concat(
            Database.MANDATORY_EXTENSIONS.stream(), Database.ADDITIONAL_EXTENSIONS.stream()),
            Database.SEARCH_BOOSTER_EXTENSIONS.stream()) .collect(Collectors.toList());


    @NotNull private final GameHeaderIndex gameHeaderIndex;
    @NotNull private final ExtendedGameHeaderStorage extendedGameHeaderStorage;
    @NotNull private final MoveRepository moveRepository;
    @NotNull private final AnnotationRepository annotationRepository;
    @NotNull private final PlayerIndex playerIndex;
    @NotNull private final TournamentIndex tournamentIndex;
    @NotNull private final TournamentExtraStorage tournamentExtraStorage;
    @NotNull private final AnnotatorIndex annotatorIndex;
    @NotNull private final SourceIndex sourceIndex;
    @NotNull private final TeamIndex teamIndex;
    @NotNull private final GameTagIndex gameTagIndex;

    @NotNull private final GameLoader loader;

    @NotNull public GameHeaderIndex gameHeaderIndex() {
        return gameHeaderIndex;
    }

    @NotNull public ExtendedGameHeaderStorage extendedGameHeaderStorage() {
        return extendedGameHeaderStorage;
    }

    @NotNull public MoveRepository moveRepository() {
        return moveRepository;
    }

    @NotNull public AnnotationRepository annotationRepository() {
        return annotationRepository;
    }

    @NotNull public PlayerIndex playerIndex() {
        return playerIndex;
    }

    @NotNull public TournamentIndex tournamentIndex() {
        return tournamentIndex;
    }

    @NotNull public TournamentExtraStorage tournamentExtraStorage() {
        return tournamentExtraStorage;
    }

    @NotNull public AnnotatorIndex annotatorIndex() {
        return annotatorIndex;
    }

    @NotNull public SourceIndex sourceIndex() {
        return sourceIndex;
    }

    @NotNull public TeamIndex teamIndex() {
        return teamIndex;
    }

    @NotNull public GameTagIndex gameTagIndex() {
        return gameTagIndex;
    }

    @NotNull public GameLoader loader() { return loader; }

    /**
     * Creates a new in-memory ChessBase database.
     *
     * Important: Write operations to the database will not be persisted to disk.
     */
    public Database() {
        this(new GameHeaderIndex(), new ExtendedGameHeaderStorage(), new MoveRepository(), new AnnotationRepository(), new PlayerIndex(),
                new TournamentIndex(), new TournamentExtraStorage(), new AnnotatorIndex(), new SourceIndex(), new TeamIndex(), new GameTagIndex());
    }

    private Database(
            @NotNull GameHeaderIndex gameHeaderIndex,
            @NotNull ExtendedGameHeaderStorage extendedGameHeaderStorage,
            @NotNull MoveRepository moveRepository,
            @NotNull AnnotationRepository annotationRepository,
            @NotNull PlayerIndex playerIndex,
            @NotNull TournamentIndex tournamentIndex,
            @NotNull TournamentExtraStorage tournamentExtraStorage,
            @NotNull AnnotatorIndex annotatorIndex,
            @NotNull SourceIndex sourceIndex,
            @NotNull TeamIndex teamIndex,
            @NotNull GameTagIndex gameTagIndex) {
        this.gameHeaderIndex = gameHeaderIndex;
        this.extendedGameHeaderStorage = extendedGameHeaderStorage;
        this.moveRepository = moveRepository;
        this.annotationRepository = annotationRepository;
        this.playerIndex = playerIndex;
        this.tournamentIndex = tournamentIndex;
        this.tournamentExtraStorage = tournamentExtraStorage;
        this.annotatorIndex = annotatorIndex;
        this.sourceIndex = sourceIndex;
        this.teamIndex = teamIndex;
        this.gameTagIndex = gameTagIndex;

        this.loader = new GameLoader(this);
    }

    public static Database create(@NotNull File file) throws IOException {
        return create(file, false);
    }

    public static Database create(@NotNull File file, boolean overwrite) throws IOException {
        if (!CBUtil.extension(file).equals(".cbh")) {
            throw new IllegalArgumentException("The extension of the database file must be .cbh");
        }
        if (overwrite) {
            Database.delete(file);
        }

        ensureNoDatabaseExists(file);

        GameHeaderIndex gameHeaderIndex = GameHeaderIndex.create(file);
        MoveRepository moveRepository = MoveRepository.create(CBUtil.fileWithExtension(file, ".cbg"));
        AnnotationRepository annotationRepository = AnnotationRepository.create(CBUtil.fileWithExtension(file, ".cba"));
        PlayerIndex playerIndex = PlayerIndex.create(CBUtil.fileWithExtension(file, ".cbp"));
        TournamentIndex tournamentIndex = TournamentIndex.create(CBUtil.fileWithExtension(file, ".cbt"));
        AnnotatorIndex annotatorIndex = AnnotatorIndex.create(CBUtil.fileWithExtension(file, ".cbc"));
        SourceIndex sourceIndex = SourceIndex.create(CBUtil.fileWithExtension(file, ".cbs"));

        ExtendedGameHeaderStorage extendedGameHeaderStorage = ExtendedGameHeaderStorage.create(CBUtil.fileWithExtension(file, ".cbj"));
        TournamentExtraStorage tournamentExtraStorage = TournamentExtraStorage.create(CBUtil.fileWithExtension(file, ".cbtt"));
        TeamIndex teamIndex = TeamIndex.create(CBUtil.fileWithExtension(file, ".cbe"));
        GameTagIndex gameTagIndex = GameTagIndex.create(CBUtil.fileWithExtension(file, ".cbl"));

        return new Database(gameHeaderIndex, extendedGameHeaderStorage, moveRepository, annotationRepository,
                playerIndex, tournamentIndex, tournamentExtraStorage, annotatorIndex, sourceIndex, teamIndex, gameTagIndex);
    }

    public static Database open(@NotNull File file) throws IOException {
        return open(file, DatabaseMode.READ_WRITE);
    }

    /**
     * Opens a ChessBase database from disk.
     *
     * Make sure not to open the same database multiple times, as that could lead to an inconsistent database!
     * It's recommended that the caller keeps a lock on the database file itself.
     *
     * @param file the database file object
     * @param mode basic operations mode (typically read-only or read-write)
     * @return an instance of this class, representing the opened database
     * @throws IOException if an IO error occurred when opening the database; this may happen
     * if some mandatory files are missing
     */
    public static Database open(@NotNull File file, @NotNull DatabaseMode mode) throws IOException {
        if (!CBUtil.extension(file).equals(".cbh")) {
            throw new IllegalArgumentException("The extension of the database file must be .cbh");
        }

        if (mode == DatabaseMode.READ_WRITE) {
            // TODO: Make simple validation that all essential files exist and have size > 0
            // before attempting anything
            ExtendedGameHeaderStorage.upgrade(file);
            PlayerIndex.upgrade(CBUtil.fileWithExtension(file, ".cbp"));
            TournamentIndex.upgrade(CBUtil.fileWithExtension(file, ".cbt"));
            AnnotatorIndex.upgrade(CBUtil.fileWithExtension(file, ".cbc"));
            SourceIndex.upgrade(CBUtil.fileWithExtension(file, ".cbs"));
            TeamIndex.upgrade(CBUtil.fileWithExtension(file, ".cbe"));
            GameTagIndex.upgrade(CBUtil.fileWithExtension(file, ".cbl"));
            TournamentExtraStorage.upgrade(CBUtil.fileWithExtension(file, ".cbtt"));
            // TODO: MoveRepository and AnnotationRepository upgrades
        }

        // The mandatory files
        GameHeaderIndex gameHeaderIndex = GameHeaderIndex.open(file, mode);
        MoveRepository moveRepository = MoveRepository.open(CBUtil.fileWithExtension(file, ".cbg"), mode);
        AnnotationRepository annotationRepository = AnnotationRepository.open(CBUtil.fileWithExtension(file, ".cba"), mode);
        PlayerIndex playerIndex = PlayerIndex.open(CBUtil.fileWithExtension(file, ".cbp"), mode);
        TournamentIndex tournamentIndex = TournamentIndex.open(CBUtil.fileWithExtension(file, ".cbt"), mode);
        AnnotatorIndex annotatorIndex = AnnotatorIndex.open(CBUtil.fileWithExtension(file, ".cbc"), mode);
        SourceIndex sourceIndex = SourceIndex.open(CBUtil.fileWithExtension(file, ".cbs"), mode);

        // Optional files. Only in very early ChessBase databases are they missing.
        // If the database is opened in read-only mode, don't create them but instead provide empty versions
        File cbjFile = CBUtil.fileWithExtension(file, ".cbj");
        File cbttFile = CBUtil.fileWithExtension(file, ".cbtt");
        File cbeFile = CBUtil.fileWithExtension(file, ".cbe");
        File cblFile = CBUtil.fileWithExtension(file, ".cbl");

        ExtendedGameHeaderStorage extendedGameHeaderStorage = cbjFile.exists()
                ? ExtendedGameHeaderStorage.open(cbjFile, mode)
                : new ExtendedGameHeaderStorage();
        TournamentExtraStorage tournamentExtraStorage = cbttFile.exists()
                ? TournamentExtraStorage.open(cbttFile, mode)
                : new TournamentExtraStorage();
        TeamIndex teamIndex = cbeFile.exists() ? TeamIndex.open(cbeFile, mode) : new TeamIndex();
        GameTagIndex gameTagIndex = cblFile.exists() ? GameTagIndex.open(cblFile, mode) : new GameTagIndex();

        return new Database(gameHeaderIndex, extendedGameHeaderStorage, moveRepository, annotationRepository,
                playerIndex, tournamentIndex, tournamentExtraStorage, annotatorIndex, sourceIndex, teamIndex, gameTagIndex);
    }

    /**
     * Ensures that there are no traces of a database with the same name
     * @param file a file name of a database that is intended to be created
     * @throws IOException if there are some files in the same folder as file with the same name
     * (case insensitive, excluding extension)
     */
    private static void ensureNoDatabaseExists(@NotNull File file) throws IOException {
        String baseNameLower = CBUtil.baseName(file).toLowerCase(Locale.ROOT);
        File[] files = file.getParentFile().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).startsWith(baseNameLower));
        if (files.length > 0) {
            throw new IOException(String.format("Can't create a database %s because file %s exists",
                    file.getPath(), files[0].getPath()));
        }
    }

    /**
     * Deletes a database from disk by deleting all database related files and subdirectories
     * with the same basename as the given file.
     * @param file the name of the cbh file
     */
    public static void delete(@NotNull File file) throws IOException {
        file = file.getAbsoluteFile();
        String basename = file.getName().split("\\.")[0];
        File directory = file.getParentFile();
        List<File> toBeDeleted = new ArrayList<>();

        // Collect all files that should be deleted and verify that the process has access to delete them all
        // before carrying out the actual delete
        File[] files = directory.listFiles((dir, name) -> name.split("\\.")[0].equals(basename));
        assert files != null;
        for(File databaseFile : files) {
            if (databaseFile.isDirectory()) {
                File[] subFiles = databaseFile.listFiles();
                assert subFiles != null;
                for(File subFile : subFiles) {
                    if (subFile.isDirectory()) {
                        throw new IOException("Database deletion aborted; unexpected subdirectory: " + subFile);
                    }
                    toBeDeleted.add(subFile);
                }
            }
            if (!databaseFile.canWrite()) {
                throw new IOException("Database deletion aborted; file can't be deleted: " + databaseFile);
            }

            toBeDeleted.add(databaseFile);
        }

        for (File databaseFile : toBeDeleted) {
            if (!databaseFile.delete()) {
                throw new IOException("Database deletion aborted; Failed to delete " + databaseFile);
            }
        }
    }

    public void close() throws IOException {
        this.gameHeaderIndex.close();
        extendedGameHeaderStorage.close();
        moveRepository.close();
        annotationRepository.close();
        playerIndex.close();
        tournamentIndex.close();
        tournamentExtraStorage.close();
        annotatorIndex.close();
        sourceIndex.close();
        teamIndex.close();
        gameTagIndex.close();
    }

    /**
     * Gets a game model
     * @param game the game
     * @return a model of the game
     * @throws MorphyException if an internal error occurred when fetching the game
     * @throws IllegalArgumentException if the game is actually a text
     */
    public GameModel getGameModel(Game game) throws MorphyException {
        return loader.getGameModel(game);
    }

    /**
     * Gets a text model
     * @param game the game
     * @return a model of the text
     * @throws MorphyException if an internal error occurred when fetching the game
     * @throws IllegalArgumentException if the text is actually a game
     */
    public TextModel getTextModel(Game game) throws MorphyException {
        return loader.getTextModel(game);
    }

    /**
     * Gets a game from the database
     * @param gameId the id of the game to get
     * @return a model of the game
     * @throws IllegalArgumentException if no game with the specified ID exists
     */
    public Game getGame(int gameId) {
        // TODO: consolidate this, it's also in DatabaseTransaction
        if (gameId < 1) {
            throw new IllegalArgumentException("Invalid game id: " + gameId);
        }
        GameHeader header = gameHeaderIndex.getGameHeader(gameId);
        ExtendedGameHeader extendedGameHeader = gameId <= extendedGameHeaderStorage.count() ? extendedGameHeaderStorage.get(gameId) : ExtendedGameHeader.empty(header);
        return new Game(this, header, extendedGameHeader);
    }

    public int count() {
        return gameHeaderIndex.count();
    }

    public int addGame(GameModel game) {
        DatabaseTransaction txn = new DatabaseTransaction(this);
        int id = txn.addGame(game);
        txn.commit();
        return id;
    }

    public void replaceGame(int gameId, GameModel game) {
        DatabaseTransaction txn = new DatabaseTransaction(this);
        txn.replaceGame(gameId, game);
        txn.commit();
    }
}
