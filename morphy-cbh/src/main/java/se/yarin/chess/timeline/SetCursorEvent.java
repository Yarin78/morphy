package se.yarin.chess.timeline;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.NavigableGameModel;

import java.util.List;

/**
 * Event that changes the current position in the game model.
 * The position is specified an index in the game tree traversed depth first,
 * where 0 is the start position of the game tree.
 */
public class SetCursorEvent extends GameEvent {
    private int index;

    public SetCursorEvent(int index) {
        this.index = index;
    }

    @Override
    public void apply(@NotNull NavigableGameModel model) throws GameEventException {
        List<GameMovesModel.Node> allNodes = model.moves().getAllNodes();
        if (index < 0 || index >= allNodes.size()) {
            throw new GameEventException(this, model,
                    String.format("Tried to select move with index %d but there are only %d nodes",
                            index, allNodes.size()));
        }
        model.setCursor(allNodes.get(index));
    }

    @Override
    public String toString() {
        return "SetCursorEvent{" +
                "index=" + index +
                '}';
    }
}
