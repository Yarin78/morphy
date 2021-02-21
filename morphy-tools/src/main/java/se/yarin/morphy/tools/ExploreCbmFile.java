package se.yarin.morphy.tools;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.games.search.GameSearcher;
import se.yarin.cbhlib.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ExploreCbmFile {
    public static void main(String[] args) throws IOException {
        File dbFile = new File("/Users/yarin/chess/bases/Mega2021/Mega Database 2021.cbm");
        //File dbFile = new File("/Users/yarin/chess/bases/Mega2009/Mega Database 2009.cbm");
        explore(dbFile);
        /*
        File file = new File("/Users/yarin/chess/bases");
        Stream<File> fileStream = Files.walk(file.toPath(), 20)
                .filter(path -> path.toString().toLowerCase().endsWith(".cbm"))
                .filter(path -> !path.getFileName().toString().startsWith("._"))
                .map(Path::toFile);
        fileStream.forEach(dbFile -> {
            try {
                //System.out.println("Reading " + dbFile);
                explore(dbFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
*/

    }

    private static void explore(File cbmFile) throws IOException {
        // Database db = Database.open(file);
        // File cbmFile = new File(file.getAbsolutePath().replace(".cbh", ".cbm"));
        FileChannel cbmChannel = FileChannel.open(cbmFile.toPath());
        ByteBuffer headerBuf = ByteBuffer.allocate(32);
        cbmChannel.read(headerBuf);
        headerBuf.flip();
        int version = ByteBufferUtil.getIntL(headerBuf);
        int recordSize = ByteBufferUtil.getIntL(headerBuf);
        int numRecords = ByteBufferUtil.getIntL(headerBuf);
        int unknown = ByteBufferUtil.getIntL(headerBuf);
        if (numRecords == 0 && unknown == -1 && version == 1) {
            return;
        }

        /*
        GameSearcher gameSearcher = new GameSearcher(db);
        for (Game game : gameSearcher.iterableSearch()) {
            int cbmOffset = game.getExtendedHeader().getMediaOffset();
            if (cbmOffset > 0) {
                cbmChannel.read()
            }
        }
         */
        HashMap<String, Integer> fileCnt = new HashMap<String, Integer>();
        System.out.println(numRecords + " records in " + cbmFile + " (unknown is " + unknown + ")");
        for (int i = 0; i < numRecords; i++) {
            ByteBuffer buf = ByteBuffer.allocate(8);
            cbmChannel.read(buf);
            buf.flip();
            int v1 = ByteBufferUtil.getIntL(buf);
            int numSlots = ByteBufferUtil.getIntL(buf);
            if (numSlots < 1) {
                throw new IOException("Weird number of slots in record " + i + ": " + numSlots);
            }
            i += numSlots - 1;
            buf = ByteBuffer.allocate(recordSize * numSlots - 8);
            cbmChannel.read(buf);
            buf.flip();
            List<String> filesUsed = readManifest(buf);
            /*
            if (!s.startsWith("FilesUsed") || v1 != -1 || numSlots != 1) {
                System.out.println(String.format("%d %d %d\n%s", i, v1, numSlots, s));
                System.out.println("----");
            }
             */
            for (String s : filesUsed) {
                String htmlPath = cbmFile.getName().replace(".cbm", ".html");
                Path p = Path.of(cbmFile.getParent(), htmlPath, s);
                if (!p.toFile().exists()) {
                    System.out.println("File " + s + " does not exist in cbm record " + i);
                }
                fileCnt.merge(s, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> entry : fileCnt.entrySet()) {
            if (entry.getValue() > 1) {
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }
        }
    }

    private static List<String> readManifest(ByteBuffer buf) {
        StringBuilder res = new StringBuilder();

        String type = ByteBufferUtil.getByteStringLine(buf);
        res.append(type);
        if (!type.trim().equals("FilesUsed")) {
            throw new RuntimeException("Unknown manifest type " + type);
        }
        String numLinesStr = ByteBufferUtil.getByteStringLine(buf);
        res.append(numLinesStr);
        int numLines = Integer.parseInt(numLinesStr.trim());
        ArrayList<String> filesUsed = new ArrayList<>();
        for (int i = 0; i < numLines; i++) {
            String s = ByteBufferUtil.getByteStringLine(buf);
            filesUsed.add(s.trim());
        }
        return filesUsed;
    }
}
