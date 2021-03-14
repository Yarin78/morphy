package se.yarin.morphy.entities;

import org.junit.Test;
import se.yarin.chess.Date;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class TournamentExtraTest {

    private final Random random = new Random();

    private TournamentExtra createItem(double latitude, double longitude, Date endDate) {
        return ImmutableTournamentExtra.builder()
                .latitude(latitude)
                .longitude(longitude)
                .endDate(endDate)
                .build();
    }

    private TournamentExtra createDummyItem() {
        return createItem(random.nextDouble(), random.nextDouble(),
                new Date(2020, random.nextInt(12) + 1, random.nextInt(28) + 1));
    }

    @Test
    public void createEmptyStorage() {
        TournamentExtraStorage storage = new TournamentExtraStorage();
        assertEquals(0, storage.numEntries());
    }

    @Test
    public void addAndRetrieveItems() {
        TournamentExtraStorage storage = new TournamentExtraStorage();
        TournamentExtra d1 = createDummyItem();
        storage.put(0, d1);
        assertEquals(1, storage.numEntries());
        TournamentExtra d2 = createDummyItem();
        storage.put(1, d2);
        assertEquals(2, storage.numEntries());

        TournamentExtra e1 = storage.get(0);
        assertEquals(d1, e1);
        TournamentExtra e2 = storage.get(1);
        assertEquals(d2, e2);
    }

    @Test
    public void addItemBeyondLastIndex() {
        TournamentExtraStorage storage = new TournamentExtraStorage();
        TournamentExtra d1 = createDummyItem();
        storage.put(0, d1);
        assertEquals(1, storage.numEntries());
        TournamentExtra d2 = createDummyItem();
        storage.put(7, d2);
        assertEquals(8, storage.numEntries());

        TournamentExtra e1 = storage.get(0);
        assertEquals(d1, e1);
        TournamentExtra ee = storage.get(3);
        assertEquals(TournamentExtra.empty(), ee);
        TournamentExtra e2 = storage.get(7);
        assertEquals(d2, e2);
    }

    @Test
    public void getItemBeyondLastIndex() {
        TournamentExtraStorage storage = new TournamentExtraStorage();

        assertEquals(0, storage.numEntries());
        assertEquals(TournamentExtra.empty(), storage.get(5));
        assertEquals(0, storage.numEntries());  // Ensure no fake entries was added on read

        TournamentExtra d1 = createDummyItem();
        storage.put(0, d1);

        // Still works after one item was added
        assertEquals(1, storage.numEntries());
        assertEquals(TournamentExtra.empty(), storage.get(5));
        assertEquals(1, storage.numEntries());  // Ensure no fake entries was added on read
    }
}
