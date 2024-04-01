package se.yarin.morphy.queries.joins;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.queries.GameQueryJoinCondition;


public class GamePlayerFilterJoin extends GameEntityFilterJoin<Player> {
    private final @NotNull GameQueryJoinCondition joinCondition;

    public GamePlayerFilterJoin(@NotNull GameQueryJoinCondition joinCondition, @NotNull EntityFilter<Player> entityFilter) {
        super(entityFilter);
        this.joinCondition = joinCondition;
    }

    @Override
    public boolean gameFilter(@NotNull Game game, @NotNull EntityIndexReadTransaction<Player> txn) {
        int whiteId = game.whitePlayerId();
        int blackId = game.blackPlayerId();

        if (whiteId < 0 || blackId < 0) {
            // Happens if game is a text
            return false;
        }

        EntityFilter<Player> entityFilter = getEntityFilter();

        Player whitePlayer = txn.get(whiteId, entityFilter);
        Player blackPlayer = txn.get(blackId, entityFilter);

        boolean whiteMatch = whitePlayer != null && entityFilter.matches(whitePlayer);
        boolean blackMatch = blackPlayer != null && entityFilter.matches(blackPlayer);

        switch (joinCondition) {
            case ANY -> {
                return whiteMatch || blackMatch;
            }
            case BOTH -> {
                return whiteMatch && blackMatch;
            }
            case WHITE -> {
                return whiteMatch;
            }
            case BLACK -> {
                return blackMatch;
            }
            case WINNER -> {
                switch (game.result()) {
                    case WHITE_WINS, WHITE_WINS_ON_FORFEIT -> {
                        return whiteMatch;
                    }
                    case BLACK_WINS, BLACK_WINS_ON_FORFEIT -> {
                        return blackMatch;
                    }
                    case DRAW, BOTH_LOST, NOT_FINISHED, DRAW_ON_FORFEIT -> {
                        return false;
                    }
                }
            }
            case LOSER -> {
                switch (game.result()) {
                    case WHITE_WINS, WHITE_WINS_ON_FORFEIT -> {
                        return blackMatch;
                    }
                    case BLACK_WINS, BLACK_WINS_ON_FORFEIT -> {
                        return whiteMatch;
                    }
                    case DRAW, NOT_FINISHED, DRAW_ON_FORFEIT -> {
                        return false;
                    }
                    case BOTH_LOST -> { return whiteMatch || blackMatch; }
                }
            }
        }
        return false;
    }
}
