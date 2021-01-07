package se.yarin.morphy.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.yarin.cbhlib.exceptions.ChessBaseInvalidDataException;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.GameHeaderBase;
import se.yarin.cbhlib.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class CheckMoveEncodingFlags {
    private static final Logger log = LogManager.getLogger();

    private static ByteBuffer getMoves(FileChannel movesFiles, int ofs) throws IOException, ChessBaseInvalidDataException {
        movesFiles.position(ofs);
        ByteBuffer buf = ByteBuffer.allocate(4);
        movesFiles.read(buf);
        buf.position(1);
        int size = ByteBufferUtil.getUnsigned24BitB(buf);
        if (size < 0 || size > 100000) throw new RuntimeException("Unreasonable game size: " + size);
        buf = ByteBuffer.allocate(size);
        movesFiles.position(ofs);
        movesFiles.read(buf);
        buf.position(0);
        return buf;
    }

    public static void main(String[] args) throws IOException {
        ArrayList<String> bases = new ArrayList<>();

//        String path = "/Users/yarin/chessbasemedia/mediafiles/cbh/tmp";
        String path = "testbases/Old databases/Dragon for Experts";
//        String path = "testbases/Mega Database 2009";
        Files.walk(Paths.get(path)).forEach(filePath -> {
            if (Files.isRegularFile(filePath) && filePath.toString().toLowerCase().endsWith(".cbh")) {
                bases.add(filePath.toString().substring(0, filePath.toString().length() - 4));
            }
        });

        Map<Integer, List<Integer>> map = new HashMap<>();
        for (String fileBase : bases) {
            map = mergeDistribution(map, getEncodingFlagDistribution(fileBase));
        }

        for (Map.Entry<Integer, List<Integer>> entry : map.entrySet()) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Flag %02X: ", entry.getKey()));
            sb.append(String.format("%d games: ", entry.getValue().size()));
            int cnt = 0;
            for (Integer gameId : entry.getValue()) {
                if (cnt > 0) sb.append(", ");
                sb.append(gameId);
                if (cnt > 20) { sb.append("..."); break; }
                cnt++;
            }
            log.info(sb.toString());
        }
    }

    private static Map<Integer, List<Integer>> mergeDistribution(
            Map<Integer, List<Integer>> a,
            Map<Integer, List<Integer>> b) {
        HashMap<Integer, List<Integer>> map = new HashMap<>(a);
        for (Map.Entry<Integer, List<Integer>> entry : b.entrySet()) {
            if (!map.containsKey(entry.getKey())) map.put(entry.getKey(), new ArrayList<>());
            map.get(entry.getKey()).addAll(entry.getValue());
        }
        return map;
    }

    private static Map<Integer, List<Integer>> getEncodingFlagDistribution(String fileBase) throws IOException {
        File headerFile = new File(fileBase + ".cbh");
        File movesFile = new File(fileBase + ".cbg");
        FileChannel movesChannel = FileChannel.open(movesFile.toPath());
        Map<Integer, List<Integer>> map = new TreeMap<>();

        GameHeaderBase base = null;
        try {
            base = GameHeaderBase.open(headerFile);
            int start = 1, stop = base.size();
//            int start = 2017673, stop = start;

            for (int gameId = start; gameId <= stop; gameId++) {
                try {
                    GameHeader gameHeader = base.getGameHeader(gameId);
                    if (gameHeader.isGuidingText()) {
//                    log.info("Game " + gameId + " is a guiding text, skipping");
                        continue;
                    }
                    ByteBuffer moves = getMoves(movesChannel, gameHeader.getMovesOffset());
                    int flags = ByteBufferUtil.getUnsignedByte(moves);
                    int size = ByteBufferUtil.getSigned24BitB(moves);
                    if (!map.containsKey(flags)) {
                        map.put(flags, new ArrayList<>());
                    }
                    map.get(flags).add(gameId);

                    if (gameId % 100000 == 0) {
                        log.info("Read " + gameId + " games");
                    }
                } catch (Exception e) {
                    log.error("Error parsing data in game " + gameId, e);
                }
            }
        } catch (IOException e) {
            log.error("IO error reading " + headerFile, e);
        } finally {
            if (base != null) {
                try {
                    base.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return map;
    }
}
