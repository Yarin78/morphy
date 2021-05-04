package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.GameModel;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.exceptions.MorphyException;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.games.*;
import se.yarin.morphy.search.GameIteratorFilter;
import se.yarin.morphy.text.TextModel;
import se.yarin.morphy.util.CBUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
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
public class Database implements EntityRetriever {
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

    @NotNull private final GameAdapter gameAdapter;
    @NotNull private final DatabaseContext context;

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

    @NotNull public GameAdapter gameAdapter() { return gameAdapter; }

    @NotNull public DatabaseContext context() {
        return context;
    }

    /**
     * Creates a new in-memory ChessBase database.
     *
     * Important: Write operations to the database will not be persisted to disk.
     */
    public Database() {
        this(null);
    }

    public Database(@Nullable DatabaseConfig config) {
        this.context = new DatabaseContext(config);

        this.gameHeaderIndex = new GameHeaderIndex(this.context);
        this.extendedGameHeaderStorage = new ExtendedGameHeaderStorage(this.context);
        this.moveRepository = new MoveRepository(this.context);
        this.annotationRepository = new AnnotationRepository(this.context);
        this.playerIndex = new PlayerIndex(this.context);
        this.tournamentIndex = new TournamentIndex(this.context);
        this.tournamentExtraStorage = new TournamentExtraStorage(this.context);
        this.annotatorIndex = new AnnotatorIndex(this.context);
        this.sourceIndex = new SourceIndex(this.context);
        this.teamIndex = new TeamIndex(this.context);
        this.gameTagIndex = new GameTagIndex(this.context);

        this.gameAdapter = new GameAdapter();
    }

