package se.yarin.cbhlib;

import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.annotations.AnnotationBase;
import se.yarin.cbhlib.entities.*;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.cbhlib.exceptions.ChessBaseInvalidDataException;
import se.yarin.cbhlib.games.ExtendedGameHeaderBase;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.GameHeaderBase;
import se.yarin.cbhlib.games.GameLoader;
import se.yarin.cbhlib.games.search.GameSearcher;
import se.yarin.cbhlib.games.search.SearchFilter;
import se.yarin.cbhlib.moves.MovesBase;
import se.yarin.chess.GameModel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Represents a ChessBase database.
 *
 * A ChessBase database consists of multiple files on disk, storing different types of data
 * such as Game Headers, encoded moves and annotations, Player database, Tournament database etc
 *
 * The {@link se.yarin.cbhlib.Database} class is a facade, exposing a simple API for
 * common database operations that underneath performs complex logic to update the database structures.
 *
 * More low-level functionality can be performed by invoking methods on the classes representing
 * the different files, such as {@link GameHeaderBase}, {@link PlayerBase} etc
 * But beware that invoking write operations on these may cause the database to end up in an inconsistent state.
 */

public final class Database implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(Database.class);

    private final DatabaseUpdater updater;
    private final GameLoader loader;

    // The database id is not persisted but unique each time the database is reloaded
    // It's used to know if an entity id in a GameModel can be used or not in the context of a database,
    // when games are copied between databases.
    @Getter @NonNull private final String databaseId;

    @Getter @NonNull private final GameHeaderBase headerBase;
    @Getter @NonNull private final ExtendedGameHeaderBase extendedHeaderBase;
    @Getter @NonNull private final MovesBase movesBase;
    @Getter @NonNull private final AnnotationBase annotationBase;
    @Getter @NonNull private final PlayerBase playerBase;
    @Getter @NonNull private final TournamentBase tournamentBase;
    @Getter @NonNull private final AnnotatorBase annotatorBase;
    @Getter @NonNull private final SourceBase sourceBase;
    @Getter @NonNull private final TeamBase teamBase;


    /**
     * Creates a new in-memory ChessBase database.
     *
     * Important: Write operations to the database will not be persisted to disk.
     */
    public Database() {
        this(new GameHeaderBase(), new ExtendedGameHeaderBase(), new MovesBase(), new AnnotationBase(), new PlayerBase(),
                new TournamentBase(), new AnnotatorBase(), new SourceBase(), new TeamBase());
    }

    private Database(
            @NonNull GameHeaderBase headerBase,
            @NonNull ExtendedGameHeaderBase extendedHeaderBase,
            @NonNull MovesBase movesBase,
            @NonNull AnnotationBase annotationBase,
            @NonNull PlayerBase playerBase,
            @NonNull TournamentBase tournamentBase,
            @NonNull AnnotatorBase annotatorBase,
            @NonNull SourceBase sourceBase,
            @NonNull TeamBase teamBase) {
        this.headerBase = headerBase;
        this.extendedHeaderBase = extendedHeaderBase;
        this.movesBase = movesBase;
        this.annotationBase = annotationBase;
        this.playerBase = playerBase;
        this.tournamentBase = tournamentBase;
        this.annotatorBase = annotatorBase;
        this.sourceBase = sourceBase;
        this.teamBase = teamBase;

        this.loader = new GameLoader(this);
        this.updater = new DatabaseUpdater(this, loader);
        this.databaseId = UUID.randomUUID().toString();
    }

    private static void validateDatabaseName(File file) {
        String path = file.getPath();
        if (!path.toLowerCase().endsWith(".cbh")) {
            throw new IllegalArgumentException("The extension of the database file must be .cbh");
        }
    }

    /**
     * Opens a ChessBase database from disk.
     *
     * Make sure not to open the same database multiple times, as that could lead to an inconsistent database!
     * It's recommended that the caller keeps a lock on the database file itself.
     *
     * @param file the database file object
     * @return an instance of this class, representing the opened database
     * @throws IOException if an IO error occurred when opening the database
     */
    public static Database open(@NonNull File file) throws IOException {
        validateDatabaseName(file);
        String base = file.getPath().substring(0, file.getPath().length() - 4);

        GameHeaderBase cbh = GameHeaderBase.open(file);
        File cbjFile = new File(base + ".cbj");
        ExtendedGameHeaderBase cbj = cbjFile.exists()
                ? ExtendedGameHeaderBase.open(cbjFile)
                : ExtendedGameHeaderBase.create(cbjFile);

        MovesBase cbg = MovesBase.open(new File(base + ".cbg"));
        AnnotationBase cba = AnnotationBase.open(new File(base + ".cba"));
        PlayerBase cbp = PlayerBase.open(new File(base + ".cbp"));
        TournamentBase cbt = TournamentBase.open(new File(base + ".cbt"));
        AnnotatorBase cbc = AnnotatorBase.open(new File(base + ".cbc"));
        SourceBase cbs = SourceBase.open(new File(base + ".cbs"));
        File cbeFile = new File(base + ".cbe");
        TeamBase cbe = cbeFile.exists() ? TeamBase.open(cbeFile) : TeamBase.create(cbeFile);

        return new Database(cbh, cbj, cbg, cba, cbp, cbt, cbc, cbs, cbe);
    }

    /**
     * Opens a ChessBase database from disk and loads the entire contents into memory.
     * Requires a lot of memory if the database is large, but allows for very fast read operations.
     * Important: write operations will not be persisted to disk!
     *
     * @param file the database file object
     * @return an instance of this class, representing the opened database
     * @throws IOException if an IO error occurred when opening the database
     */
    public static Database openInMemory(@NonNull File file) throws IOException {
        validateDatabaseName(file);
        String base = file.getPath().substring(0, file.getPath().length() - 4);

        GameHeaderBase cbh = GameHeaderBase.openInMemory(file);
        ExtendedGameHeaderBase cbj = ExtendedGameHeaderBase.openInMemory(new File(base + ".cbj"));
        MovesBase cbg = MovesBase.openInMemory(new File(base + ".cbg"));
        AnnotationBase cba = AnnotationBase.openInMemory(new File(base + ".cba"));
        PlayerBase cbp = PlayerBase.openInMemory(new File(base + ".cbp"));
        TournamentBase cbt = TournamentBase.openInMemory(new File(base + ".cbt"));
        AnnotatorBase cbc = AnnotatorBase.openInMemory(new File(base + ".cbc"));
        SourceBase cbs = SourceBase.openInMemory(new File(base + ".cbs"));
        TeamBase cbe = TeamBase.openInMemory(new File(base + ".cbe"));

        return new Database(cbh, cbj, cbg, cba, cbp, cbt, cbc, cbs, cbe);
    }

    /**
     * Creates a new ChessBase database on disk.
     * @param file the database file object
     * @return an instance of this class, representing the created database
     * @throws IOException if an IO error occurred when trying to create the database
     */
    public static Database create(@NonNull File file) throws IOException {
        return create(file, false);
    }

    /**
     * Creates a new ChessBase database on disk.
     * @param file the database file object
     * @param createOnClose if true, some of the bases are in-memory until being flushed when the database closes
     * @return an instance of this class, representing the created database
     * @throws IOException if an IO error occurred when trying to create the database
     */
    public static Database create(@NonNull File file, boolean createOnClose) throws IOException {
        validateDatabaseName(file);
        String base = file.getPath().substring(0, file.getPath().length() - 4);

        GameHeaderBase cbh = GameHeaderBase.create(file);
        ExtendedGameHeaderBase cbj = ExtendedGameHeaderBase.create(new File(base + ".cbj"));
        MovesBase cbg = MovesBase.create(new File(base + ".cbg"));
        AnnotationBase cba = AnnotationBase.create(new File(base + ".cba"));

        PlayerBase cbp = PlayerBase.create(new File(base + ".cbp"), createOnClose);
        TournamentBase cbt = TournamentBase.create(new File(base + ".cbt"), createOnClose);
        AnnotatorBase cbc = AnnotatorBase.create(new File(base + ".cbc"), createOnClose);
        SourceBase cbs = SourceBase.create(new File(base + ".cbs"), createOnClose);
        TeamBase cbe = TeamBase.create(new File(base + ".cbe"), createOnClose);

        return new Database(cbh, cbj, cbg, cba, cbp, cbt, cbc, cbs, cbe);
    }

    /**
     * Deletes a database from disk by deleting all database related files and subdirectories
     * with the same basename as the given file.
     * @param file the name of the cbh file
     */
    public static void delete(File file) throws IOException {
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

    /**
     * Gets a game model
     * @param game the game
     * @return a model of the game
     * @throws IOException if the game couldn't be fetched due to an IO error
     * @throws ChessBaseException if an internal error occurred when fetching the game
     */
    public GameModel getGameModel(Game game) throws ChessBaseException {
        return loader.getGameModel(game);
    }

    /**
     * Gets a game from the database
     * @param gameId the id of the game to get
     * @return a model of the game
     * @throws IOException if the game couldn't be fetched due to an IO error
     * @throws ChessBaseException if an internal error occurred when fetching the game
     */
    public Game getGame(int gameId) throws ChessBaseException {
        return new Game(this, gameId);
    }

    /**
     * Gets a list over all games that matches the given search filter.
     * Use the {@link se.yarin.cbhlib.games.search.GameSearcher} directly for more options.
     * @param filter a search filter, or null to return all games
     * @param limit maximum number of games to return; 0 for no limit
     * @return all matching game, ordered by id
     */
    public List<Game> getGames(SearchFilter filter, int limit) {
        GameSearcher gameSearcher = new GameSearcher(this);
        if (filter != null) {
            gameSearcher.addFilter(filter);
        }
        Stream<Game> stream = gameSearcher.streamSearch();
        if (limit > 0) {
            stream = stream.limit(limit);
        }
        return stream.collect(Collectors.toList());
    }

    /**
     * Adds a new game to the database
     * @param model the model of the game to add
     * @return the game header of the saved game
     * @throws ChessBaseInvalidDataException if the game model contained invalid data
     * @throws IOException if the game couldn't be stored due to an IO error
     */
    public Game addGame(@NonNull GameModel model) throws ChessBaseInvalidDataException {
        return updater.addGame(model);
    }

    /**
     * Replaces a game in the database
     * @param gameId the id of the game to replace
     * @param model the model of the game to replace
     * @return the game header of the saved game
     * @throws ChessBaseInvalidDataException if the game model contained invalid data
     * @throws IOException if the game couldn't be stored due to an IO error
     */
    public Game replaceGame(int gameId, @NonNull GameModel model) throws ChessBaseInvalidDataException {
        return updater.replaceGame(gameId, model);
    }

    /**
     * Closes the database.
     *
     * @throws IOException if an IO error occurred when closing the database.
     */
    public void close() throws IOException {
        headerBase.close();
        extendedHeaderBase.close();
        movesBase.close();
        annotationBase.close();
        playerBase.close();
        tournamentBase.close();
        annotatorBase.close();
        sourceBase.close();
        teamBase.close();
    }

}
