package yarin.cbhlib;

import yarin.cbhlib.exceptions.CBHFormatException;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

public class ExtractGameHeaders {
    public static void main(String[] args) throws IOException, CBHFormatException {
        File file = new File("src/test/java/yarin/cbhlib/databases/cbhlib_test.cbh");
        FileChannel channel = FileChannel.open(file.toPath());
        channel.position(0x2E);
        for (int i = 1; i <= 19 ; i++) {
            File outputFile = new File("/Users/yarin/chessbasemedia/mediafiles/gameheaders/gameheader" + i + ".bin");
            FileChannel output = FileChannel.open(outputFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            ByteBuffer buf = ByteBuffer.allocate(0x2E);
            channel.read(buf);
            buf.position(0);
            output.write(buf);
            output.close();
        }
        channel.close();
    }
}
