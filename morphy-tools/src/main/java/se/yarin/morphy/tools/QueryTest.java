package se.yarin.morphy.tools;

import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.AnnotatorNameFilter;
import se.yarin.morphy.entities.filters.PlayerNameFilter;
import se.yarin.morphy.entities.filters.TournamentTitleFilter;
import se.yarin.morphy.games.filters.RatingRangeFilter;
import se.yarin.morphy.queries.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
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
        queryTest.getWorld();
    }

    private final Database db;

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
