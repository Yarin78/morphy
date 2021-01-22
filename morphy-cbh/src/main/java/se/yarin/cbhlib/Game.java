package se.yarin.cbhlib;

import lombok.Getter;
import lombok.NonNull;
import se.yarin.cbhlib.entities.*;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.cbhlib.games.ExtendedGameHeader;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.RatingType;
import se.yarin.chess.*;

import java.util.Calendar;

/**
 * Class that represents a game stored in a {@link Database}.
 * Immutable.
 */
public class Game {
    @Getter
    private final Database database;

    @Getter
    private final GameHeader header;

    @Getter
    private final ExtendedGameHeader extendedHeader;

    public int getId() {
        return header.getId();
    }

    public @NonNull PlayerEntity getWhite() {
        return database.getPlayerBase().get(header.getWhitePlayerId());
    }

    public @NonNull PlayerEntity getBlack() {
        return database.getPlayerBase().get(header.getBlackPlayerId());
    }

    public @NonNull TournamentEntity getTournament() {
        return database.getTournamentBase().get(header.getTournamentId());
    }

    public @NonNull AnnotatorEntity getAnnotator() {
        return database.getAnnotatorBase().get(header.getAnnotatorId());
    }

    public @NonNull SourceEntity getSource() {
        return database.getSourceBase().get(header.getSourceId());
    }

    public TeamEntity getWhiteTeam() {
        int teamId = extendedHeader.getWhiteTeamId();
        return teamId == -1 ? null : database.getTeamBase().get(teamId);
    }

    public TeamEntity getBlackTeam() {
        int teamId = extendedHeader.getBlackTeamId();
        return teamId == -1 ? null : database.getTeamBase().get(teamId);
    }

    public Game(@NonNull Database database, @NonNull GameHeader header, @NonNull ExtendedGameHeader extendedHeader) {
        this.database = database;
        this.header = header;
        this.extendedHeader = extendedHeader;

        assert header.getId() == extendedHeader.getId();
    }

    public Date getPlayedDate() {
        return header.getPlayedDate();
    }

    public GameResult getResult() {
        return header.getResult();
    }

    public boolean isGuidingText() {
        return header.isGuidingText();
    }

    public Calendar getCreationTime() {
        return extendedHeader.getCreationTime();
    }

    public long getCreationTimestamp() {
        return extendedHeader.getCreationTimestamp();
    }

    public Eco getEco() {
        return header.getEco();
    }

    public int getGameVersion() {
        return extendedHeader.getGameVersion();
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

    public int getWhiteElo() {
        return header.getWhiteElo();
    }

    public int getBlackElo() {
        return header.getBlackElo();
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

    public int getRound() {
        return header.getRound();
    }

    public int getSubRound() {
        return header.getSubRound();
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

    public int getWhitePlayerId() {
        return header.getWhitePlayerId();
    }

    public int getBlackPlayerId() {
        return header.getBlackPlayerId();
    }

    public boolean isDeleted() {
        return header.isDeleted();
    }

    public int getMovesOffset() {
        // TODO: Resolve if different; should be long
        return header.getMovesOffset();
    }

    public int getAnnotationOffset() {
        // TODO: Resolve if different; should be long
        return header.getAnnotationOffset();
    }

    public GameModel getModel() throws ChessBaseException {
        return database.getGameModel(this);
    }

    public int getTournamentId() {
        return header.getTournamentId();
    }

    public int getAnnotatorId() {
        return header.getAnnotatorId();
    }

    public int getSourceId() {
        return header.getSourceId();
    }

    public int getWhiteTeamId() {
        return extendedHeader.getWhiteTeamId();
    }

    public int getBlackTeamId() {
        return extendedHeader.getBlackTeamId();
    }
}
