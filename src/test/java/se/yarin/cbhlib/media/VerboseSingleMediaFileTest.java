package se.yarin.cbhlib.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.timeline.GameEvent;
import se.yarin.chess.timeline.GameEventException;
import se.yarin.chess.timeline.NavigableGameModelTimeline;

import java.io.File;
import java.io.IOException;

public class VerboseSingleMediaFileTest {
    private static final Logger log = LoggerFactory.getLogger(VerboseSingleMediaFileTest.class);

    public static void main(String[] args) throws IOException {
        String base = "/Users/yarin/chessbasemedia/mediafiles/TEXT/";
//        String video = "Jacob Aagaard - Queen's Indian Defence/Queen's Indian Defence.avi/8.wmv";
//        String video = "Alexei Shirov - My Best Games in the Najdorf (only audio)/My best games in the Sicilian Najdorf.html/2.wmv";
//        String video = "Karsten Müller - Chess Endgames 3/68.wmv";
//        String video = "Ari Ziegler - French Defence/2.wmv";
        String video = "Andrew Martin - The ABC of the Modern Benoni/Modern Benoni.html/CMO GAME FOUR.wmv";

        try {
            NavigableGameModelTimeline model = ChessBaseMediaLoader.openMedia(new File(base + video));
            while (model.getNextEventTimestamp() < Integer.MAX_VALUE) {
                int tm = model.getNextEventTimestamp() / 1000;
                GameEvent nextEvent = model.getNextEvent();
                String millisTime = String.format("%d:%02d", tm / 60, tm % 60);
                log.info(String.format("Applying event %s at %s", nextEvent.toString(), millisTime));
                try {
                    model.applyNextEvent();
                } catch (GameEventException e) {
                    String msg = String.format("Error applying event %s at %s: %s", nextEvent.toString(), millisTime, e.getMessage());
                    log.warn(msg);
                }
            }
        } catch (IOException e) {
            String msg = "Error reading file";
            System.err.println(msg);
        } catch (ChessBaseMediaException e) {
            String msg = "Error parsing script command " + e;
            System.err.println(msg);
        }

    }

}
