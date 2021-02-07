package se.yarin.cbhlib.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.*;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.cbhlib.exceptions.ChessBaseIOException;
import se.yarin.cbhlib.exceptions.ChessBaseInvalidDataException;
import se.yarin.cbhlib.games.*;
import se.yarin.cbhlib.games.search.GameSearcher;
import se.yarin.cbhlib.moves.ChessBaseMoveDecodingException;
import se.yarin.cbhlib.moves.MovesSerializer;
import se.yarin.chess.GameMovesModel;

import java.nio.ByteBuffer;

public class GamesValidator {
    private static final Logger log = LoggerFactory.getLogger(GamesValidator.class);

    private final Database db;
    private final GameLoader loader;

    public GamesValidator(Database db) {
        this.db = db;
        this.loader = new GameLoader(db);
    }

    public void readAllGames() throws ChessBaseException {
        GameSearcher gameSearcher = new GameSearcher(db);

        for (Game game : gameSearcher.iterableSearch()) {
            game.getModel();
        }
    }

    public void validateMovesAndAnnotationOffsets() throws ChessBaseException {
        long lastMovesOfs = 0, lastAnnotationOfs = 0;
        for (GameHeader gameHeader : db.getHeaderBase().iterable()) {
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

        lastMovesOfs = 0;
        lastAnnotationOfs = 0;
        for (ExtendedGameHeader extendedGameHeader : db.getExtendedHeaderBase().iterable()) {
            if (extendedGameHeader.getMovesOffset() <= lastMovesOfs) {
                throw new ChessBaseException(String.format("Game %d has moves at offset %d while the previous game had moves at offset %d",
                        extendedGameHeader.getId(), extendedGameHeader.getMovesOffset(), lastMovesOfs));
            }
            lastMovesOfs = extendedGameHeader.getMovesOffset();

            if (extendedGameHeader.getAnnotationOffset() > 0) {
                if (extendedGameHeader.getAnnotationOffset() <= lastAnnotationOfs) {
                    throw new ChessBaseException(String.format("Game %d has annotations at offset %d while the last annotated game had annotations at offset %d",
                            extendedGameHeader.getId(), extendedGameHeader.getAnnotationOffset(), lastAnnotationOfs));
                }
                lastAnnotationOfs = extendedGameHeader.getAnnotationOffset();
            }
        }
    }

    public int processGames(boolean loadMoves, Runnable progressCallback) {
        int numGames = 0, numDeleted = 0, numAnnotated = 0, numText = 0, numErrors = 0, numChess960 = 0;
        long lastMovesOfs = this.db.getMovesBase().getStorage().getHeaderSize();
        long lastAnnotationOfs = this.db.getAnnotationBase().getStorage().getHeaderSize();
        int numOverlappingAnnotations = 0, numOverlappingMoves = 0;
        int numAnnotationGaps = 0, numMoveGaps = 0;
        int annotationFreeSpace = 0, moveFreeSpace = 0;
        int numMoveDecodingErrors = 0, numInvalidEntityReferences = 0;
        boolean moveOffsetDiffers = false, annotationOffsetDiffers = false;

        MovesSerializer movesSerializer = new MovesSerializer(true);

        GameSearcher gameSearcher = new GameSearcher(db);
        for (Game game : gameSearcher.iterableSearch()) {
            GameHeader header = game.getHeader();
            ExtendedGameHeader extendedHeader = game.getExtendedHeader();

            if (extendedHeader.getId() != header.getId()) {
                log.warn(String.format("Game %d: Extended game header has wrong id %d",
                        header.getId(), extendedHeader.getId()));
            }

            if (extendedHeader.getMovesOffset() != 0 && extendedHeader.getMovesOffset() != header.getMovesOffset() && !moveOffsetDiffers) {
                log.warn(String.format("Game %d: Move offset differs between header files (%d != %d) [ignoring similar errors]",
                        header.getId(), header.getMovesOffset(), extendedHeader.getMovesOffset()));
                moveOffsetDiffers = true; // If this happens in one game, it usually happens in many games
            }

            if (extendedHeader.getAnnotationOffset() != 0 && extendedHeader.getAnnotationOffset() != header.getAnnotationOffset() && !annotationOffsetDiffers) {
                log.warn(String.format("Game %d: Annotation offset differs between header files (%d != %d) [ignoring similar errors]",
                        header.getId(), header.getAnnotationOffset(), extendedHeader.getAnnotationOffset()));
                annotationOffsetDiffers = true; // If this happens in one game, it usually happens in many games
            }

            if (header.getAnnotationOffset() > 0) {
                int annotationSize = db.getAnnotationBase().getStorage().readBlob(header.getAnnotationOffset()).limit();
                int annotationEnd = header.getAnnotationOffset() + annotationSize;
                if (log.isDebugEnabled()) {
                    log.debug(String.format("Game %d: Annotation [%d, %d)", header.getId(), header.getAnnotationOffset(), annotationEnd));
                }
                if (header.getAnnotationOffset() < lastAnnotationOfs) {
                    if (numOverlappingAnnotations == 0) {
                        log.warn(String.format("Game %d has annotation data at offset %d but previous game annotation data ended at %d",
                                game.getId(), header.getAnnotationOffset(), lastAnnotationOfs));
                    }
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
                log.debug(String.format("Game %d: Moves [%d, %d)", header.getId(), header.getMovesOffset(), movesEnd));
            }
            if (header.getMovesOffset() < lastMovesOfs) {
                if (numOverlappingMoves == 0) {
                    log.warn(String.format("Game %d has move data at offset %d but previous game move data ended at %d",
                            game.getId(), header.getMovesOffset(), lastMovesOfs));
                }
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
                    // Deserialize the game header (and lookup player, team, source, commentator)
                    loader.getGameHeaderModel(game);

                    if (loadMoves) {
                        // Deserialize explicitly to be able to catch the exceptions
                        ByteBuffer movesBlob = this.db.getMovesBase().getMovesBlob(game.getMovesOffset());
                        GameMovesModel moves = movesSerializer.deserializeMoves(movesBlob, true, game.getId());
                        this.db.getAnnotationBase().getAnnotations(moves, game.getAnnotationOffset());
                    }
                    numGames += 1;
                }
            } catch (ChessBaseMoveDecodingException e) {
                if (numMoveDecodingErrors < 5) {
                    log.error("Move decoding error in game " + game.getId() + ": " + e.getMessage());
                }
                numMoveDecodingErrors += 1;
                numErrors += 1;
            } catch (ChessBaseInvalidDataException e) {
                if (numInvalidEntityReferences < 5) {
                    log.error("Invalid data in game " + game.getId() + ": " + e.getMessage());
                }
                numInvalidEntityReferences += 1;
                numErrors += 1;
            } catch (ChessBaseIOException | AssertionError e) {
                log.error("Critical error in game " + game.getId() + ": " + e.getMessage());
                numErrors += 1;
            } finally {
                progressCallback.run();
            }
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

        return numErrors;
    }
}
