package se.yarin.cbhlib;

import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.entities.*;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.cbhlib.games.ExtendedGameHeader;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.RatingType;
import se.yarin.chess.*;

import java.nio.ByteBuffer;
import java.util.Calendar;

/**
 * Class that represents a game stored in a {@link Database}.
 * Immutable.
 */
public class Game {
    private static final Logger log = LoggerFactory.getLogger(Game.class);

    @Getter
    private final Database database;

    @Getter
    private final GameHeader header;

    @Getter
    private final ExtendedGameHeader extendedHeader;

    public Game(@NonNull Database database, @NonNull GameHeader header, @NonNull ExtendedGameHeader extendedHeader) {
        this.database = database;
        this.header = header;
        this.extendedHeader = extendedHeader;

        assert header.getId() == extendedHeader.getId();
    }

    // All getters below are just convenience getters that fetch
    // the data either from the header entity or the extended header entity (or both!)

    public int getId() {
        return header.getId();
    }

    public int getWhitePlayerId() {
        return header.getWhitePlayerId();
    }

    public int getBlackPlayerId() {
        return header.getBlackPlayerId();
    }

    public @NonNull PlayerEntity getWhite() {
        return database.getPlayerBase().get(header.getWhitePlayerId());
    }

    public @NonNull PlayerEntity getBlack() {
        return database.getPlayerBase().get(header.getBlackPlayerId());
    }

    public int getWhiteElo() {
        return header.getWhiteElo();
    }

    public int getBlackElo() {
        return header.getBlackElo();
    }

    public int getTournamentId() {
        return header.getTournamentId();
    }

    public @NonNull TournamentEntity getTournament() {
        return database.getTournamentBase().get(header.getTournamentId());
    }

    public int getAnnotatorId() {
        return header.getAnnotatorId();
    }

    public @NonNull AnnotatorEntity getAnnotator() {
        return database.getAnnotatorBase().get(header.getAnnotatorId());
    }

    public int getSourceId() {
        return header.getSourceId();
    }

    public @NonNull SourceEntity getSource() {
        return database.getSourceBase().get(header.getSourceId());
    }

    public int getWhiteTeamId() {
        return extendedHeader.getWhiteTeamId();
    }

    public int getBlackTeamId() {
        return extendedHeader.getBlackTeamId();
    }

    public TeamEntity getWhiteTeam() {
        int teamId = extendedHeader.getWhiteTeamId();
        return teamId == -1 ? null : database.getTeamBase().get(teamId);
    }

    public TeamEntity getBlackTeam() {
        int teamId = extendedHeader.getBlackTeamId();
        return teamId == -1 ? null : database.getTeamBase().get(teamId);
    }

    public boolean isGuidingText() {
        return header.isGuidingText();
    }

    public boolean isDeleted() {
        return header.isDeleted();
    }

    public GameResult getResult() {
        return header.getResult();
    }

    public int getRound() {
        return header.getRound();
    }

    public int getSubRound() {
        return header.getSubRound();
    }

    public Date getPlayedDate() {
        return header.getPlayedDate();
    }

    public Calendar getCreationTime() {
        return extendedHeader.getCreationTime();
    }

    public long getCreationTimestamp() {
        return extendedHeader.getCreationTimestamp();
    }

    public long getLastChangedTimestamp() {
        return extendedHeader.getLastChangedTimestamp();
    }

    public Calendar getLastChangedTime() {
        return extendedHeader.getLastChangedTime();
    }

    public int getNoMoves() {
        return header.getNoMoves();
    }

    public Eco getEco() {
        return header.getEco();
    }

    public int getGameVersion() {
        return extendedHeader.getGameVersion();
    }

    public RatingType getWhiteRatingType() {
        return extendedHeader.getWhiteRatingType();
    }

    public RatingType getBlackRatingType() {
        return extendedHeader.getBlackRatingType();
    }

    public NAG getLineEvaluation() {
        return header.getLineEvaluation();
    }

    public int getVariationsMagnitude() {
        return header.getVariationsMagnitude();
    }

    public int getCommentariesMagnitude() {
        return header.getCommentariesMagnitude();
    }

    public int getSymbolsMagnitude() {
        return header.getSymbolsMagnitude();
    }

    public long getMovesOffset() {
        return resolveOffset(header.getMovesOffset(), extendedHeader.getMovesOffset(), "move");
    }

    public long getAnnotationOffset() {
        return resolveOffset(header.getAnnotationOffset(), extendedHeader.getAnnotationOffset(), "annotation");
    }

    private long resolveOffset(int shortOffset, long longOffset, String type) {
        if (shortOffset == longOffset) {
            return longOffset;
        }

        if ((int) longOffset == shortOffset) {
            // This indicates that the offset requires more than 32 bits; use the extended version
            return longOffset;
        }

        // The difference is not due to the 32 bit limitation in the game header
        log.warn(String.format("The %s offset differs between the two header databases (%d != %d)", type, shortOffset, longOffset));
        // Use the default header offset, this is what ChessBase does
        return shortOffset;
    }

    public GameModel getModel() throws ChessBaseException {
        return database.getGameModel(this);
    }

    public ByteBuffer getMovesBlob() {
        return database.getMovesBase().getMovesBlob(getMovesOffset());
    }

    public ByteBuffer getAnnotationsBlob() {
        return database.getAnnotationBase().getAnnotationsBlob(getAnnotationOffset());
    }

}
