package se.yarin.morphy.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.yarin.chess.GameMovesModel;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.exceptions.MorphyException;
import se.yarin.morphy.exceptions.MorphyIOException;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.exceptions.MorphyMoveDecodingException;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.games.moves.MoveSerializer;

import java.nio.ByteBuffer;

public class GamesValidator {
    private static final Logger log = LoggerFactory.getLogger(GamesValidator.class);

    private final Database db;

    public GamesValidator(Database db) {
        this.db = db;
    }

    public void readAllGames() throws MorphyException {
        try (var txn = new DatabaseReadTransaction(db)) {
            for (Game game : txn.iterable()) {
                game.getModel();
            }
        }
    }

    public void validateMovesAndAnnotationOffsets() throws MorphyException {
        long lastMovesOfs = 0, lastAnnotationOfs = 0;
        for (GameHeader gameHeader : db.gameHeaderIndex().getAll()) { // TODO: iterable
            if (gameHeader.movesOffset() <= lastMovesOfs) {
                throw new MorphyException(String.format("Game %d has moves at offset %d while the previous game had moves at offset %d",
                        gameHeader.id(), gameHeader.movesOffset(), lastMovesOfs));
            }
            lastMovesOfs = gameHeader.movesOffset();

            if (gameHeader.annotationOffset() > 0) {
                if (gameHeader.annotationOffset() <= lastAnnotationOfs) {
                    throw new MorphyException(String.format("Game %d has annotations at offset %d while the last annotated game had annotations at offset %d",
                            gameHeader.id(), gameHeader.annotationOffset(), lastAnnotationOfs));
                }
                lastAnnotationOfs = gameHeader.annotationOffset();
            }
        }

        lastMovesOfs = 0;
        lastAnnotationOfs = 0;
        int curExtId = 0;
        for (ExtendedGameHeader extendedGameHeader : db.extendedGameHeaderStorage().getAll()) { // TODO: iterable
            curExtId += 1;
            if (extendedGameHeader.movesOffset() <= lastMovesOfs) {
                throw new MorphyException(String.format("Game %d has moves at offset %d while the previous game had moves at offset %d",
                        curExtId, extendedGameHeader.movesOffset(), lastMovesOfs));
            }
            lastMovesOfs = extendedGameHeader.movesOffset();

            if (extendedGameHeader.annotationOffset() > 0) {
                if (extendedGameHeader.annotationOffset() <= lastAnnotationOfs) {
                    throw new MorphyException(String.format("Game %d has annotations at offset %d while the last annotated game had annotations at offset %d",
                            curExtId, extendedGameHeader.annotationOffset(), lastAnnotationOfs));
                }
                lastAnnotationOfs = extendedGameHeader.annotationOffset();
            }
        }
    }

