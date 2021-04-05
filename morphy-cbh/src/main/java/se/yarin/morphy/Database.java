package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.games.AnnotationRepository;
import se.yarin.morphy.games.ExtendedGameHeaderStorage;
import se.yarin.morphy.games.GameHeaderIndex;
import se.yarin.morphy.games.MoveRepository;
import se.yarin.morphy.util.CBUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.OpenOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.*;

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
    }

    public static Database open(@NotNull File file) throws IOException {
        return open(file, Set.of(READ, WRITE));
    }

    /**
     * Opens a ChessBase database from disk.
     *
     * Make sure not to open the same database multiple times, as that could lead to an inconsistent database!
     * It's recommended that the caller keeps a lock on the database file itself.
     *
     * @param file the database file object
     * @return an instance of this class, representing the opened database
     * @throws IOException if an IO error occurred when opening the database; this may happen
     * if some mandator files are missing
     */
    public static Database open(@NotNull File file, @NotNull Set<OpenOption> openOptions) throws IOException {
        if (!CBUtil.extension(file).equals(".cbh")) {
            throw new IllegalArgumentException("The extension of the database file must be .cbh");
        }

        boolean create = openOptions.contains(CREATE) || openOptions.contains(CREATE_NEW);
        if (create && !openOptions.contains(WRITE)) {
            throw new IllegalArgumentException("Can't create a new database in read-only mode");
        }

        if (openOptions.contains(WRITE)) {
            // TODO: Make simple validation that all essential files exist and have size > 0
            // before attempting anything
            ExtendedGameHeaderStorage.upgrade(file);
            // TODO: Upgrade TournamentExtra as well
        }

        // The mandatory files
        GameHeaderIndex gameHeaderIndex = GameHeaderIndex.open(file, openOptions);
        MoveRepository moveRepository = MoveRepository.open(CBUtil.fileWithExtension(file, ".cbg"), openOptions);
        AnnotationRepository annotationRepository = AnnotationRepository.open(CBUtil.fileWithExtension(file, ".cba"), openOptions);
        PlayerIndex playerIndex = PlayerIndex.open(CBUtil.fileWithExtension(file, ".cbp"), openOptions);
        TournamentIndex tournamentIndex = TournamentIndex.open(CBUtil.fileWithExtension(file, ".cbt"), openOptions);
        AnnotatorIndex annotatorIndex = AnnotatorIndex.open(CBUtil.fileWithExtension(file, ".cbc"), openOptions);
        SourceIndex sourceIndex = SourceIndex.open(CBUtil.fileWithExtension(file, ".cbs"), openOptions);

        // Optional files. Only in very early ChessBase databases are they missing.
        // If the database is opened in read-only mode, don't create them but instead provide empty versions
        File cbjFile = CBUtil.fileWithExtension(file, ".cbj");
        File cbttFile = CBUtil.fileWithExtension(file, ".cbtt");
        File cbeFile = CBUtil.fileWithExtension(file, ".cbe");
        File cblFile = CBUtil.fileWithExtension(file, ".cbl");

        ExtendedGameHeaderStorage extendedGameHeaderStorage = cbjFile.exists() || create
                ? ExtendedGameHeaderStorage.open(cbjFile, openOptions)
                : new ExtendedGameHeaderStorage();
        TournamentExtraStorage tournamentExtraStorage = cbttFile.exists() || create
                ? TournamentExtraStorage.open(cbttFile, openOptions)
                : new TournamentExtraStorage();
        TeamIndex teamIndex = cbeFile.exists() || create ? TeamIndex.open(cbeFile, openOptions) : new TeamIndex();
        GameTagIndex gameTagIndex = cblFile.exists() || create ? GameTagIndex.open(cblFile, openOptions) : new GameTagIndex();

        return new Database(gameHeaderIndex, extendedGameHeaderStorage, moveRepository, annotationRepository,
                playerIndex, tournamentIndex, tournamentExtraStorage, annotatorIndex, sourceIndex, teamIndex, gameTagIndex);
    }
}
