package yarin.cbhlib;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yarin.asflib.ASFScriptCommand;
import yarin.asflib.ASFScriptCommandReader;
import yarin.cbhlib.actions.AddMoveAction;
import yarin.cbhlib.actions.ApplyActionException;
import yarin.cbhlib.actions.NullAction;
import yarin.cbhlib.actions.RecordedAction;
import yarin.cbhlib.exceptions.CBMException;
import yarin.chess.GameMetaData;
import yarin.chess.GameModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BenchmarkChessBaseMediaFiles {
    private static final Logger log = LoggerFactory.getLogger(BenchmarkChessBaseMediaFiles.class);

    @Data
    private static class MediaFileReport {
        private String file;
        private int noActions;
        private long processTime;
        private String error;

        public MediaFileReport(String file) {
            this.file = file;
        }

        public List<String> failedActionLog = new ArrayList<>();
        public List<String> parsingIssues = new ArrayList<>();
    }

    public static void main(String[] args) throws IOException {
        final AtomicInteger noErrors = new AtomicInteger();
        ArrayList<MediaFileReport> reports = new ArrayList<>();
        Files.walk(Paths.get("/Users/yarin/chessbasemedia/mediafiles/TEXT")).forEach(filePath -> {
            if (Files.isRegularFile(filePath) && filePath.toString().endsWith(".wmv")) {
                MediaFileReport report = new MediaFileReport(filePath.toString());
                try {
                    long start = System.currentTimeMillis();
                    log.info("Reading " + filePath);
                    AnnotatedGame game = new AnnotatedGame();
                    GameModel gameModel = new GameModel(game, new GameMetaData(), game);
                    ASFScriptCommandReader reader = new ASFScriptCommandReader(filePath.toFile());
                    reader.init();
                    while (reader.hasMore()) {
                        ASFScriptCommand cmd = reader.read();
                        ChessBaseMediaParser parser = new ChessBaseMediaParser();
                        if (!cmd.getType().equals("TEXT")) {
                            log.error("Command type " + cmd.getType() + " not yet supported");
                            break;
                        }
                        RecordedAction action = parser.parseTextCommand(cmd.getCommand());
                        String millisTime = String.format("%d:%02d", (cmd.getMillis() - 5000) / 60000, (cmd.getMillis() - 5000) / 1000 % 60);

                        report.noActions++;
                        if (action instanceof NullAction)
                            log.info(String.format("Applying action %s at %s", action.toString(), millisTime));
                        try {
                            action.apply(gameModel);
                        } catch (ApplyActionException e) {
                            String msg = String.format("Error applying action %s at %s: %s",
                                    action.toString(),
                                    millisTime,
                                    e.getMessage());
                            log.warn(msg);
                            report.failedActionLog.add(msg);
                        }
                    }
//                    log.info(String.format("%d actions (%d invalid)", report.noActions, report.failedActionLog.size()));
                    long done = System.currentTimeMillis();
                    report.setProcessTime(done - start);
                } catch (IOException e) {
                    String msg = "Error reading file";
                    System.err.println(msg);
                    report.setError("msg");
                    noErrors.incrementAndGet();
                } catch (CBMException e) {
                    String msg = "Error parsing script command " + filePath + ": " + e;
                    System.err.println(msg);
                    report.setError("msg");
                    noErrors.incrementAndGet();
                } finally {
                    reports.add(report);
                }
            }
        });
        System.err.println("No errors: " + noErrors.get());

        long totTime = 0, slowestTime = 0;
        int cnt = 0;
        String slowest = "";
        for (MediaFileReport report : reports) {
            totTime += report.processTime;
            cnt++;
            // Ugly, but the first files are always slow due to the JIT not being warm
            if (cnt > 20 && report.processTime > slowestTime) {
                slowestTime = report.processTime;
                slowest = report.file;
            }
            if (report.failedActionLog.size() > 0) {
                System.err.println(report.file);
                for (String s : report.failedActionLog) {
                    System.err.println("Failed action: " + s);
                }
            }
        }

        System.err.println(String.format("Total time: %7.3f s", totTime / 1000.0));
        System.err.println(String.format("Avg time:   %7.3f s", totTime / 1000.0 / reports.size()));
        System.err.println(String.format("Slowest:    %7.3f s (%s)", slowestTime / 1000.0, slowest));
    }
}
