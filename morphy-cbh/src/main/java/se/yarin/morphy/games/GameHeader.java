package se.yarin.morphy.games;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Date;
import se.yarin.chess.Eco;
import se.yarin.chess.GameResult;
import se.yarin.chess.NAG;
import se.yarin.morphy.IdObject;

import java.util.EnumSet;

/**
 * A game header record in the CBH file.
 */
@Value.Immutable
public abstract class GameHeader implements IdObject {
    @Value.Default
    public int id() {
        // This field isn't actually stored in the CBH file, but makes sense to keep it around.
        // If 0, the GameHeader represents a game that's not currently stored in an index.
        return 0;
    }

    @Value.Default
    public boolean deleted() {
        // If true, game has been marked as deleted but no physical deletion has been done yet
        return false;
    }

    @Value.Default
    public boolean guidingText() {
        // If true, this is a text document and not a chess game
        return false;
    }

    @Value.Default
    public int movesOffset() {
        // Position in the .cbg file where the actual moves are stored
        return 0;
    }

    @Value.Default
    public int annotationOffset() {
        // Position in the .cba file where the actual annotation start (0 = no annotations)
        return 0;
    }

    public abstract int whitePlayerId();
    public abstract int blackPlayerId();
    public abstract int tournamentId();
    public abstract int annotatorId();
    public abstract int sourceId();

    public int winningPlayerId() {
        return switch (result()) {
            case WHITE_WINS, WHITE_WINS_ON_FORFEIT -> whitePlayerId();
            case BLACK_WINS, BLACK_WINS_ON_FORFEIT -> blackPlayerId();
            default -> -1;
        };
    }

    public int losingPlayerId() {
        return switch (result()) {
            case WHITE_WINS, WHITE_WINS_ON_FORFEIT -> blackPlayerId();
            case BLACK_WINS, BLACK_WINS_ON_FORFEIT -> whitePlayerId();
            default -> -1;
        };
    }

    @Value.Default
    public @NotNull Date playedDate() {
        return Date.unset();
    }
    @Value.Default
    public @NotNull GameResult result() {
        return GameResult.NOT_FINISHED;
    }

    @Value.Default
    public int round() {
        // 0 = not set
        return 0;
    };

    @Value.Default
    public int subRound() {
        // 0 = not set
        return 0;
    }

    @Value.Default
    public int whiteElo() {
        // 0 = not set
        return 0;
    }

    @Value.Default
    public int blackElo() {
        // 0 = not set
        return 0;
    }

    // According to the official Chess960 numbering scheme, -1 if regular chess
    @Value.Default
    public int chess960StartPosition() {
        return -1;
    }

    @Value.Default
    public @NotNull Eco eco() {
        return Eco.unset();
    };

    @Value.Default
    public @NotNull NAG lineEvaluation() {
        return NAG.NONE;
    }

    // The GameHeader may contain medals that's not represented in the moves data
    // But if the moves data contain a medal annotation, that will be reflected in the header medals field
    @Value.Default
    public @NotNull EnumSet<Medal> medals() {
        return EnumSet.noneOf(Medal.class);
    }

    @Value.Default
    public @NotNull EnumSet<GameHeaderFlags> flags() {
        return EnumSet.noneOf(GameHeaderFlags.class);
    }

    // Contains info about the quantity of annotation, variations etc
    // TODO: These ranges needs to be double checked
    @Value.Default
    public int variationsMagnitude() {
        // 1 = [1,50], 2 = [51,300], 3 = [301, 1000], 4 = [1001,]
        return 0;
    }

    @Value.Default
    public int commentariesMagnitude() {
        // 1 = [1,200], 2 = [201,] (number of characters in the text annotations)
        return 0;
    }

    @Value.Default
    public int symbolsMagnitude() {
        // 1 = [1,9], 2 = [10,]
        return 0;
    }

    @Value.Default
    public int graphicalSquaresMagnitude() {
        // 1 = [1,9], 2 = [10,]
        return 0;
    }

    @Value.Default
    public int graphicalArrowsMagnitude() {
        // 1 = [1,5], 2 = [6,]
        return 0;
    }

    @Value.Default
    public int trainingMagnitude() {
        // 1 = [1, 10], 2 = [11,]
        return 0;
    }

    @Value.Default
    public int timeSpentMagnitude() {
        // 1 = [1, 10], 2 = [11,]
        return 0;
    }

    @Value.Default
    public int noMoves() {
        // -1 = More than 255 moves. Count the exact number upon demand.
        return 0;
    }
}
