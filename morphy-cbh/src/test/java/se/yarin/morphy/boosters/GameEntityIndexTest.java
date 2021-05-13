package se.yarin.morphy.boosters;

import org.junit.Test;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class GameEntityIndexTest {

    private void updateExpected(HashMap<Integer, List<Integer>> map, int gameId, int entityId) {
        if (entityId >= 0) {
            if (map.containsKey(entityId)) {
                map.get(entityId).add(gameId);
            } else {
                ArrayList<Integer> list = new ArrayList<>();
                list.add(gameId);
                map.put(entityId, list);
            }
        }
    }

    @Test
    public void testReading() {
        Database db = ResourceLoader.openWorldChDatabase();

        HashMap<Integer, List<Integer>> expectedPlayerGameIds = new HashMap<>();
        HashMap<Integer, List<Integer>> expectedTournamentIds = new HashMap<>();
        HashMap<Integer, List<Integer>> expectedAnnotatorIds = new HashMap<>();
        HashMap<Integer, List<Integer>> expectedSourceIds = new HashMap<>();
        HashMap<Integer, List<Integer>> expectedTeamIds = new HashMap<>();
        HashMap<Integer, List<Integer>> expectedGameTagIds = new HashMap<>();

        try (var txn = new DatabaseReadTransaction(db)) {
            for (Game game : txn.iterable()) {
                updateExpected(expectedPlayerGameIds, game.id(), game.whitePlayerId());
                updateExpected(expectedPlayerGameIds, game.id(), game.blackPlayerId());
                updateExpected(expectedTournamentIds, game.id(), game.tournamentId());
                updateExpected(expectedAnnotatorIds, game.id(), game.annotatorId());
                updateExpected(expectedSourceIds, game.id(), game.sourceId());
                updateExpected(expectedTeamIds, game.id(), game.whiteTeamId());
                updateExpected(expectedTeamIds, game.id(), game.blackTeamId());
                updateExpected(expectedGameTagIds, game.id(), game.gameTagId());
            }

            verify(db.gameEntityIndex(), EntityType.PLAYER, txn.playerTransaction(), expectedPlayerGameIds);
            verify(db.gameEntityIndex(), EntityType.TOURNAMENT, txn.tournamentTransaction(), expectedTournamentIds);
            verify(db.gameEntityIndex(), EntityType.ANNOTATOR, txn.annotatorTransaction(), expectedAnnotatorIds);
            verify(db.gameEntityIndex(), EntityType.SOURCE, txn.sourceTransaction(), expectedSourceIds);
            verify(db.gameEntityIndex(), EntityType.TEAM, txn.teamTransaction(), expectedTeamIds);
            verify(db.gameEntityIndex(), EntityType.GAME_TAG, txn.gameTagTransaction(), expectedGameTagIds);
        }
    }

    private void verify(
            GameEntityIndex gameEntityIndex,
            EntityType type,
            EntityIndexReadTransaction<? extends Entity> entityTransaction,
            HashMap<Integer, List<Integer>> expectedGameIds) {
        int count = 0;
        for (Entity entity : entityTransaction.iterable()) {
            List<Integer> actualGameIds = gameEntityIndex.getGameIds(entity.id(), type);
            assertEquals(expectedGameIds.get(entity.id()), actualGameIds);
            count += 1;
        }
        assertEquals(expectedGameIds.size(), count);
    }
}
