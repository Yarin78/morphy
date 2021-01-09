package se.yarin.morphy.tools;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Random;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

public class FileChannelPerformanceTest {
    public static void main(String[] args) throws IOException {
        try (FileChannel channel = FileChannel.open(new File("/Users/yarin/src/morphy/bases/Mega2021/Mega Database 2021.cbg").toPath(), READ, WRITE)) {
            int pos = 0, size = (int) channel.size(), games = 0;
            Random random = new Random(0);
            //size = 1000000;

            long start = System.currentTimeMillis();
            while (pos < size) {
                int gameSize = 16384; // 50000 + random.nextInt(100);
                if (pos + gameSize >= size) {
                    gameSize = size - pos;
                }
                ByteBuffer buf = ByteBuffer.allocate(gameSize);
                channel.read(buf);
                pos += gameSize;
                games += 1;
            }
            long elapsed = System.currentTimeMillis() - start;
            // 9009134 games in 7885 ms (~100)
            // 177523 games in 495 ms (~5,000)
            // 54713 games in 346 ms (16384)
            // 17911 games in 322 ms (~50,000)
            // 1793 games in 311 ms (~500,000)
            // 180 games in 646 ms (~5,000,000)
            System.out.printf("%d games in %d ms%n", games, elapsed);
        }
    }
}
