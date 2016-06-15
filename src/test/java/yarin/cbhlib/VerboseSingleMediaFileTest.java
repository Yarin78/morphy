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

import java.io.File;
import java.io.IOException;

public class VerboseSingleMediaFileTest {
    private static final Logger log = LoggerFactory.getLogger(VerboseSingleMediaFileTest.class);

    public static void main(String[] args) throws IOException {
        String base = "/Users/yarin/chessbasemedia/mediafiles/TEXT/";
//        String video = "Jacob Aagaard - Queen's Indian Defence/Queen's Indian Defence.avi/8.wmv";
//        String video = "Alexei Shirov - My Best Games in the Najdorf (only audio)/My best games in the Sicilian Najdorf.html/2.wmv";
        String video = "Karsten Müller - Chess Endgames 3/68.wmv";

        try {
            AnnotatedGame game = new AnnotatedGame();
            GameModel gameModel = new GameModel(game, new GameMetaData(), game);
            ASFScriptCommandReader reader = new ASFScriptCommandReader(new File(base + video));
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
                log.info(String.format("Applying action %s at %s", action.toString(), millisTime));
                try {
                    action.apply(gameModel);
                } catch (ApplyActionException e) {
                    String msg = String.format("Error applying action %s at %s: %s", action.toString(), millisTime, e.getMessage());
                    log.warn(msg);
                }
            }
        } catch (IOException e) {
            String msg = "Error reading file";
            System.err.println(msg);
        } catch (CBMException e) {
            String msg = "Error parsing script command " + e;
            System.err.println(msg);
        }

    }
}
