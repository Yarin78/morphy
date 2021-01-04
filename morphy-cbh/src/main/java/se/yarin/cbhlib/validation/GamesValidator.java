package se.yarin.cbhlib.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.*;

import java.io.IOException;

public class GamesValidator {
    private static final Logger log = LoggerFactory.getLogger(GamesValidator.class);

    private final Database db;
    private final GameLoader loader;

    public GamesValidator(Database db) {
        this.db = db;
        this.loader = new GameLoader(db);
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

            if (gameHeader.getAnnotationOffset() > 0) {
                if (gameHeader.getAnnotationOffset() <= lastAnnotationOfs) {
                    throw new ChessBaseException(String.format("Game %d has annotations at offset %d while the last annotated game had annotations at offset %d",
                            gameHeader.getId(), gameHeader.getAnnotationOffset(), lastAnnotationOfs));
                }
                lastAnnotationOfs = gameHeader.getAnnotationOffset();
            }
        }
    }

    public void processGames(boolean loadMoves, Runnable progressCallback) throws IOException {
        GameHeaderBase headerBase = db.getHeaderBase();

        // System.out.println("Loading all " + headerBase.size() + " games...");
        int numGames = 0, numDeleted = 0, numAnnotated = 0, numText = 0, numErrors = 0, numChess960 = 0;
        int lastAnnotationOfs = FileBlobStorage.DEFAULT_SERIALIZED_HEADER_SIZE, lastMovesOfs = FileBlobStorage.DEFAULT_SERIALIZED_HEADER_SIZE;
        int numOverlappingAnnotations = 0, numOverlappingMoves = 0;
        int numAnnotationGaps = 0, numMoveGaps = 0;
        int annotationFreeSpace = 0, moveFreeSpace = 0;

        for (GameHeader header : headerBase) {
            if (header.getAnnotationOffset() > 0) {
                int annotationSize = db.getAnnotationBase().getStorage().readBlob(header.getAnnotationOffset()).limit();
                int annotationEnd = header.getAnnotationOffset() + annotationSize;
                if (log.isDebugEnabled()) {
                    log.debug(String.format("Game %5d: Annotation [%d, %d)", header.getId(), header.getAnnotationOffset(), annotationEnd));
                }
                if (header.getAnnotationOffset() < lastAnnotationOfs) {
                    numOverlappingAnnotations += 1;
                } else if (header.getAnnotationOffset() > lastAnnotationOfs) {
                    numAnnotationGaps += 1;
                    annotationFreeSpace += header.getAnnotationOffset() - lastAnnotationOfs;
                }
                lastAnnotationOfs = annotationEnd;
            }

            int movesSize = db.getMovesBase().getStorage().readBlob(header.getMovesOffset()).limit();
            int movesEnd = header.getMovesOffset() + movesSize;
            if (log.isDebugEnabled()) {
                log.debug(String.format("Game %5d: Moves [%d, %d)", header.getId(), header.getMovesOffset(), movesEnd));
            }
            if (header.getMovesOffset() < lastMovesOfs) {
                numOverlappingMoves += 1;
            } else if (header.getMovesOffset() > lastMovesOfs) {
                numMoveGaps += 1;
                moveFreeSpace += header.getMovesOffset() - lastMovesOfs;
            }
            lastMovesOfs = movesEnd;

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
                        loader.getGameModel(header.getId());
                    } else {
                        // Only deserialize the game header (and lookup player, team, source, commentator)
                        loader.getHeaderModel(header);
                    }
                    numGames += 1;
                }
            } catch (ChessBaseException | IOException e) {
                numErrors += 1;
            } finally {
                progressCallback.run();
            }
        }

        log.info(String.format("%d games loaded (%d deleted, %d annotated, %d guiding texts, %d Chess960)", numGames, numDeleted, numAnnotated, numText, numChess960));
        if (numErrors > 0) {
            log.warn(String.format("%d errors encountered", numErrors));
        }
        if (numOverlappingMoves > 0) {
            log.warn(String.format("%d games had overlapping move data", numOverlappingMoves));
        }
        if (numOverlappingAnnotations > 0) {
            log.warn(String.format("%d games had overlapping annotation data", numOverlappingAnnotations));
        }
        if (numAnnotationGaps > 0) {
            log.info(String.format("There were %d gaps in the annotation data (total %d bytes)", numAnnotationGaps, annotationFreeSpace));
        }
        if (numMoveGaps > 0) {
            log.info(String.format("There were %d gaps in the moves data (total %d bytes)", numMoveGaps, moveFreeSpace));
        }
    }
}
