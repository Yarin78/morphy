package se.yarin.cbhlib.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.ChessBaseException;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.GameHeader;
import se.yarin.cbhlib.GameHeaderBase;

import java.io.IOException;

public class GamesValidator {
    private static final Logger log = LoggerFactory.getLogger(GamesValidator.class);

    private Database db;

    public GamesValidator(Database db) {
        this.db = db;
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

    public void processGames(boolean loadMoves, Runnable progressCallback) {
        GameHeaderBase headerBase = db.getHeaderBase();

        // System.out.println("Loading all " + headerBase.size() + " games...");
        int numGames = 0, numDeleted = 0, numAnnotated = 0, numText = 0, numErrors = 0, numChess960 = 0;
        int lastAnnotationOfs = 0, lastMovesOfs = 0, numBadAnnotationsOrder = 0, numBadMovesOrder = 0;

        for (GameHeader header : headerBase) {
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
                progressCallback.run();
            }
        }

        log.info(String.format("%d games loaded (%d deleted, %d annotated, %d guiding texts, %d Chess960)", numGames, numDeleted, numAnnotated, numText, numChess960));
        if (numErrors > 0) {
            log.warn(String.format("%d errors encountered", numErrors));
        }
        if (numBadMovesOrder > 0) {
            log.warn(String.format("%d games had their move data before that of the previous game", numBadMovesOrder));
        }
        if (numBadAnnotationsOrder > 0) {
            log.warn(String.format("%d games had their annotation data before that of the previous game", numBadAnnotationsOrder));
        }
    }
}
