package yarin.cbhlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yarin.asflib.ASFScriptCommand;
import yarin.asflib.ASFScriptCommandReader;
import yarin.cbhlib.actions.ApplyActionException;
import yarin.cbhlib.actions.RecordedAction;
import yarin.cbhlib.exceptions.CBMException;
import yarin.chess.GameMetaData;
import yarin.chess.GameModel;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.Stack;
import java.util.TreeMap;

/**
 * Represents a recorded chess game with different positions and actions at different points in time
 */
public class RecordedGame {
    private static final Logger log = LoggerFactory.getLogger(RecordedGame.class);

    // TODO: Different parsers depending on version of the media
    private ChessBaseMediaParser parser = new ChessBaseMediaParser();

    private static String formatMillis(int millis) {
        return String.format("%d:%02d", millis/1000/60, millis/1000%60);
    }

    private class Event {
        private int timestamp;
        private Object data;

        Event(int timestamp, Object data) {
            this.timestamp = timestamp;
            this.data = data;
        }
    }

    private TreeMap<Integer, Event> events = new TreeMap<>();

    public static RecordedGame load(InputStream inputStream, boolean tryParse) throws CBMException {
        RecordedGame game = new RecordedGame();
        try {
            ASFScriptCommandReader reader = new ASFScriptCommandReader(inputStream);
            int cnt = reader.init(), lastMillis = -1;
            log.debug("Found " + cnt + " commands");
            while (reader.hasMore()) {
                ASFScriptCommand cmd = reader.read();
                log.debug(cmd.getType() + " command at " + formatMillis(cmd.getMillis()));
                if (!cmd.getType().equals("TEXT")) {
                    throw new CBMException("Unsupported command type: " + cmd.getType());
                }

                // The commands are delayed with 5 seconds for some reason
                int millis = cmd.getMillis() - 5000;
                if (millis == lastMillis) {
                    // If multiple actions occur on the same timestamp, put them explicitly
                    // after each other to avoid actions being reordered in the TreeMap
                    // TODO: Would be nicer to do this more properly somehow
                    millis = game.getLastEventTime() + 1;
                } else {
                    lastMillis = millis;
                }
                if (tryParse) {
                    RecordedAction action = game.parser.parseTextCommand(cmd.getCommand());
                    log.info(String.format("%s at %s", action.toString(), formatMillis(millis)));
                }
                game.addEvent(millis, cmd.getCommand());
            }
            return game;
        } catch (IOException e) {
            throw new CBMException("Error reading ASF commands", e);
        }
    }

    public static RecordedGame load(File file, boolean tryParse) throws CBMException, FileNotFoundException {
        return load(new FileInputStream(file), tryParse);
    }

    public GameModel getGameModelAt(int millis) {
        try {
            log.debug("Getting game model at " + formatMillis(millis));
            // Find the most recent full update and apply all actions that have happened since then
            NavigableMap<Integer, Event> map = events.headMap(millis, true).descendingMap();
            Stack<RecordedAction> actionStack = new Stack<>();
            for (Event event : map.values()) {
                RecordedAction action = parser.parseTextCommand((String) event.data);
                actionStack.add(action);
                if (action.isFullUpdate()) break;
            }
            GameModel current = new GameModel(new AnnotatedGame(), new GameMetaData(), null);
            while (actionStack.size() > 0) {
                RecordedAction action = actionStack.pop();
                log.debug("Apply action " + action.getClass().getSimpleName());
                try {
                    action.apply(current);
                } catch (ApplyActionException e) {
                    log.warn(String.format("Failed to apply action %s: %s", e.toString(), e.getMessage()));
                }
            }
            return current;
        } catch (CBMException e) {
            log.warn("Failed to decode game model at " + millis);
            return new GameModel();
        }
    }

    public int applyActionsBetween(GameModel model, int start, int stop) {
        // start is exclusive, stop is inclusive
        int noActions = 0;
        SortedMap<Integer, Event> map = events.tailMap(start);
        for (Event event : map.values()) {
            if (event.timestamp <= start) continue;
            if (event.timestamp > stop) break;
            try {
                RecordedAction action = parser.parseTextCommand((String) event.data);
//                log.info(String.format("Applying action %s at %s", action.toString(), formatMillis(event.timestamp)));
                action.apply(model);
                noActions++;
            } catch (ApplyActionException e) {
                log.warn(String.format("Failed to apply action %s at %s: %s",
                        e.getAction().toString(), formatMillis(event.timestamp), e.getMessage()));
            } catch (CBMException e) {
                log.warn("Failed to parse action at " + event.timestamp, e);
            }
        }
        return noActions;
    }

    public int getLastEventTime() {
        if (events.size() == 0) return 0;
        return events.lastKey();
    }

    public void addEvent(int millis, Object data) {
        if (events.size() > 0 && millis < events.lastKey()) {
            throw new IllegalArgumentException("Can only add events to the end of the event list");
        }
        events.put(millis, new Event(millis, data));
    }
}
