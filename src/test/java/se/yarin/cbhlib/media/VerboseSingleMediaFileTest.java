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
//        String base = "/Users/yarin/chessbasemedia/mediafiles/TEXT/";
//        String video = "Jacob Aagaard - Queen's Indian Defence/Queen's Indian Defence.avi/8.wmv";
//        String video = "Alexei Shirov - My Best Games in the Najdorf (only audio)/My best games in the Sicilian Najdorf.html/2.wmv";
//        String video = "Karsten Müller - Chess Endgames 3/68.wmv";
//        String video = "Ari Ziegler - French Defence/2.wmv";
//        String video = "Andrew Martin - The ABC of the Modern Benoni/Modern Benoni.html/CMO GAME FOUR.wmv";
//        String video = "Nigel Davies - The Tarrasch Defence/Tarrasch.html/04_Monacell_Nadanyan new.wmv";
//        String video = "Rustam Kasimdzhanov - Endgames for Experts/Endgame.html/endgame adams-kasim.wmv";
//        String video = "Rustam Kasimdzhanov - Endgames for Experts/Endgame.html/endgame kasim-ghaem.wmv";

//        String base = "/Users/yarin/chessbasemedia/mediafiles/GA/";
//        String video = "Alexei Shirov - My Best Games in the Caro Kann Defence/My best games in the Caro-Kann.html/Shirov-Bologan.wmv";
//        String video = "Alexei Shirov - My Best Games in the Caro Kann Defence/My best games in the Caro-Kann.html/Shirov-Hracek.wmv";

        // This file has an unreasonble number of headers
//        String video = "Alexei Shirov - Guide to the Tkachiev Ruy Lopez/Spanish with black.html/Svidler-Shirov(2).wmv";

//        String video = "Maurice Ashley - The Secret to Chess/17Secret.wmv";
//        String video = "Viswanathan Anand - My Career - Volume 1/10.wmv";

        String base = "/Users/yarin/chessbasemedia/mediafiles/";
//        String video = "HEADER/Simon Williams - Most Amazing Moves/Game 15 Spassky-Fischer/Game 15 Spassky-Fischer000.wmv";
//        String video = "HEADER/Simon Williams - Most Amazing Moves/Game 16 Hickl-Jussupow/Game 16 Hickl-Jussupow000.wmv";
//        String video = "CBM168/168!Start.html/CBM 168 Marin - KI Mar del Plata/02 Mar del Plata000.wmv";
        String video = "CBM168/Festival Biel 2015.html/Biel 2015 round 04 Navara-Wojtaszek.wmv";
//        String video = "CBM168/168!Start.html/CBM 168 Rogozenco - Classical Paulsen-Morphy.wmv";

        try {
            NavigableGameModelTimeline model = ChessBaseMediaLoader.loadMedia(new File(base + video));
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
