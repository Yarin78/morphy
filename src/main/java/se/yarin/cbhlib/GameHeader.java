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
//@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GameHeader {
    @Getter
    private final int id;
    private final boolean game;
    private final boolean deleted; // If true, game has been marked as deleted but no physical deletion has been done yet
    private final boolean guidingText; // If true, this is a text document and not a chess game
    private final int movesOffset; // Position in the .cbg file where the actual moves are stored
    private final int annotationOffset; // Position in the .cba file where the actual annotation start (0 = no yarin.cbhlib.annotations)
    private final int whitePlayerId;
    private final int blackPlayerId;
    private final int tournamentId;
    private final int annotatorId;
    private final int sourceId;
    private final Date playedDate;
    private final GameResult result;
    private final int round; // 0 = not set
    private final int subRound; // 0 = not set
    private final int whiteElo; // 0 = not set
    private final int blackElo; // 0 = not set
    private final Eco eco;
    private final LineEvaluation lineEvaluation;
    private final EnumSet<Medal> medals;
    private final EnumSet<GameHeaderFlags> flags;
    private final int variationsMagnitude; // 1 = [1,50], 2 = [51,300], 3 = [301, 1000], 4 = [1001,]
    private final int commentariesMagnitude; // 1 = [1,?], 2 = [?,]
    private final int symbolsMagnitude; // 1 = [1,?], 2 = [?,]
    private final int graphicalSquaresMagnitude; // 1 = [1,9], 2 = [10,]
    private final int graphicalArrowsMagnitude; // 1 = [1,9], 2 = [10,]
    private final int trainingMagnitude; // 1 = [1, ?], 2 = [?,]
    private final int timeAnnotationsMagnitude; // 1 = [1, ?], 2 = [?,]
    private final int noMoves; // -1 = More than 255 moves. Count the exact number upon demand.
}
