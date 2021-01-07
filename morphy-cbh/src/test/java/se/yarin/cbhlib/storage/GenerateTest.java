package se.yarin.cbhlib.storage;

import se.yarin.cbhlib.entities.AnnotatorBase;
import se.yarin.cbhlib.entities.AnnotatorEntity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class GenerateTest {

    private static Random random = new Random(0);

    private static String nextRandomString() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < sb.capacity(); i++) {
            sb.append((char)('a' + random.nextInt(26)));
        }
        return sb.toString();
    }

    public static void main(String[] args) throws IOException {
        AnnotatorBase ab = AnnotatorBase.open(new File("/Users/yarin/chessbasemedia/mediafiles/cbh/generate_test.cbc"));
        for (AnnotatorEntity annotatorEntity : ab.streamOrderedAscending().limit(1000).collect(Collectors.toList())) {
            System.out.println(annotatorEntity);
        }
    }

    public static void main2(String[] args) throws IOException, EntityStorageException {
        AnnotatorBase ab = AnnotatorBase.open(new File("/Users/yarin/chessbasemedia/mediafiles/cbh/generate_test.cbc"));
        ArrayList<String> list = new ArrayList<>();
        for (int ops = 0; ops < 1000; ops++) {
            if (random.nextDouble() < 1-(double) ops/1500 || list.size() == 0) {
                String key = nextRandomString();
                list.add(key);
                ab.add(AnnotatorEntity.builder().firstGameId(1).count(1).name(key).build());
            } else {
                int i = random.nextInt(list.size());
                String key = list.get(i);
                ab.delete(new AnnotatorEntity(key));
                list.remove(i);
            }
        }
        System.out.println(ab.getCount());
        ab.close();
    }
}
