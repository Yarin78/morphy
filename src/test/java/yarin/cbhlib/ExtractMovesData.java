package yarin.cbhlib;

import yarin.cbhlib.exceptions.CBHException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class ExtractMovesData {

    public static void main(String[] args) throws IOException, CBHException {
        Database db = Database.open("src/test/java/yarin/cbhlib/databases/cbhlib_test.cbh");
        for (int i = 0; i < db.getNumberOfGames(); i++) {
            GameHeader gameHeader = db.getGameHeader(i + 1);
            int gameDataPosition = gameHeader.getGameDataPosition();

            ByteBuffer buf = ByteBuffer.allocate(4);
            FileChannel fc = db.getFileChannel("cbg");
            fc.read(buf, gameDataPosition);
            int bufferSize = buf.getInt(0) & 0x3FFFFFFF;

            buf = ByteBuffer.allocate(bufferSize);
            fc.read(buf, gameDataPosition);
            buf.position(0);

            FileOutputStream stream = new FileOutputStream("/Users/yarin/tmp/gamemoves" + (i + 1) + ".bin");
            stream.write(buf.array());
            stream.close();
        }
    }
}