    private Database(
            @NotNull DatabaseContext context,
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

        Set<DatabaseContext> contexts = new HashSet<>(Arrays.asList(context, gameHeaderIndex.context(), extendedGameHeaderStorage.context(), moveRepository.context(),
                annotationRepository.context(), playerIndex.context(), tournamentIndex.context(), tournamentExtraStorage.context(),
                annotatorIndex.context(), sourceIndex.context(), teamIndex.context(), gameTagIndex.context()));
        if (contexts.size() > 1) {
            throw new IllegalArgumentException("All indexes in a Database must share the same context");
        }

        this.context = context;
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

        this.gameAdapter = new GameAdapter();
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

        DatabaseContext context = new DatabaseContext();

        GameHeaderIndex gameHeaderIndex = GameHeaderIndex.create(file, context);
        MoveRepository moveRepository = MoveRepository.create(CBUtil.fileWithExtension(file, ".cbg"), context);
        AnnotationRepository annotationRepository = AnnotationRepository.create(CBUtil.fileWithExtension(file, ".cba"), context);
        PlayerIndex playerIndex = PlayerIndex.create(CBUtil.fileWithExtension(file, ".cbp"), context);
        TournamentIndex tournamentIndex = TournamentIndex.create(CBUtil.fileWithExtension(file, ".cbt"), context);
        AnnotatorIndex annotatorIndex = AnnotatorIndex.create(CBUtil.fileWithExtension(file, ".cbc"), context);
        SourceIndex sourceIndex = SourceIndex.create(CBUtil.fileWithExtension(file, ".cbs"), context);

        ExtendedGameHeaderStorage extendedGameHeaderStorage = ExtendedGameHeaderStorage.create(CBUtil.fileWithExtension(file, ".cbj"), context);
        TournamentExtraStorage tournamentExtraStorage = TournamentExtraStorage.create(CBUtil.fileWithExtension(file, ".cbtt"), context);
        TeamIndex teamIndex = TeamIndex.create(CBUtil.fileWithExtension(file, ".cbe"), context);
        GameTagIndex gameTagIndex = GameTagIndex.create(CBUtil.fileWithExtension(file, ".cbl"), context);

        return new Database(context, gameHeaderIndex, extendedGameHeaderStorage, moveRepository, annotationRepository,
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
            // before attempting anything, and that cbj file contains correct number of headers
            // Add context?
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

        DatabaseContext context = new DatabaseContext();

        // The mandatory files
        GameHeaderIndex gameHeaderIndex = GameHeaderIndex.open(file, mode, context);
        MoveRepository moveRepository = MoveRepository.open(CBUtil.fileWithExtension(file, ".cbg"), mode, context);
        AnnotationRepository annotationRepository = AnnotationRepository.open(CBUtil.fileWithExtension(file, ".cba"), mode, context);
        PlayerIndex playerIndex = PlayerIndex.open(CBUtil.fileWithExtension(file, ".cbp"), mode, context);
        TournamentIndex tournamentIndex = TournamentIndex.open(CBUtil.fileWithExtension(file, ".cbt"), mode, context);
        AnnotatorIndex annotatorIndex = AnnotatorIndex.open(CBUtil.fileWithExtension(file, ".cbc"), mode, context);
        SourceIndex sourceIndex = SourceIndex.open(CBUtil.fileWithExtension(file, ".cbs"), mode, context);

        // Optional files. Only in very early ChessBase databases are they missing.
        // If the database is opened in read-only mode, don't create them but instead provide empty versions
        File cbjFile = CBUtil.fileWithExtension(file, ".cbj");
        File cbttFile = CBUtil.fileWithExtension(file, ".cbtt");
        File cbeFile = CBUtil.fileWithExtension(file, ".cbe");
        File cblFile = CBUtil.fileWithExtension(file, ".cbl");

        ExtendedGameHeaderStorage extendedGameHeaderStorage = cbjFile.exists()
                ? ExtendedGameHeaderStorage.open(cbjFile, mode, context)
                : new ExtendedGameHeaderStorage(context);
        TournamentExtraStorage tournamentExtraStorage = cbttFile.exists()
                ? TournamentExtraStorage.open(cbttFile, mode, context)
                : new TournamentExtraStorage(context);
        TeamIndex teamIndex = cbeFile.exists() ? TeamIndex.open(cbeFile, mode, context) : new TeamIndex(context);
        GameTagIndex gameTagIndex = cblFile.exists() ? GameTagIndex.open(cblFile, mode, context) : new GameTagIndex(context);

        // TODO: Move this to constructor, add mode to DatabaseContext
        int headerCount = gameHeaderIndex.count(), extHeaderCount = extendedGameHeaderStorage.count();
        boolean mismatch = switch (mode) {
            case READ_WRITE -> headerCount != extHeaderCount;
            case READ_ONLY, IN_MEMORY -> headerCount != extHeaderCount && extHeaderCount > 0;
            case READ_REPAIR -> false;
        };
        if (mismatch) {
            throw new MorphyInvalidDataException(String.format("The number of headers mismatch in the cbh and cbj files mismatch (%d != %d)",
                    headerCount, extHeaderCount));
        }

        return new Database(context, gameHeaderIndex, extendedGameHeaderStorage, moveRepository, annotationRepository,
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
     * Gets a game from the database
     * @param gameId the id of the game to get
     * @return the game
     * @throws IllegalArgumentException if no game with the specified ID exists
     */
    public @NotNull Game getGame(int gameId) {
        try (var txn = new DatabaseReadTransaction(this)) {
            return txn.getGame(gameId);
        }
    }

    /**
     * Gets all games in the database
     * If the database is large, use the iterable options in DatabaseReadTransaction instead.
     * @return a list of all games in the database, ordered by id
     */
    public @NotNull List<Game> getGames() {
        return getGames(null);
    }

    /**
     * Gets all games in the database matching a filter by doing a sequence scan.
     * Consider using the GameSearcher instead for more performant game searches.
     * @return a list of all games in the database matching the filter, ordered by id
     */
    public @NotNull List<Game> getGames(@Nullable GameIteratorFilter filter) {
        try (var txn = new DatabaseReadTransaction(this)) {
            return txn.stream(1, filter).collect(Collectors.toList());
        }
    }

    /**
     * Gets a game model
     * @param gameId the id of the game to get
     * @return a model of the game
     * @throws MorphyException if an internal error occurred when fetching the game
     * @throws IllegalArgumentException if the game is actually a text
     */
    public @NotNull GameModel getGameModel(int gameId) throws MorphyException {
        return getGame(gameId).getModel();
    }

    /**
     * Gets a text model
     * @param gameId the id of the text to get
     * @return a model of the text
     * @throws MorphyException if an internal error occurred when fetching the game
     * @throws IllegalArgumentException if the text is actually a game
     */
    public @NotNull TextModel getTextModel(int gameId) throws MorphyException {
        return getGame(gameId).getTextModel();
    }

    public int count() {
        return gameHeaderIndex.count();
    }

    public int addGame(@NotNull GameModel game) {
        int id;
        try (var txn = new DatabaseWriteTransaction(this)) {
            id = txn.addGame(game).id();
            txn.commit();
        }
        return id;
    }

    public void replaceGame(int gameId, @NotNull GameModel game) {
        try (var txn = new DatabaseWriteTransaction(this)) {
            txn.replaceGame(gameId, game);
            txn.commit();
        }
    }

    @Override
    public @NotNull Player getPlayer(int id) {
        return playerIndex.get(id);
    }

    @Override
    public @NotNull Annotator getAnnotator(int id) {
        return annotatorIndex.get(id);
    }

    @Override
    public @NotNull Source getSource(int id) {
        return sourceIndex.get(id);
    }

    @Override
    public @NotNull Tournament getTournament(int id) {
        return tournamentIndex.get(id);
    }

    @Override
    public @NotNull TournamentExtra getTournamentExtra(int id) {
        return tournamentExtraStorage.get(id);
    }

    @Override
    public @NotNull Team getTeam(int id) {
        return teamIndex.get(id);
    }

    @Override
    public @NotNull GameTag getGameTag(int id) {
        return gameTagIndex.get(id);
    }
}
