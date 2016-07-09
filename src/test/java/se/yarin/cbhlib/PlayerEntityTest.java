package se.yarin.cbhlib;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static org.junit.Assert.assertEquals;

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
    public void testPlayerBaseStats() throws IOException, BlobStorageException {
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
    public void testDeleteTrail() throws IOException, BlobStorageException {
        URL url = this.getClass().getResource("megadb2016.cbp");
        FileBlobStorage storage = new FileBlobStorage(new File(url.getFile()), 58);
        int current = storage.firstDeletedBlobId();
        int total = 0;
        while (current >= 0) {
            total++;
            System.out.println(current);
            current = storage.nextDeletedBlobId(current);
        }
        System.out.println(total + " deleted entries");
    }

    @Test
    public void testAddBlob() throws IOException, BlobStorageException {
        File cbpFile = materializeStream(this.getClass().getResourceAsStream("jimmy.cbp"));
        PlayerBase playerBase = PlayerBase.open(cbpFile);
//        PlayerBase playerBase = PlayerBase.create(new File("/Users/yarin/tmp/unittest.cbp"));
//        PlayerBase playerBase = PlayerBase.open(new File("/Users/yarin/tmp/unittest.cbp"));
//        System.out.println(playerBase.get(0));
        playerBase.put(new PlayerEntity(-1, "Mardell", "Jimmy", 7, 2));
    }

    @Test
    public void testLoadMegabasePlayer() throws IOException, BlobStorageException {
        URL url = this.getClass().getResource("megadb2016.cbp");
//        URL url = this.getClass().getResource("jimmy.cbp");
        PlayerBase playerBase = PlayerBase.open(new File(url.getFile()));

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
