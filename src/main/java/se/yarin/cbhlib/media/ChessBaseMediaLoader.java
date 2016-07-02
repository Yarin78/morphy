package se.yarin.cbhlib.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.timeline.GameEvent;
import se.yarin.chess.timeline.NavigableGameModelTimeline;
import yarin.asflib.ASFScriptCommand;
import yarin.asflib.ASFScriptCommandReader;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class containing functionality for loading a ChessBase Media file
 * and extract the metadata and produce a {@link se.yarin.chess.timeline.NavigableGameModelTimeline}.
 * Currently only supports the old format of storing the metadata as
 * ASF Script Commands of type TEXT.
 */
public final class ChessBaseMediaLoader {
    private static final Logger log = LoggerFactory.getLogger(ChessBaseMediaLoader.class);

    private ChessBaseMediaLoader() { }

    private static File getCommandFile(File file) {
        int ix = file.getPath().lastIndexOf('.');
        if (ix < 0) return null;
        File txtFile = new File(file.getPath().substring(0, ix) + ".txt");
        if (!txtFile.exists()) return null;
        return txtFile;
    }

    /**
     * Opens a ChessBase media file and returns a model of the recorded game,
     * excluding the actual AV contents.
     * @param file the file containing the media file
     * @return a model of the recorded game
     * @throws ChessBaseMediaException thrown if there was an error reading the
     * stream or if the format was unknown
     * @throws FileNotFoundException if the file doesn't exist
     */
    public static NavigableGameModelTimeline loadMedia(File file)
            throws ChessBaseMediaException, FileNotFoundException {
        File commandFile = getCommandFile(file);
        if (commandFile != null) {
            return loadFromTxt(new FileInputStream(commandFile));
        }
        return loadfromAsf(new FileInputStream(file));
    }

    /**
     * Opens a ChessBase media file containing ASF script commands and returns a
     * model of the recorded game, excluding the actual AV contents.
     * @param inputStream the stream containing the media file
     * @return a model of the recorded game
     * @throws ChessBaseMediaException thrown if there was an error reading the
     * stream or if the format was unknown
     */
    public static NavigableGameModelTimeline loadfromAsf(InputStream inputStream)
            throws ChessBaseMediaException {
        NavigableGameModelTimeline model = new NavigableGameModelTimeline();
        try {
            ASFScriptCommandReader reader = new ASFScriptCommandReader(inputStream);
            int cnt = reader.init(), timestampOffset = -1;
            log.debug("Found " + cnt + " commands");
            while (reader.hasMore()) {
                ASFScriptCommand cmd = reader.read();
                log.debug(cmd.getType() + " command at " + formatMillis(cmd.getMillis()));

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
                    log.warn("Unsupported command type " + cmd.getType() + " with data " + cmd.getCommand());
                    continue;
//                    throw new ChessBaseMediaException("Unsupported command type: " + cmd.getType());
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

    /**
     * Opens a ChessBase command file (.txt) containing unencrypted chess command
     * and returns a model of the recorded game.
     * @param inputStream the stream containing the chess commands
     * @return a model of the recorded game
     * @throws ChessBaseMediaException thrown if there was an error reading the
     * stream or if the format was unknown
     */
    public static NavigableGameModelTimeline loadFromTxt(InputStream inputStream)
            throws ChessBaseMediaException {
        NavigableGameModelTimeline model = new NavigableGameModelTimeline();
        Pattern pattern = Pattern.compile("(\\d):(\\d\\d):(\\d\\d)(\\.\\d)?:");
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            int lineNo = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                line = line.trim(); // Sometimes there's a trailing space!
                Matcher matcher = pattern.matcher(line);
                if (!matcher.matches()) {
                    throw new ChessBaseMediaException("Failed to parse timestamp on line " + lineNo + ": " + line);
                }
                int hour = Integer.parseInt(matcher.group(1));
                int minute = Integer.parseInt(matcher.group(2));
                int second = Integer.parseInt(matcher.group(3));
                int tenth = matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4).substring(1));
                int millis = (((hour*60+minute) * 60 + second)*10+tenth)*100;

                lineNo++;
                String cmd = reader.readLine();
                int colon = cmd.indexOf(':');
                if (colon < 0) {
                    throw new ChessBaseMediaException("Failed to parse message on line " + lineNo);
                }
                String type = cmd.substring(0, colon);
                cmd = cmd.substring(colon+1);
                log.debug(type + " command at " + formatMillis(millis));

                try {
                    GameEvent event = parseCommand(type, cmd);
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

    private static GameEvent parseCommand(String type, String cmd) throws ChessBaseMediaException {
        if (type.equals("GA")) {
            // Convert GA command into TEXT command
            String s = cmd;
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
            type = "TEXT";
            cmd = sb.toString();
        }

        if (!type.equals("TEXT")) {
            log.warn("Unsupported command type " + type + " with data " + cmd);
            throw new ChessBaseMediaException("Unsupported command type: " + type);
        }

        return ASFTextCommandParser.parseTextCommand(cmd);
    }

    private static String formatMillis(int millis) {
        return String.format("%d:%02d", millis/1000/60, millis/1000%60);
    }
}
