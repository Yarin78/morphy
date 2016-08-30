package se.yarin.cbhlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.GameModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Loads all game headers for all bases found in a directory and outputs simple metadata
 */
public class ScanAllGameHeaders {
    private static final Logger log = LoggerFactory.getLogger(ScanAllGameHeaders.class);

//    public static void main(String[] args) throws IOException, ChessBaseException {
//        Database db = Database.open(new File("testbases/Mega Database 2016/Mega Database 2016.cbh"));
//        new EntityStatsValidator(db).validateMovesAndAnnotationOffsets();
//    }

    public static void main(String[] args) throws IOException {
//        Files.walk(Paths.get("testbases/tmp/Move data fragmentation/annotest.cbh")).forEach(filePath -> {
//        Files.walk(Paths.get("testbases/tmp/Weird Move Encodings/gen3.cbh")).forEach(filePath -> {
        Files.walk(Paths.get("testbases/tmp/Weird Move Encodings/WeirdEncodings.cbh")).forEach(filePath -> {
//        Files.walk(Paths.get("testbases/Mega Database 2016")).forEach(filePath -> {
            if (Files.isRegularFile(filePath) && filePath.toString().endsWith(".cbh")) {
                log.info("Reading " + filePath);
                Database base = null;
                try {
                    base = Database.open(filePath.toFile());
                    GameHeaderBase headerBase = base.getHeaderBase();
//                    for (int i = 1; i <= headerBase.size(); i++) {
                    for (int i = 18; i <= 18; i++) {
                        try {
                            GameHeader gameHeader = headerBase.getGameHeader(i);

                            GameModel gameModel = base.getGameModel(i);
                            System.out.println(gameModel.moves());
                        } catch (ChessBaseException e) {
                            e.printStackTrace();
                        }
                        /*
                        if (gameHeader.getAnnotationOffset() != 0) {
                            log.info(String.format("#%d: moves offset = %d, annotation offset = %d",
                                    i, gameHeader.getMovesOffset(), gameHeader.getAnnotationOffset()));
                        }*/


                    }
                } catch (IOException e) {
                    log.error("IO error reading " + filePath, e);
                } finally {
                    if (base != null) {
                        try {
                            base.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }
}
