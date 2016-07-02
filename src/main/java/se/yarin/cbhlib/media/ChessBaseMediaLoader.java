package se.yarin.cbhlib.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.timeline.GameEvent;
import se.yarin.chess.timeline.NavigableGameModelTimeline;
import yarin.asflib.ASFScriptCommand;
import yarin.asflib.ASFScriptCommandReader;

import java.io.*;

/**
 * Class containing functionality for loading a ChessBase Media file
 * and extract the metadata and produce a {@link se.yarin.chess.timeline.NavigableGameModelTimeline}.
 * Currently only supports the old format of storing the metadata as
 * ASF Script Commands of type TEXT.
 */
public final class ChessBaseMediaLoader {
    private static final Logger log = LoggerFactory.getLogger(ChessBaseMediaLoader.class);

    private ChessBaseMediaLoader() { }

    /**
     * Opens a ChessBase media file and returns a model of the recorded game,
     * excluding the actual AV contents.
     * @param file the file containing the media file
     * @return a model of the recorded game
     * @throws ChessBaseMediaException thrown if there was an error reading the
     * stream or if the format was unknown
     * @throws FileNotFoundException if the file doesn't exist
     */
    public static NavigableGameModelTimeline openMedia(File file)
            throws ChessBaseMediaException, FileNotFoundException {
        return openMedia(new FileInputStream(file));
    }

    /**
     * Opens a ChessBase media file and returns a model of the recorded game,
     * excluding the actual AV contents.
     * @param inputStream the stream containing the media file
     * @return a model of the recorded game
     * @throws ChessBaseMediaException thrown if there was an error reading the
     * stream or if the format was unknown
     */
    public static NavigableGameModelTimeline openMedia(InputStream inputStream)
            throws ChessBaseMediaException {
        NavigableGameModelTimeline model = new NavigableGameModelTimeline();
        try {
            ASFScriptCommandReader reader = new ASFScriptCommandReader(inputStream);
            int cnt = reader.init(), timestampOffset = -1;
            log.debug("Found " + cnt + " commands");
            while (reader.hasMore()) {
                ASFScriptCommand cmd = reader.read();
                log.debug(cmd.getType() + " command at " + formatMillis(cmd.getMillis()));

                // TODO: Support more formats
                if (cmd.getType().equals("GA")) {
                    // Convert GA command into TEXT command
                    String s = cmd.getCommand();
                    int[] bytes = new int[s.length() * 6 / 8];
                    for (int i = 0, p = 0; i < s.length(); i++) {
                        int b = s.charAt(i) - 63;
                        if (b < 0 || b >= 64) throw new RuntimeException();
                        for (int j = 0; j < 6; j++, p++) {
                            int q = p/8, r = 7-p%8;
                            if (((1<<(5-j)) & b) > 0) {
                                bytes[q] |= (1<<r);
                            }
                        }
                    }
                    // This could be done more efficiently :) Please refactor!
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("000000%02d%02d", bytes.length / 100, bytes.length%100));
                    for (int i = 0; i < bytes.length; i++) {
                        sb.append(String.format("%02X", bytes[i]));
                    }
                    cmd = new ASFScriptCommand(cmd.getMillis(), "TEXT", sb.toString());
                }
                if (!cmd.getType().equals("TEXT")) {
                    throw new ChessBaseMediaException("Unsupported command type: " + cmd.getType());
                }

                // The commands are delayed with 5 seconds for some reason
                // Except in Andrew Martin - The ABC of the Modern Benoni/Modern Benoni.html/CMO GAME FOUR.wmv for some reason!?
                if (timestampOffset < 0) {
                    // Assume that the first command is at the very start of the video, but at most 5 seconds.
                    // This might not be entirely correct?!
                    timestampOffset = Math.min(5000, cmd.getMillis());
                }
                int millis = cmd.getMillis() - timestampOffset;

                try {
                    GameEvent event = ASFTextCommandParser.parseTextCommand(cmd.getCommand());
                    model.addEvent(millis, event);
                } catch (ChessBaseMediaException e) {
                    log.error(String.format("Failed to parse ASF command at %s: %s",
                            formatMillis(millis), e.toString()));
                } catch (IllegalArgumentException e) {
                    log.error(String.format("An ASF command was out of order at %s; ignoring it.",
                            formatMillis(millis)));
                }
            }
        } catch (IOException e) {
            throw new ChessBaseMediaException("Error reading ASF commands", e);
        }
        return model;
    }

    private static String formatMillis(int millis) {
        return String.format("%d:%02d", millis/1000/60, millis/1000%60);
    }
}