    public int processGames(boolean loadMoves, boolean warningAsErrors, Runnable progressCallback) {
        int numGames = 0, numDeleted = 0, numAnnotated = 0, numText = 0, numErrors = 0, numWarnings = 0, numChess960 = 0;
        long lastMovesOfs = this.db.moveRepository().getStorage().getHeader().headerSize();
        long lastAnnotationOfs = this.db.annotationRepository().getStorage().getHeader().headerSize();
        int numOverlappingAnnotations = 0, numOverlappingMoves = 0;
        int numAnnotationGaps = 0, numMoveGaps = 0;
        int annotationFreeSpace = 0, moveFreeSpace = 0;
        int numMoveDecodingErrors = 0, numInvalidEntityReferences = 0;
        boolean moveOffsetDiffers = false, annotationOffsetDiffers = false;

        MoveSerializer movesSerializer = new MoveSerializer(db.context());
        movesSerializer.setLogDetailedErrors(true);

        try (var txn = new DatabaseReadTransaction(db)) {
            for (Game game : txn.iterable()) {
                GameHeader header = game.header();
                ExtendedGameHeader extendedHeader = game.extendedHeader();

                if (extendedHeader.movesOffset() != 0 && extendedHeader.movesOffset() != header.movesOffset() && !moveOffsetDiffers) {
                    log.warn(String.format("Game %d: Move offset differs between header files (%d != %d) [ignoring similar errors]",
                            header.id(), header.movesOffset(), extendedHeader.movesOffset()));
                    moveOffsetDiffers = true; // If this happens in one game, it usually happens in many games
                    numWarnings += 1;
                }

                if (extendedHeader.annotationOffset() != 0 && extendedHeader.annotationOffset() != header.annotationOffset() && !annotationOffsetDiffers) {
                    log.warn(String.format("Game %d: Annotation offset differs between header files (%d != %d) [ignoring similar errors]",
                            header.id(), header.annotationOffset(), extendedHeader.annotationOffset()));
                    annotationOffsetDiffers = true; // If this happens in one game, it usually happens in many games
                    numWarnings += 1;
                }

                if (header.annotationOffset() > 0) {
                    int annotationSize = db.annotationRepository().getAnnotationsBlobSize(header.annotationOffset());
                    int annotationEnd = header.annotationOffset() + annotationSize;
                    if (log.isDebugEnabled()) {
                        log.debug(String.format("Game %d: Annotation [%d, %d)", header.id(), header.annotationOffset(), annotationEnd));
                    }
                    if (header.annotationOffset() < lastAnnotationOfs) {
                        if (numOverlappingAnnotations == 0) {
                            log.warn(String.format("Game %d has annotation data at offset %d but previous game annotation data ended at %d",
                                    game.id(), header.annotationOffset(), lastAnnotationOfs));
                            numWarnings += 1;
                        }
                        numOverlappingAnnotations += 1;
                    } else if (header.annotationOffset() > lastAnnotationOfs) {
                        numAnnotationGaps += 1;
                        annotationFreeSpace += header.annotationOffset() - lastAnnotationOfs;
                    }
                    lastAnnotationOfs = annotationEnd;
                }

                int movesSize = db.moveRepository().getMovesBlobSize(header.movesOffset());
                int movesEnd = header.movesOffset() + movesSize;
                if (log.isDebugEnabled()) {
                    log.debug(String.format("Game %d: Moves [%d, %d)", header.id(), header.movesOffset(), movesEnd));
                }
                if (header.movesOffset() < lastMovesOfs) {
                    if (numOverlappingMoves == 0) {
                        log.warn(String.format("Game %d has move data at offset %d but previous game move data ended at %d",
                                game.id(), header.movesOffset(), lastMovesOfs));
                        numWarnings += 1;
                    }
                    numOverlappingMoves += 1;
                } else if (header.movesOffset() > lastMovesOfs) {
                    numMoveGaps += 1;
                    moveFreeSpace += header.movesOffset() - lastMovesOfs;
                }
                lastMovesOfs = movesEnd;

                try {
                    if (header.chess960StartPosition() >= 0) {
                        numChess960 += 1;
                    }
                    if (header.annotationOffset() > 0) {
                        numAnnotated += 1;
                    }
                    if (header.deleted()) {
                        numDeleted += 1;
                    }
                    if (header.guidingText()) {
                        numText += 1;
                    }
                    if (!header.guidingText()) {
                        // Deserialize the game header (and lookup player, team, source, commentator)
                        game.getGameHeaderModel();

                        if (loadMoves) {
                            // Deserialize explicitly to be able to catch the exceptions
                            ByteBuffer movesBlob = this.db.moveRepository().getMovesBlob(game.getMovesOffset());
                            GameMovesModel moves = movesSerializer.deserializeMoves(movesBlob, true, game.id());
                            this.db.annotationRepository().getAnnotations(moves, game.getAnnotationOffset());
                        }
                        numGames += 1;
                    }
                } catch (MorphyMoveDecodingException e) {
                    if (numMoveDecodingErrors < 5) {
                        log.error("Move decoding error in game " + game.id() + ": " + e.getMessage());
                    }
                    numMoveDecodingErrors += 1;
                    numErrors += 1;
                } catch (MorphyInvalidDataException e) {
                    if (numInvalidEntityReferences < 5) {
                        log.error("Invalid data in game " + game.id() + ": " + e.getMessage());
                    }
                    numInvalidEntityReferences += 1;
                    numErrors += 1;
                } catch (MorphyIOException | AssertionError e) {
                    log.error("Critical error in game " + game.id() + ": " + e.getMessage());
                    numErrors += 1;
                } finally {
                    progressCallback.run();
                }
            }
        }

        long movesStorageSize = this.db.moveRepository().getStorage().getSize();
        long annotationsStorageSize = this.db.annotationRepository().getStorage().getSize();
        if (movesStorageSize > lastMovesOfs) {
            numMoveGaps += 1;
            moveFreeSpace += movesStorageSize - lastMovesOfs;
        }
        if (annotationsStorageSize > lastAnnotationOfs) {
            numAnnotationGaps += 1;
            annotationFreeSpace += annotationsStorageSize - lastAnnotationOfs;
        }

        long expectedUnusedMovesBytes = this.db.moveRepository().getStorage().getHeader().wasted();
        long expectedUnusedAnnotationBytes = this.db.moveRepository().getStorage().getHeader().wasted();

        if (moveFreeSpace != expectedUnusedMovesBytes) {
            log.warn(String.format("Move repository header says %d unused bytes, but it actually was %d bytes", expectedUnusedMovesBytes, moveFreeSpace));
            numWarnings += 1;
        }
        if (moveFreeSpace != expectedUnusedMovesBytes) {
            log.warn(String.format("Annotation repository header says %d unused bytes, but it actually was %d bytes", expectedUnusedAnnotationBytes, annotationFreeSpace));
            numWarnings += 1;
        }

        log.info(String.format("%d games loaded (%d deleted, %d annotated, %d guiding texts, %d Chess960)", numGames, numDeleted, numAnnotated, numText, numChess960));
        if (numErrors > 0) {
            log.warn(String.format("%d errors in the game data encountered (%d move decoding errors, %d entity references errors)", numErrors, numMoveDecodingErrors, numInvalidEntityReferences));
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

        if (warningAsErrors) {
            numErrors += numWarnings;
        }

        return numErrors;
    }
}
