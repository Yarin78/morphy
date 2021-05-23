package se.yarin.morphy.entities.filters;

import org.junit.Test;
import se.yarin.morphy.Database;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.entities.Tournament;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class TournamentNationFilterTest {
    @Test
    public void testWch() {
        Database db = ResourceLoader.openWorldChDatabase();
        try (var txn = db.tournamentIndex().beginReadTransaction()) {
            HashMap<Nation, Integer> cnt = new HashMap<Nation, Integer>();
            for (Tournament tournament : txn.iterable()) {
                cnt.compute(tournament.nation(), (nation, integer) -> integer == null ? 1 : integer + 1);
            }
            for (Map.Entry<Nation, Integer> entry : cnt.entrySet()) {
                int actual = (int) txn.stream(new TournamentNationFilter(Set.of(entry.getKey()))).count();
                assertEquals((int) entry.getValue(), actual);
            }
        }
    }
}
