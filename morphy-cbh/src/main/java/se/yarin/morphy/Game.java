package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.*;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.exceptions.MorphyException;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.games.RatingType;
import se.yarin.morphy.text.TextContentsModel;
import se.yarin.morphy.text.TextModel;

import java.nio.ByteBuffer;
import java.util.Calendar;

/**
 * Class that represents a game that is bound to a {@link Database}.
 */
public class Game {
    // TODO: This should only represent a game; Text should be a separate class
    private static final Logger log = LoggerFactory.getLogger(Game.class);

    private final @NotNull Database database;

    private final @Nullable DatabaseWriteTransaction transaction;

    private final @NotNull EntityRetriever entityRetriever;

    private final @NotNull GameHeader header;

    private final @NotNull ExtendedGameHeader extendedHeader;

    public Game(@NotNull Database database, @NotNull GameHeader header, @NotNull ExtendedGameHeader extendedHeader) {
        this.database = database;
        this.transaction = null;
        this.header = header;
        this.extendedHeader = extendedHeader;
        this.entityRetriever = database;
    }

    public Game(@NotNull DatabaseWriteTransaction transaction, @NotNull GameHeader header, @NotNull ExtendedGameHeader extendedHeader) {
        this.database = transaction.database();
        this.transaction = transaction;
        this.header = header;
        this.extendedHeader = extendedHeader;
        this.entityRetriever = transaction;
    }

    public @NotNull Database database() {
        return this.database;
    }

    /**
     * @return the transaction the game is part of, or null if the game is already committed to the database
     */
    public @Nullable DatabaseWriteTransaction transaction() {
        return transaction;
    }

    public @NotNull GameHeader header() {
        return header;
    }

    public @NotNull ExtendedGameHeader extendedHeader() {
        return extendedHeader;
    }

    // All getters below are just convenience getters that fetch
    // the data either from the header entity or the extended header entity (or both!)

    public int id() {
        return header.id();
    }

    public int whitePlayerId() {
        return header.whitePlayerId();
    }

    public int blackPlayerId() {
        return header.blackPlayerId();
    }

    public @NotNull Player white() {
        return entityRetriever.getPlayer(whitePlayerId());
    }

    public @NotNull Player black() {
        return entityRetriever.getPlayer(blackPlayerId());
    }

    public int whiteElo() {
        return header.whiteElo();
    }

    public int blackElo() {
        return header.blackElo();
    }

    public int tournamentId() {
        return header.tournamentId();
    }

    public @NotNull Tournament tournament() {
        return entityRetriever.getTournament(tournamentId());
    }

    public @NotNull TournamentExtra tournamentExtra() {
        return entityRetriever.getTournamentExtra(tournamentId());
    }

    public int annotatorId() {
        return header.annotatorId();
    }

    public @NotNull Annotator annotator() {
        return entityRetriever.getAnnotator(annotatorId());
    }

    public int sourceId() {
        return header.sourceId();
    }

    public @NotNull Source source() {
        return entityRetriever.getSource(sourceId());
    }

    public int whiteTeamId() {
        return extendedHeader.whiteTeamId();
    }

    public int blackTeamId() {
        return extendedHeader.blackTeamId();
    }

    public @Nullable Team whiteTeam() {
        int teamId = whiteTeamId();
        return teamId == -1 ? null : entityRetriever.getTeam(teamId);
    }

    public @Nullable Team blackTeam() {
        int teamId = blackTeamId();
        return teamId == -1 ? null : entityRetriever.getTeam(teamId);
    }

    public int gameTagId() { return extendedHeader.gameTagId(); }

    public @Nullable GameTag gameTag() {
        int tagId = gameTagId();
        return tagId == -1 ? null : entityRetriever.getGameTag(tagId);
    }

    public boolean guidingText() {
        return header.guidingText();
    }

    public boolean deleted() {
        return header.deleted();
    }

    public @NotNull GameResult result() {
        return header.result();
    }

    public int round() {
        return header.round();
    }

    public int subRound() {
        return header.subRound();
    }

    public @NotNull Date playedDate() {
        return header.playedDate();
    }

    public @NotNull Calendar creationTime() {
        return extendedHeader.creationTime();
    }

    public long creationTimestamp() {
        return extendedHeader.creationTimestamp();
    }

    public long lastChangedTimestamp() {
        return extendedHeader.lastChangedTimestamp();
    }

    public @NotNull Calendar lastChangedTime() {
        return extendedHeader.lastChangedTime();
    }

    public int noMoves() {
        return header.noMoves();
    }

    public @NotNull Eco eco() {
        return header.eco();
    }

    public int gameVersion() {
        return extendedHeader.gameVersion();
    }

    public @NotNull RatingType whiteRatingType() {
        return extendedHeader.whiteRatingType();
    }

    public @NotNull RatingType getBlackRatingType() {
        return extendedHeader.blackRatingType();
    }

    public @NotNull NAG lineEvaluation() {
        return header.lineEvaluation();
    }

    public int variationsMagnitude() {
        return header.variationsMagnitude();
    }

    public int commentariesMagnitude() {
        return header.commentariesMagnitude();
    }

    public int symbolsMagnitude() {
        return header.symbolsMagnitude();
    }

    public long getMovesOffset() {
        return resolveOffset(header.movesOffset(), extendedHeader.movesOffset(), "move");
    }

    public long getAnnotationOffset() {
        return resolveOffset(header.annotationOffset(), extendedHeader.annotationOffset(), "annotation");
    }

    private long resolveOffset(int shortOffset, long longOffset, @NotNull String type) {
        if (shortOffset == longOffset) {
            return longOffset;
        }

        if ((int) longOffset == shortOffset) {
            // This indicates that the offset requires more than 32 bits; use the extended version
            return longOffset;
        }

        if (longOffset != 0 && log.isDebugEnabled()) {
            // The difference is not due to the 32 bit limitation in the game header
            // or because the extended game header didn't contain an offset
            // Only log as debug as this is a non-critical error
            log.debug(String.format("The %s offset differs between the two header databases (%d != %d)", type, shortOffset, longOffset));
        }
        // Use the default header offset, this is what ChessBase does
        return shortOffset;
    }

    public @NotNull GameModel getModel() throws MorphyException {
        return database.gameAdapter().getGameModel(this);
    }

    public @NotNull TextModel getTextModel() throws MorphyException {
        return database.gameAdapter().getTextModel(this);
    }

    public @NotNull GameHeaderModel getGameHeaderModel() {
        return database.gameAdapter().getGameHeaderModel(this);
    }

    public @NotNull String getTextTitle() {
        return TextContentsModel.deserializeTitle(id(), getMovesBlob());
    }

    public @NotNull ByteBuffer getMovesBlob() {
        return database.moveRepository().getMovesBlob(getMovesOffset());
    }

    public @NotNull ByteBuffer getAnnotationsBlob() {
        return database.annotationRepository().getAnnotationsBlob(getAnnotationOffset());
    }

    public int getMovesBlobSize() {
        return database.moveRepository().getMovesBlobSize(getMovesOffset());
    }

    public int getAnnotationsBlobSize() {
        long offset = getAnnotationOffset();
        return offset == 0 ? 0 : database.annotationRepository().getAnnotationsBlobSize(offset);
    }
}
