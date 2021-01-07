package se.yarin.morphy.tools;

import java.io.*;

public class RipDbHeaders {
    public static void main(String[] args) throws IOException, InterruptedException {
        for (int i = 0; i < 30; i++) {
            Thread.sleep(500);
            String baseDir = "/Users/yarin/src/cbhlib/testbases/tmp/Created/";
            backupHeaders(baseDir + "random", Integer.toString(i), baseDir + "bak/");
        }
    }

    private static void backupHeaders(String base, String prefix, String bakDirectory) throws IOException {
        backupHeader(new File(base + ".cbh"), 46, prefix, bakDirectory);
        backupHeader(new File(base + ".cbg"), 26, prefix, bakDirectory);
    }

    private static void backupHeader(File file, int headerLength, String prefix, String bakDirectory)
            throws IOException {
        byte[] buf = new byte[headerLength];
        FileInputStream fis = new FileInputStream(file);
        fis.read(buf);
        fis.close();
        FileOutputStream fos = new FileOutputStream(bakDirectory + prefix + "_" + file.getName());
        fos.write(buf);
        fos.close();
    }
}
