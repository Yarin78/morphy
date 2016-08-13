package se.yarin.cbhlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Extracts the moves data from all or a specified set of games in a database
 */
public class ExtractMovesData {
    private static final Logger log = LoggerFactory.getLogger(ExtractMovesData.class);

    private static ByteBuffer getMoves(FileChannel movesFiles, int ofs) throws IOException, ChessBaseInvalidDataException {
        movesFiles.position(ofs);
        ByteBuffer buf = ByteBuffer.allocate(4);
        movesFiles.read(buf);
        buf.position(1);
        int size = se.yarin.cbhlib.ByteBufferUtil.getUnsigned24BitB(buf);
        if (size < 0 || size > 100000) throw new RuntimeException("Unreasonable game size: " + size);
        buf = ByteBuffer.allocate(size);
        movesFiles.position(ofs);
        movesFiles.read(buf);
        buf.position(0);
        return buf;
    }

    public static void main(String[] args) throws IOException {
        String fileBase = "testbases/Mega Database 2016/Mega Database 2016";
        File headerFile = new File(fileBase + ".cbh");
        File movesFile = new File(fileBase + ".cbg");
        FileChannel movesChannel = FileChannel.open(movesFile.toPath());
        GameHeaderBase base = null;
        try {
            base = GameHeaderBase.open(headerFile);
//            int start = 1, stop = base.size();
            int start = 2017673, stop = start;
            int gameIds[] = new int[] {2017678,2769933,2870325,3789643,6161154,2017673};

//            for (int gameId = start; gameId <= stop; gameId++) {
            for (int gameId : gameIds) {
                try {
                    GameHeader gameHeader = base.getGameHeader(gameId);
                    ByteBuffer buf = getMoves(movesChannel, gameHeader.getMovesOffset());
                    FileOutputStream stream = new FileOutputStream("/Users/yarin/tmp/gamemoves" + gameId + ".bin");
                    stream.write(buf.array());
                    stream.close();
                } catch (Exception e) {
                    log.error("Error extracting move data from game " + gameId, e);
                }
            }
        } catch (IOException e) {
            log.error("Error opening database", e);
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
}
