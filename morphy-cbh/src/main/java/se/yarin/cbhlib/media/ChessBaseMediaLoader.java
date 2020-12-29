package se.yarin.cbhlib.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.timeline.GameEvent;
import se.yarin.chess.timeline.NavigableGameModelTimeline;
import se.yarin.asflib.ASFScriptCommand;
import se.yarin.asflib.ASFScriptCommandReader;

import java.io.*;
import java.nio.ByteBuffer;
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

    private static int[] hexToDec = new int[128];

    static {
        for (int i = 0; i < 10; i++) {
            hexToDec['0' + i] = i;
        }
        for (int i = 0; i < 6; i++) {
            hexToDec['A' + i] = 10 + i;
            hexToDec['a' + i] = 10 + i;
        }
    }

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
            // Probably just assuming that the TXT file will contain commands is wrong.
            // The lack of commands in the ASF file is a good indicator, although
            // for protected streams there seems to be both.
            return loadfromTXT(new FileInputStream(commandFile));
        }
        return loadfromASF(new FileInputStream(file));
    }

    /**
     * Opens a ChessBase media file containing ASF script commands and returns a
     * model of the recorded game, excluding the actual AV contents.
     * @param inputStream the stream containing the media file
     * @return a model of the recorded game
     * @throws ChessBaseMediaException thrown if there was an error reading the
     * stream or if the format was unknown
     */
    public static NavigableGameModelTimeline loadfromASF(InputStream inputStream)
            throws ChessBaseMediaException {
        NavigableGameModelTimeline model = new NavigableGameModelTimeline();
        try {
            ASFScriptCommandReader reader = new ASFScriptCommandReader(inputStream);
            int cnt = reader.init(), timestampOffset = -1;
            log.debug("Found " + cnt + " commands");
            while (reader.hasMore()) {
                ASFScriptCommand cmd = reader.read();
                log.debug(cmd.getType() + " command at " + formatMillis(cmd.getMillis()));

                // The commands are delayed with 5 seconds for some reason
                // Except in Andrew Martin - The ABC of the Modern Benoni/Modern Benoni.html/CMO GAME FOUR.wmv for some reason!?
                if (timestampOffset < 0) {
                    // Assume that the first command is at the very start of the video, but at most 5 seconds.
                    // This might not be entirely correct?!
                    timestampOffset = Math.min(5000, cmd.getMillis());
                }
                int millis = cmd.getMillis() - timestampOffset;

                try {
                    GameEvent event = parseCommand(cmd.getType(), cmd.getCommand());
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
    public static NavigableGameModelTimeline loadfromTXT(InputStream inputStream)
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

    private static GameEvent parseCommand(String type, String text) throws ChessBaseMediaException {
        if (type.equals("GA")) {
            // The GA type stores 6 bits of data per character in the text stream
            // using ASCII characters [63, 126] inclusive
            byte[] bytes = new byte[text.length() * 6 / 8];
            for (int i = 0, p = 0, q = 128; i < text.length(); i++) {
                int b = text.charAt(i) - 63;
                if (b < 0 || b >= 64) {
                    throw new ChessBaseMediaException("Invalid byte in GA command: " + b);
                }
                for (int j = 32; j > 0; j >>= 1) {
                    if ((j & b) > 0) bytes[p] += q;
                    if ((q >>= 1) == 0) {
                        p++;
                        q=128;
                    }

                }
            }
            return ChessBaseMediaEventParser.parseChessMediaEvent(ByteBuffer.wrap(bytes));
        }

        if (type.equals("TEXT")) {
            // The TEXT type stores 4 bits of data per character in the text stream using hexadecimal digits.
            // It also contains a 6 byte header containing the length of the rest of the data.
            // This is the old ChessBase media format which is no longer used.
            if (text.length() % 2 != 0 || text.length() < 10) {
                throw new ChessBaseMediaException("Invalid format of ChessBase script command");
            }

            byte[] bytes = new byte[text.length() / 2];
            for (int i = 0, j = 0; i < text.length(); i += 2, j++) {
                try {
                    // Parse a hexadecimal byte
                    bytes[j] = (byte) (hexToDec[text.charAt(i)] * 16 + hexToDec[text.charAt(i+1)]);
                } catch (NumberFormatException e) {
                    throw new ChessBaseMediaException("Invalid format of ChessBase script command", e);
                }
            }

            // The 5 first bytes stores the length using binary coded decimals
            int len = 0;
            for (int i = 0; i < 5; i++) {
                int bcdDigits = bytes[i];
                if (bcdDigits < 0) bcdDigits += 256;
                int msbDigit = bcdDigits/16;
                int lsbDigit = bcdDigits%16;
                len = (len * 10 + msbDigit) * 10 + lsbDigit;
            }
            int bytesLeft = bytes.length - 5;
            if (bytesLeft != len) {
                String msg = String.format("Number of bytes left in buffer (%d) didn't match specified length (%d)",
                        bytesLeft, len);
                log.warn(msg);
                throw new ChessBaseMediaException("Invalid format of ChessBase script command: " + msg);
            }

            ByteBuffer buf = ByteBuffer.wrap(bytes, 5, bytes.length - 5);
            return ChessBaseMediaEventParser.parseChessMediaEvent(buf);
        }

        if (type.equals("HEADER")) {
            // Occurs in Simon Williams - Most Amazing Moves
            // Probably related to protected streams?
            log.warn("Ignoring HEADER command with data " + text);
            return new HeaderEvent();
        }

        log.warn("Unsupported command type " + type + " with data " + text);
        throw new ChessBaseMediaException("Unsupported command type: " + type);
    }

    private static String formatMillis(int millis) {
        return String.format("%d:%02d", millis/1000/60, millis/1000%60);
    }
}
