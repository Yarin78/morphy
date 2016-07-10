package se.yarin.cbhlib;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayerEntityTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File materializeStream(InputStream stream) throws IOException {
        File file = folder.newFile("temp.cbp");
        FileOutputStream fos = new FileOutputStream(file);
        byte[] buf = new byte[0x1000];
        while (true) {
            int r = stream.read(buf);
            if (r == -1) {
                break;
            }
            fos.write(buf, 0, r);
        }
        fos.close();
        return file;
    }

    @Test
    public void testIterateAllPlayers() throws IOException, EntityStorageException {
        File cbpFile = materializeStream(this.getClass().getResourceAsStream("jimmy.cbp"));
        PlayerBase playerBase = PlayerBase.openInMemory(cbpFile);
//        PlayerBase playerBase = PlayerBase.open(cbpFile);
        AtomicInteger total = new AtomicInteger();
        playerBase.getAll().forEach(playerEntity -> {
            System.out.printf("%3d: %s%n", playerEntity.getId(), playerEntity.getFullName());
            total.incrementAndGet();
        });

        System.out.printf("Total: " + total);
    }

    @Test
    public void testPlayerBaseStats() throws IOException, EntityStorageException {
        File cbpFile = materializeStream(this.getClass().getResourceAsStream("jimmy.cbp"));

        PlayerBase playerBase = PlayerBase.openInMemory(cbpFile);
        PlayerEntity current = playerBase.getFirst();
        int total = 0;
        while (current != null) {
            System.out.printf("%3d: %s%n", current.getId(), current.toString());
            current = playerBase.getNext(current);
            total++;
        }
        System.out.println("Total: " + total);

//        assertEquals(10, entityBase.getNumPlayers());
//        assertEquals(1, entityBase.getFirstDeletedPlayerId());
//        assertEquals(9, entityBase.getNumExistingPlayers());
    }

    @Test
    public void testAddBlob() throws IOException, EntityStorageException {
        File cbpFile = materializeStream(this.getClass().getResourceAsStream("jimmy.cbp"));
        PlayerBase playerBase = PlayerBase.open(cbpFile);
//        PlayerBase playerBase = PlayerBase.create(new File("/Users/yarin/tmp/unittest.cbp"));
//        PlayerBase playerBase = PlayerBase.open(new File("/Users/yarin/tmp/unittest.cbp"));
//        System.out.println(playerBase.get(0));
        playerBase.put(new PlayerEntity("Mårdell2", "Jimmy", 7, 2));
    }

    @Test
    public void testLoadMegabasePlayer() throws IOException, EntityStorageException {
        URL url = this.getClass().getResource("megadb2016.cbp");
//        URL url = this.getClass().getResource("jimmy.cbp");
        PlayerBase playerBase = PlayerBase.open(new File(url.getFile()));
        List<PlayerEntity> players = playerBase.getDescendingList(new PlayerEntity("Mardell", "Jimmy", 0, 0), 20);

        for (PlayerEntity current : players) {
            System.out.printf("%3d: %s%n", current.getId(), current.toString());
        }
/*
        PlayerEntity current = playerBase.getFirst();
        int total = 0;
        while (current != null) {
            if (total % 100 == 0) {
                System.out.printf("%3d: %s%n", current.getId(), current.toString());
                System.out.println("Total: " + total);
            }
            current = playerBase.getNext(current);
            total++;
        }
        System.out.println("Total: " + total);
        */
    }

    @Test
    public void testLoadPlayer() throws IOException {
//        PlayerBase entityBase = PlayerBase.open(this.getClass().getResourceAsStream("players.cbp"));
//        PlayerEntity player = entityBase.load(1);
//        assertEquals("Kasparov", player.getLastName());
//        assertEquals("Garry", player.getFirstName());
    }

    @Test
    public void testSavePlayer() {

    }


}
