package se.yarin.cbhlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Loads all game headers for all bases found in a directory and outputs simple metadata
 */
public class ScanAllGameHeaders {
    private static final Logger log = LoggerFactory.getLogger(ScanAllGameHeaders.class);

    public static void main(String[] args) throws IOException {
        Files.walk(Paths.get("testbases/tmp")).forEach(filePath -> {
            if (Files.isRegularFile(filePath) && filePath.toString().endsWith(".cbh")) {
                log.info("Reading " + filePath);
                GameHeaderBase base = null;
                try {
                    base = GameHeaderBase.open(filePath.toFile());
                    for (int i = 1; i <= base.size(); i++) {
                        GameHeader gameHeader = base.getGameHeader(i);
                        log.info(String.format("#%d: moves offset = %d, annotation offset = %d",
                                i, gameHeader.getMovesOffset(), gameHeader.getAnnotationOffset()));
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
