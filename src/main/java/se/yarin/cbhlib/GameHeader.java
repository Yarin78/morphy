package se.yarin.cbhlib;

import lombok.*;
import se.yarin.chess.Date;
import se.yarin.chess.Eco;
import se.yarin.chess.GameResult;
import se.yarin.chess.LineEvaluation;

import java.util.EnumSet;

/**
 * A game header record in the CBH file. This class is immutable.
 */
@Builder(toBuilder = true)
@Data
@EqualsAndHashCode
public class GameHeader {
    private final int id;
    private final boolean game; // This one is always true
    private final boolean deleted; // If true, game has been marked as deleted but no physical deletion has been done yet
    private final boolean guidingText; // If true, this is a text document and not a chess game
    private final int movesOffset; // Position in the .cbg file where the actual moves are stored
    private final int annotationOffset; // Position in the .cba file where the actual annotation start (0 = no annotations)
    private final int whitePlayerId;
    private final int blackPlayerId;
    private final int tournamentId;
    private final int annotatorId;
    private final int sourceId;
    private final @NonNull Date playedDate;
    private final @NonNull GameResult result;
    private final int round; // 0 = not set
    private final int subRound; // 0 = not set
    private final int whiteElo; // 0 = not set
    private final int blackElo; // 0 = not set
    private final int chess960StartPosition; // According to the official Chess960 numbering scheme, -1 if regular chess
    private final @NonNull Eco eco;
    private final @NonNull LineEvaluation lineEvaluation;

    // The GameHeader may contain medals that's not represented in the moves data
    // But if the moves data contain a medal annotation, that will be reflected in the header medals field
    private final @NonNull EnumSet<Medal> medals;
    private final @NonNull EnumSet<GameHeaderFlags> flags;

    // Contains info about the quantity of annotation, variations etc
    // TODO: These ranges needs to be double checked
    private final int variationsMagnitude; // 1 = [1,50], 2 = [51,300], 3 = [301, 1000], 4 = [1001,]
    private final int commentariesMagnitude; // 1 = [1,200], 2 = [201,] (number of characters in the text annotations)
    private final int symbolsMagnitude; // 1 = [1,9], 2 = [10,]
    private final int graphicalSquaresMagnitude; // 1 = [1,9], 2 = [10,]
    private final int graphicalArrowsMagnitude; // 1 = [1,5], 2 = [6,]
    private final int trainingMagnitude; // 1 = [1, 10], 2 = [11,]
    private final int timeSpentMagnitude; // 1 = [1, 10], 2 = [11,]

    private final int noMoves; // -1 = More than 255 moves. Count the exact number upon demand.

    static GameHeader.GameHeaderBuilder defaultBuilder() {
        return builder()
                .playedDate(new Date(0))
                .result(GameResult.NOT_FINISHED)
                .eco(Eco.unset())
                .lineEvaluation(LineEvaluation.NO_EVALUATION)
                .medals(EnumSet.noneOf(Medal.class))
                .flags(EnumSet.noneOf(GameHeaderFlags.class));
    }
}
