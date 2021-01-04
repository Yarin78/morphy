package se.yarin.cbhlib;

import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.annotations.AnnotationBase;
import se.yarin.cbhlib.entities.AnnotatorBase;
import se.yarin.cbhlib.entities.TournamentBase;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.GameHeaderBase;
import se.yarin.cbhlib.games.GameLoader;
import se.yarin.cbhlib.moves.MovesBase;
import se.yarin.cbhlib.entities.PlayerBase;
import se.yarin.cbhlib.entities.SourceBase;
import se.yarin.chess.GameModel;

import java.io.File;
import java.io.IOException;

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

    @Getter @NonNull private final GameHeaderBase headerBase;
    @Getter @NonNull private final MovesBase movesBase;
    @Getter @NonNull private final AnnotationBase annotationBase;
    @Getter @NonNull private final PlayerBase playerBase;
    @Getter @NonNull private final TournamentBase tournamentBase;
    @Getter @NonNull private final AnnotatorBase annotatorBase;
    @Getter @NonNull private final SourceBase sourceBase;

    /**
     * Creates a new in-memory ChessBase database.
     *
     * Important: Write operations to the database will not be persisted to disk.
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

        this.loader = new GameLoader(this);
        this.updater = new DatabaseUpdater(this, loader);
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
        MovesBase cbg = MovesBase.open(new File(base + ".cbg"));
        AnnotationBase cba = AnnotationBase.open(new File(base + ".cba"));
        PlayerBase cbp = PlayerBase.open(new File(base + ".cbp"));
        TournamentBase cbt = TournamentBase.open(new File(base + ".cbt"));
        AnnotatorBase cbc = AnnotatorBase.open(new File(base + ".cbc"));
        SourceBase cbs = SourceBase.open(new File(base + ".cbs"));

        return new Database(cbh, cbg, cba, cbp, cbt, cbc, cbs);
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
        MovesBase cbg = MovesBase.openInMemory(new File(base + ".cbg"));
        AnnotationBase cba = AnnotationBase.openInMemory(new File(base + ".cba"));
        PlayerBase cbp = PlayerBase.openInMemory(new File(base + ".cbp"));
        TournamentBase cbt = TournamentBase.openInMemory(new File(base + ".cbt"));
        AnnotatorBase cbc = AnnotatorBase.openInMemory(new File(base + ".cbc"));
        SourceBase cbs = SourceBase.openInMemory(new File(base + ".cbs"));

        return new Database(cbh, cbg, cba, cbp, cbt, cbc, cbs);
    }

    /**
     * Creates a new ChessBase database on disk.
     * @param file the database file object
     * @return an instance of this class, representing the created database
     * @throws IOException if an IO error occurred when trying to create the database
     */
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

    /**
     * Gets a game from the database
     * @param gameId the id of the game to get
     * @return a model of the game
     * @throws IOException if the game couldn't be fetched due to an IO error
     * @throws ChessBaseException if an internal error occurred when fetching the game
     */
    public GameModel getGameModel(int gameId) throws IOException, ChessBaseException {
        return loader.getGameModel(gameId);
    }

    /**
     * Adds a new game to the database
     * @param model the model of the game to add
     * @return the game header of the saved game
     * @throws IOException if the game couldn't be stored due to an IO error
     */
    public GameHeader addGame(@NonNull GameModel model) throws IOException {
        return updater.addGame(model);
    }

    /**
     * Replaces a game in the database
     * @param gameId the id of the game to replace
     * @param model the model of the game to replace
     * @return the game header of the saved game
     * @throws IOException if the game couldn't be stored due to an IO error
     */
    public GameHeader replaceGame(int gameId, @NonNull GameModel model) throws IOException {
        return updater.replaceGame(gameId, model);
    }

    /**
     * Closes the database.
     *
     * @throws IOException if an IO error occurred when closing the database.
     */
    public void close() throws IOException {
        headerBase.close();
        movesBase.close();
        annotationBase.close();
        playerBase.close();
        tournamentBase.close();
        annotatorBase.close();
        sourceBase.close();
    }

}
