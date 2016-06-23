package se.yarin.cbhlib.media;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.timeline.GameEvent;
import se.yarin.chess.timeline.GameEventException;
import se.yarin.chess.timeline.NavigableGameModelTimeline;
import se.yarin.chess.timeline.NullEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BenchmarkMediaParser {
    private static final Logger log = LoggerFactory.getLogger(BenchmarkMediaParser.class);

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
                    NavigableGameModelTimeline model = ChessBaseMediaLoader.openMedia(filePath.toFile());

                    while (model.getNextEventTimestamp() < Integer.MAX_VALUE) {
                        int tm = model.getNextEventTimestamp() / 1000;
                        GameEvent nextEvent = model.getNextEvent();
                        String millisTime = String.format("%d:%02d", tm / 60, tm % 60);

                        report.noActions++;
                        if (nextEvent instanceof NullEvent)
                            log.info(String.format("Applying event %s at %s", nextEvent.toString(), millisTime));
                        try {
                            model.applyNextEvent();
                        } catch (GameEventException e) {
                            String msg = String.format("Error applying event %s at %s: %s", nextEvent.toString(), millisTime, e.getMessage());
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
                } catch (ChessBaseMediaException e) {
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
