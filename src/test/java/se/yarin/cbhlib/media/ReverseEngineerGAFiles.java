package se.yarin.cbhlib.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.CBUtil;
import se.yarin.chess.NavigableGameModel;
import se.yarin.asflib.ASFScriptCommand;
import se.yarin.asflib.ASFScriptCommandReader;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReverseEngineerGAFiles {

    private static final Logger log = LoggerFactory.getLogger(ReverseEngineerGAFiles.class);

    public static class GameData {
        public String whiteLast, whiteFirst;
        public String blackLast, blackFirst;
    }

    private static Pattern whitePlayerPattern = Pattern.compile("\\[White \"([^\"]*)\"\\]");
    private static Pattern blackPlayerPattern = Pattern.compile("\\[Black \"([^\"]*)\"\\]");


    public static GameData resolveGameData(Path filePath) {
        String dataFile = filePath.getFileName().toString().replace(".wmv", ".txt");
        String fileName = "/Users/yarin/chessbasemedia/medianalysis/" + dataFile;
        Path path = Paths.get(fileName);
        if (!Files.exists(path)) return null;
        GameData data = new GameData();
        try {
            Scanner scanner = new Scanner(path);
            String firstLine = scanner.nextLine();
            if (!firstLine.equals(filePath.toString())) return null;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                Matcher m = whitePlayerPattern.matcher(line);
                if (m.matches()) {
                    String group = m.toMatchResult().group(1);
                    if (group.indexOf(',') < 0) {
                        log.warn("Not full name in " + filePath);
                        return null;
                    }
                    data.whiteLast = group.substring(0, group.indexOf(','));
                    data.whiteFirst = group.substring(group.indexOf(',') + 2);
                }

                m = blackPlayerPattern.matcher(line);
                if (m.matches()) {
                    String group = m.toMatchResult().group(1);
                    if (group.indexOf(',') < 0) {
                        log.warn("Not full name in " + filePath);
                        return null;
                    }
                    data.blackLast = group.substring(0, group.indexOf(','));
                    data.blackFirst = group.substring(group.indexOf(',') + 2);
                }
            }
        } catch (IOException e) {
            log.error("Error reading " + fileName, e);
        }
        return data;
    }

    public static void main(String[] args) throws IOException {
        AtomicInteger cnt = new AtomicInteger();
        final PrintWriter pw = new PrintWriter("/Users/yarin/chessbasemedia/medianalysis/ga.txt");
        Files.walk(Paths.get("/Users/yarin/chessbasemedia/mediafiles/GA")).forEach(filePath -> {
                if (Files.isRegularFile(filePath) && filePath.toString().endsWith(".wmv")) {
                    try {
                        GameData data = resolveGameData(filePath);
                        if (data != null) {
                            pw.println(data.whiteLast);
                            pw.println(data.whiteFirst);
                            pw.println(data.blackLast);
                            pw.println(data.blackFirst);
                            ASFScriptCommandReader reader = new ASFScriptCommandReader(filePath.toFile());
                            reader.init();
                            if (!reader.hasMore()) {
                                log.warn("No ASF commands in " + filePath);
                            } else {
                                ASFScriptCommand cmd = reader.read();
                                if (cmd.getMillis() == 5000) {
                                    showCommand(pw, filePath, cmd);
                                }

                            }
                        }
                    } catch (Exception e) {
                        log.warn("Error reading " + filePath, e);
                    }
//                    if (cnt.incrementAndGet() >= 10) System.exit(0);
                }
            }
        );
        pw.flush();
    }

    private static void showCommand(PrintWriter out, Path filePath, ASFScriptCommand cmd) {
        String s = cmd.getCommand();
        if (!cmd.getType().equals("GA")) {
            System.err.println("Invalid command type in " + filePath + ": " + cmd.getType());
            return;
        }

        int[] bits = new int[s.length() * 6 + 8];
        for (int i = 0, p = 0; i < s.length(); i++) {
            int b = s.charAt(i) - 63;
            if (b < 0 || b >= 64) throw new RuntimeException();
            for (int j = 0; j < 6; j++, p++) {
                if (((1<<(5-j)) & b) > 0) {
                    bits[p] = 1;
                }
            }
        }

        byte[] bytes = new byte[bits.length / 8];
        for (int i = 0; i+7 < bits.length; i+=8) {
            int b = 0;
            for (int j = 0; j < 8; j++) {
                if (bits[i+j] > 0) b+= (1<<(7-j));
            }
            bytes[i/8] = (byte) b;
        }

        ByteBuffer buf = ByteBuffer.wrap(bytes);
        CBUtil.getIntL(buf);
        try {
            NavigableGameModel navigableGameModel = ChessBaseMediaEventParser.parseFullUpdate(buf);
        } catch (ChessBaseMediaException e) {
            out.println("Error parsing: " + e);
        }

        out.println(filePath);

        StringBuilder sb;
        sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02X ", bytes[i]));
        }
        out.println(sb.toString());

        sb = new StringBuilder();
        for (int i = 0; i+7 < bits.length; i+=8) {
            int b = 0;
            for (int j = 0; j < 8; j++) {
                if (bits[i+j] > 0) b+= (1<<(7-j));
            }
            sb.append(String.format("%c ", b < 32 ? '?' : b));
        }
        out.println(sb.toString());
/*
        int start = 16*8, p = 0;
        sb = new StringBuilder();
        for (int i = start; i < bits.length; i++) {
            sb.append(String.format("%d", bits[i]));
            if (++p % 8 == 0) sb.append(' ');
        }
        out.println(sb.toString());
        */
/*
        sb = new StringBuilder();
        for (int i = start; i < bytes.length; i++) {
            for (int j = 0; j < 8; j++) {
                sb.append(String.format("%d", ((bytes[i] & (1<<(7-j))) > 0) ? 1 : 0));
            }
            sb.append(' ');
        }
        out.println(sb.toString());
*/
        out.println();
    }
}
