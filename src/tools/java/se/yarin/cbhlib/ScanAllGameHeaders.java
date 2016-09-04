package se.yarin.cbhlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.Chess960;
import se.yarin.chess.GameModel;
import se.yarin.chess.Position;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.*;

/**
 * Loads all game headers for all bases found in a directory and outputs simple metadata
 */
public class ScanAllGameHeaders {
    private static final Logger log = LoggerFactory.getLogger(ScanAllGameHeaders.class);

    public static void main(String[] args) throws IOException {
//        Files.walk(Paths.get("testbases/tmp/Move data fragmentation/testing.cbh")).forEach(filePath -> {
//        Files.walk(Paths.get("testbases/tmp/Created/random.cbh")).forEach(filePath -> {

        Map<Integer, List<String>> map = new TreeMap<>();

//        Files.walk(Paths.get("testbases/tmp/Texts")).forEach(filePath -> {
        Files.walk(Paths.get("testbases/CHESS LITERATURE 3")).forEach(filePath -> {
//        Files.walk(Paths.get("testbases/Mega Database 2016")).forEach(filePath -> {
            if (Files.isRegularFile(filePath) && filePath.toString().endsWith(".cbh")) {
                log.info("Reading " + filePath);
                Database base = null;
                try {
                    base = Database.open(filePath.toFile());

//                    int value = base.getHeaderBase().getStorage().getMetadata().getStorageHeaderSize();
//                    if (!map.containsKey(value)) map.put(value, new ArrayList<>());
//                    map.get(value).add(filePath.toString());

                    GameHeaderBase headerBase = base.getHeaderBase();

                    FileDynamicBlobStorage storage = base.getMovesBase().getStorage();
                    int lastMoves = 0, lastAnno = 0;
                    for (int i = 1; i <= headerBase.size(); i++) {
                        try {
                            GameHeader gameHeader = headerBase.getGameHeader(i);
                            int movesOffset = gameHeader.getMovesOffset();
                            if (movesOffset <= lastMoves) {
                                log.warn("Move offsets out of order in game "+ i);
                            }
                            lastMoves = movesOffset;

                            int annoOffset = gameHeader.getAnnotationOffset();
                            if (annoOffset != 0) {
                                if (annoOffset <= lastAnno) {
                                    log.warn("Annotation offsets out of order in game "+ i);
                                }
                                lastAnno = annoOffset;
                            }
                        } catch (RuntimeException e) {
                            log.error("Error reading game " + i, e);
                            break;
                        }
                    }

                } catch (NoSuchFileException e) {
                    log.error("File missing " + filePath);
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

        for (Map.Entry<Integer, List<String>> entry : map.entrySet()) {
            System.out.println(String.format("VALUE = %4d  (%04X)", entry.getKey(), entry.getKey()));
            for (String s : entry.getValue()) {
                System.out.println("   " + s);
            }
            System.out.println();
        }
    }
}
