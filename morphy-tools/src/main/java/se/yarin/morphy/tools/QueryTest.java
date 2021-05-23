package se.yarin.morphy.tools;

import se.yarin.morphy.*;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.entities.filters.AnnotatorNameFilter;
import se.yarin.morphy.entities.filters.PlayerNameFilter;
import se.yarin.morphy.entities.filters.TournamentCategoryFilter;
import se.yarin.morphy.entities.filters.TournamentTitleFilter;
import se.yarin.morphy.games.filters.RatingRangeFilter;
import se.yarin.morphy.queries.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class QueryTest {
    public QueryTest(Database db) {
        this.db = db;
    }

    public static void main(String[] args) throws IOException {
        Database db = Database.open(new File("/Users/yarin/chess/bases/Mega2021/Mega Database 2021.cbh"), DatabaseMode.READ_ONLY);

        QueryTest queryTest = new QueryTest(db);

        //queryTest.getCarlsenGames();
        //queryTest.getWorld();
        //queryTest.iterateTournaments();
        queryTest.iteratePlayers();
    }

    private void iteratePlayers() {
        long start = System.currentTimeMillis();
        EntityIndexReadTransaction<Player> txn = db.playerIndex().beginReadTransaction();

        EntityBatchIterator<Player> iterator = new EntityBatchIterator<>(txn, 0, new PlayerNameFilter("carlsen", "m", false, false));
        //Iterator<Player> iterator = txn.iterableAscending(new TournamentCategoryFilter(15, 99)).iterator();
        //Iterator<Player> iterator = txn.iterableDescending(new TournamentCategoryFilter(15, 99)).iterator();

        int cnt = 0;
        while (iterator.hasNext()) {
            cnt += 1;
            iterator.next();
        }
        System.out.println(cnt + " players");
        long elapsed = System.currentTimeMillis() - start;
        System.out.println(elapsed + " ms");
        db.context().instrumentation().show(2);
    }

    private final Database db;

    private void iterateTournaments() {
        long start = System.currentTimeMillis();
        EntityIndexReadTransaction<Tournament> txn = db.tournamentIndex().beginReadTransaction();

        // EntityBatchIterator<Tournament> iterator = new EntityBatchIterator<>(txn, 0, new TournamentCategoryFilter(15, 99));
        //Iterator<Tournament> iterator = txn.iterableAscending(new TournamentCategoryFilter(15, 99)).iterator();
        Iterator<Tournament> iterator = txn.iterableDescending(new TournamentCategoryFilter(15, 99)).iterator();

        int cnt = 0;
        while (iterator.hasNext()) {
            cnt += 1;
            iterator.next();
        }
        System.out.println(cnt + " tournaments");
        long elapsed = System.currentTimeMillis() - start;
        System.out.println(elapsed + " ms");
        db.context().instrumentation().show(2);
    }

    private void getCarlsenGames() {
        ItemQuery<Game> query = new QGamesByPlayers(new QPlayersWithName(new PlayerNameFilter("Carlsen", "Magnus", true, true)));
        runGameQuery(query, false);
    }

    private void getWorld() {
        ItemQuery<Game> worldChGamesQuery = new QGamesByTournaments(new QTournamentsWithTitle( new TournamentTitleFilter("World-ch", true, false)));
        //runGameQuery(worldChGamesQuery);

        ItemQuery<Player> players = new QPlayersByGames(worldChGamesQuery);
        //runPlayerQuery(players);

        ItemQuery<Game> stahlAnnotatedGames = new QGamesByAnnotators(new QAnnotatorsWithName(new AnnotatorNameFilter("Stohl", true, false)));
        //runGameQuery(stahlAnnotatedGames);

        ItemQuery<Game> topRatingGames = new QGamesWithRating(2600, 3000, RatingRangeFilter.RatingColor.BOTH);
        //runGameQuery(topRatingGames);

        ItemQuery<Game> joinedQuery1 = new QAnd<>(Arrays.asList(worldChGamesQuery, stahlAnnotatedGames, topRatingGames));
        //runGameQuery(joinedQuery1, false); // 6816 ms

        ItemQuery<Game> joinedQuery2 = new QAnd<>(Arrays.asList(topRatingGames, worldChGamesQuery, stahlAnnotatedGames));
        //runGameQuery(joinedQuery2, false); // 6266 ms

        ItemQuery<Game> joinedQuery3 = new QAnd<>(Arrays.asList(stahlAnnotatedGames, topRatingGames, worldChGamesQuery));
        runGameQuery(joinedQuery3, false); // 5912 ms
    }

    private void runGameQuery(ItemQuery<Game> gameQuery, boolean summaryOnly) {
        long start = System.currentTimeMillis();
        try (var txn = new DatabaseReadTransaction(db)) {
            List<Game> games = gameQuery.stream(txn).collect(Collectors.toList());
            if (!summaryOnly) {
                for (Game game : games) {
                    if (game.guidingText()) {
                        System.out.printf("%8d Text%n", game.id());
                    } else {
                        System.out.printf("%8d %4d  %-20s - %-20s  %s%n",
                                game.id(), game.playedDate().year(), game.white().getFullNameShort(), game.black().getFullNameShort(), game.result());
                    }
                }
            }
            long elapsed = System.currentTimeMillis() - start;
            System.out.printf("%d games (%d ms)%n", games.size(), elapsed);
        }
    }

    private void runPlayerQuery(ItemQuery<Player> playerQuery) {
        long start = System.currentTimeMillis();
        try (var txn = new DatabaseReadTransaction(db)) {
            List<Player> players = playerQuery.stream(txn).collect(Collectors.toList());
            for (Player player : players) {
                System.out.printf("%8d %s%n", player.id(), player.getFullName());
            }
            long elapsed = System.currentTimeMillis() - start;
            System.out.printf("%d players (%d ms)%n", players.size(), elapsed);
        }
    }
}
