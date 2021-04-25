package se.yarin.morphy.entities;

import org.junit.Test;
import se.yarin.chess.Date;

import static org.junit.Assert.*;

public class TournamentIndexTransactionTest {

    // Since Tournament has an extra file outside the default EntityIndex,
    // we need explicit test that it's updated correctly on changes

    private Tournament testTournament(int startDay) {
        Date startDate = new Date(2021, 3, startDay);
        return ImmutableTournament.builder()
                .title("foobar")
                .date(startDate)
                .build();
    }

    private TournamentExtra testTournamentExtra(int endDay) {
        Date endDate = new Date(2021, 3, endDay);
        return ImmutableTournamentExtra.builder()
                .endDate(endDate)
                .build();
    }

    @Test
    public void testAddTournament() {
        TournamentIndex index = new TournamentIndex();
        TournamentExtraStorage extraStorage = new TournamentExtraStorage();
        TournamentIndexTransaction txn = new TournamentIndexTransaction(index, extraStorage);

        Tournament newTournament = testTournament(1);
        TournamentExtra newTournamentExtra = testTournamentExtra(5);

        int addedTournamentId = txn.addEntity(newTournament, newTournamentExtra);
        assertEquals(0, addedTournamentId);

        // Should be available in transaction
        Tournament addedTournament = txn.get(addedTournamentId);
        TournamentExtra addedTournamentExtra = txn.getExtra(addedTournamentId);
        assertEquals(newTournament.date(), addedTournament.date());
        assertEquals(newTournamentExtra.endDate(), addedTournamentExtra.endDate());

        // But not available in index (yet)
        assertEquals(0, index.count());
        assertEquals(0, extraStorage.numEntries());

        txn.commit();
        // Now it should be in the index
        assertEquals(1, index.count());
        assertEquals(1, extraStorage.numEntries());
    }

    @Test
    public void testPutTournamentById() {
        TournamentIndex index = new TournamentIndex();
        TournamentExtraStorage extraStorage = new TournamentExtraStorage();
        TournamentIndexTransaction txn = new TournamentIndexTransaction(index, extraStorage);

        int id = txn.addEntity(testTournament(1), testTournamentExtra(5));
        assertEquals(0, id);
        txn.commit();

        txn = new TournamentIndexTransaction(index, extraStorage);
        Tournament putTournament = testTournament(1);  // Update with same primary key
        TournamentExtra putTournamentExtra = testTournamentExtra(6);  // Update with same primary key
        txn.putEntityById(0, putTournament, putTournamentExtra);
        txn.commit();

        Tournament updatedTournament = index.get(id);
        TournamentExtra updatedTournamentExtra = extraStorage.get(id);
        assertEquals(updatedTournament.date(), putTournament.date());
        assertEquals(updatedTournamentExtra.endDate(), putTournamentExtra.endDate());

        putTournament = testTournament(2);  // Update with different primary key
        putTournamentExtra = testTournamentExtra(7);  // Update with different primary key
        txn = new TournamentIndexTransaction(index, extraStorage);
        txn.putEntityById(0, putTournament, putTournamentExtra);
        txn.commit();

        updatedTournament = index.get(id);
        updatedTournamentExtra = extraStorage.get(id);
        assertEquals(updatedTournament.date(), putTournament.date());
        assertEquals(updatedTournamentExtra.endDate(), putTournamentExtra.endDate());
    }

    @Test
    public void testPutTournamentByKey() {
        TournamentIndex index = new TournamentIndex();
        TournamentExtraStorage extraStorage = new TournamentExtraStorage();
        TournamentIndexTransaction txn = new TournamentIndexTransaction(index, extraStorage);

        int id = txn.addEntity(testTournament(1), testTournamentExtra(5));
        assertEquals(0, id);
        txn.commit();

        Tournament putTournament = testTournament(1);  // Same start date needed
        TournamentExtra putTournamentExtra = testTournamentExtra(6);  // Same start date needed

        txn = new TournamentIndexTransaction(index, extraStorage);
        int updatedId = txn.putEntityByKey(putTournament, putTournamentExtra);
        assertEquals(id, updatedId);
        txn.commit();

        Tournament updatedTournament = index.get(id);
        TournamentExtra updatedTournamentExtra = extraStorage.get(id);
        assertEquals(updatedTournament.date(), putTournament.date());
        assertEquals(updatedTournamentExtra.endDate(), putTournamentExtra.endDate());
    }

    @Test
    public void testDeleteTournamentById() {
        TournamentIndex index = new TournamentIndex();
        TournamentExtraStorage extraStorage = new TournamentExtraStorage();
        TournamentIndexTransaction txn = new TournamentIndexTransaction(index, extraStorage);

        int id = txn.addEntity(testTournament(1), testTournamentExtra(5));
        assertEquals(0, id);
        txn.commit();

        txn = new TournamentIndexTransaction(index, extraStorage);
        txn.deleteEntity(0);
        txn.commit();

        assertTrue(index.getNode(id).isDeleted());
    }

    @Test
    public void testDeleteTournamentByKey() {
        TournamentIndex index = new TournamentIndex();
        TournamentExtraStorage extraStorage = new TournamentExtraStorage();
        TournamentIndexTransaction txn = new TournamentIndexTransaction(index, extraStorage);

        int id = txn.addEntity(testTournament(1));
        assertEquals(0, id);
        txn.commit();

        txn = new TournamentIndexTransaction(index, extraStorage);
        txn.deleteEntity(testTournament(1));
        txn.commit();

        assertTrue(index.getNode(id).isDeleted());
    }

    @Test
    public void testAddTournamentWithoutExtraData() {
        TournamentIndex index = new TournamentIndex();
        TournamentExtraStorage extraStorage = new TournamentExtraStorage();

        TournamentIndexTransaction txn = new TournamentIndexTransaction(index);
        txn.addEntity(Tournament.of("a", new Date(2021, 3, 1)));
        txn.addEntity(Tournament.of("b", new Date(2021, 3, 1)));
        txn.commit();

        assertEquals(2, index.count());
        assertEquals(0, extraStorage.numEntries());

        txn = new TournamentIndexTransaction(index, extraStorage);
        txn.addEntity(testTournament(1), testTournamentExtra(2));
        txn.commit();

        assertEquals(3, index.count());
        assertEquals(3, extraStorage.numEntries());
    }

    @Test
    public void testRemoveExtraDataFromTournament() {
        TournamentIndex index = new TournamentIndex();
        TournamentExtraStorage extraStorage = new TournamentExtraStorage();

        TournamentIndexTransaction txn = new TournamentIndexTransaction(index, extraStorage);
        txn.addEntity(Tournament.of("a", new Date(2021, 3, 1)));
        txn.addEntity(Tournament.of("b", new Date(2021, 3, 1)));
        txn.addEntity(testTournament(1), testTournamentExtra(2));
        txn.commit();

        assertEquals(3, index.count());
        assertEquals(3, extraStorage.numEntries());

        assertNotEquals(TournamentExtra.empty(), extraStorage.get(2));

        txn = new TournamentIndexTransaction(index, extraStorage);
        int id = txn.putEntityByKey(testTournament(1), TournamentExtra.empty());
        assertEquals(2, id);
        txn.commit();

        assertEquals(TournamentExtra.empty(), extraStorage.get(id));
    }
}
