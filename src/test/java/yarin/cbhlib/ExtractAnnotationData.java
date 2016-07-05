package yarin.cbhlib;

import yarin.cbhlib.exceptions.CBHException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ExtractAnnotationData {
    public static void main(String[] args) throws IOException, CBHException {
        Database db = Database.open("src/test/java/yarin/cbhlib/databases/cbhlib_test.cbh");
        for (int i = 0; i < db.getNumberOfGames(); i++) {
            GameHeader gameHeader = db.getGameHeader(i + 1);
            ByteBuffer annotationData = gameHeader.getAnnotationData();

            FileOutputStream stream = new FileOutputStream("/Users/yarin/tmp/annotation" + (i + 1) + ".bin");
            stream.write(annotationData.array());
            stream.close();
        }
    }
}
