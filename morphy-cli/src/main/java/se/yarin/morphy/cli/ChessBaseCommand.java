package se.yarin.morphy.cli;

import me.tongfei.progressbar.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import se.yarin.cbhlib.*;
import se.yarin.cbhlib.entities.Entity;
import se.yarin.chess.GameModel;

import java.io.File;
import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "cb", description = "Performs an operation on a ChessBase file",
        mixinStandardHelpOptions = true,
        subcommands = { Games.class, Players.class, Check.class})
class ChessBaseCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("A subcommand must be specified; use --help");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ChessBaseCommand()).execute(args);
        System.exit(exitCode);
    }
}

@CommandLine.Command(name = "games", mixinStandardHelpOptions = true)
class Games implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "The ChessBase file to load")
    private File cbhFile;

    @Override
    public Integer call() throws IOException {
        Database db = Database.open(cbhFile);
        System.out.println(db.getHeaderBase().size() + " games");
        db.close();
        return 0;
    }
}

@CommandLine.Command(name = "players", mixinStandardHelpOptions = true)
class Players implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "The ChessBase file to load")
    private File cbhFile;

    @Override
    public Integer call() throws IOException {
        Database db = Database.open(cbhFile);
        System.out.println(db.getPlayerBase().getCount() + " players");
        db.close();
        return 0;
    }
}


@CommandLine.Command(name = "check", mixinStandardHelpOptions = true)
class Check implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(Check.class);

    @CommandLine.Parameters(index = "0", description = "The ChessBase file to load")
    private File cbhFile;

    private <T extends Entity & Comparable<T>> void loadEntities(EntityBase<T> entities, String entityType) throws IOException {
        int numEntities = 0, numEqual = 0, numWrongOrder = 0, numEmpty = 0;

        TreeSet<Integer> existingIds = new TreeSet<>();

        String entityTypeCapitalized = entityType.substring(0, 1).toUpperCase() + entityType.substring(1);
        try (ProgressBar progressBar = new ProgressBar(entityTypeCapitalized, entities.getCount())) {
            T last = null;
            Iterator<T> iterator = entities.getAscendingIterator();
            while (iterator.hasNext()) {
                progressBar.step();
                T current = iterator.next();
                existingIds.add(current.getId());

                entities.get(current.getId()); // Sanity check that we can do this lookup as well
                if (current.getCount() == 0) {
                    numEmpty += 0;
                }

                if (numEntities > 0) {
                    if (current.compareTo(last) < 0) {
                        if (log.isDebugEnabled()) {
                            log.debug("Wrong order: (%d) %s  (%d) %s".formatted(last.getId(), last, current.getId(), current));
                        }
                        numWrongOrder += 1;
                    }
                    if (current.compareTo(last) == 0) {
                        numEqual += 1;
                    }
                }
                last = current;
                numEntities += 1;
            }
        }

        if (existingIds.size() != numEntities) {
            log.warn(String.format("Found %d unique %s during sorted iteration, expected to find %d %s",
                    existingIds.size(), entityType, numEntities, entityType));
        }
        if (numEntities != entities.getCount()) {
            log.warn(String.format("Iterated over %d %s in ascending order, expected to find %d %s",
                    numEntities, entityType, entities.getCount(), entityType));
        }
        if (numWrongOrder > 0) {
            log.warn(String.format("%d %s were in the wrong order", numWrongOrder, entityType));
        }
        if (numEqual > 0) {
            log.warn(String.format("%d %s with the same key as the previous entity", numEqual, entityType));
        }
        if (numEmpty > 0) {
            log.warn(String.format("%d %s have count 0", numEmpty, entityType));
        }
        if (entities.getCapacity() > numEntities) {
            log.info(String.format("Database has additional capacity for %d %s",
                    entities.getCapacity() - numEntities, entityType));
        }
        if (existingIds.size() > 0) {
            if (existingIds.first() != 0) {
                log.warn(String.format("First id in %s was %d, expected 0", entityType, existingIds.first()));
            }
            if (existingIds.last() != entities.getCount() - 1) {
                log.warn(String.format("Last id in %s was %d, expected %d",
                        entityType, existingIds.last(), entities.getCount() - 1));
            }
        }
    }

    private void loadGames(Database db, boolean loadMoves) {
        GameHeaderBase headerBase = db.getHeaderBase();

        // System.out.println("Loading all " + headerBase.size() + " games...");
        int numGames = 0, numDeleted = 0, numAnnotated = 0, numText = 0, numErrors = 0, numChess960 = 0;
        int lastAnnotationOfs = 0, lastMovesOfs = 0, numBadAnnotationsOrder = 0, numBadMovesOrder = 0;

        String task = "Integrity checking game headers";
        if (loadMoves) {
            task += ", moves and annotations";
        }
        System.out.println(task + "...");
        try (ProgressBar progressBar = new ProgressBar("Games", headerBase.size())) {
            Iterable<GameHeader> iterable = () -> headerBase.iterator();
            for (GameHeader header : iterable) {
                if (header.getAnnotationOffset() > 0) {
                    if (header.getAnnotationOffset() < lastAnnotationOfs) {
                        numBadAnnotationsOrder += 1;
                    }
                    lastAnnotationOfs = header.getAnnotationOffset();
                }
                if (header.getMovesOffset() < lastMovesOfs) {
                    numBadMovesOrder += 1;
                }
                lastMovesOfs = header.getMovesOffset();

                try {
                    if (header.getChess960StartPosition() >= 0) {
                        numChess960 += 1;
                    }
                    if (header.getAnnotationOffset() > 0) {
                        numAnnotated += 1;
                    }
                    if (header.isDeleted()) {
                        numDeleted += 1;
                    }
                    if (header.isGuidingText()) {
                        numText += 1;
                    }
                    if (!header.isGuidingText()) {
                        if (loadMoves) {
                            // Deserialize all the moves and annotations
                            db.getGameModel(header.getId());
                        } else {
                            // Only deserialize the game header (and lookup player, team, source, commentator)
                            db.getHeaderModel(header);
                        }
                        numGames += 1;
                    }
                } catch (ChessBaseException | IOException e) {
                    numErrors += 1;
                } finally {
                    progressBar.step();
                }
            }
        }
        System.out.println(String.format("%d games loaded (%d deleted, %d annotated, %d guiding texts, %d Chess960)", numGames, numDeleted, numAnnotated, numText, numChess960));
        if (numErrors > 0) {
            System.out.println(String.format("%d errors encountered", numErrors));
        }
        if (numBadMovesOrder > 0) {
            System.out.println(String.format("%d games had their move data before that of the previous game", numBadMovesOrder));
        }
        if (numBadAnnotationsOrder > 0) {
            System.out.println(String.format("%d games had their annotation data before that of the previous game", numBadAnnotationsOrder));
        }
    }

    @Override
    public Integer call() throws IOException {
        Database db = Database.open(cbhFile);
        loadEntities(db.getPlayerBase(), "players");
        loadEntities(db.getTournamentBase(), "tournaments");
        loadEntities(db.getSourceBase(), "sources");
        loadEntities(db.getAnnotatorBase(), "annotators");
        //loadGames(db, true);
        db.close();
        return 0;
    }
}
