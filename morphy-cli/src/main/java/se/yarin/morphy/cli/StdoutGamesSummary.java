package se.yarin.morphy.cli;

import se.yarin.cbhlib.entities.PlayerEntity;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.search.GameSearcher;
import se.yarin.chess.GameResult;

public class StdoutGamesSummary implements GameConsumer {

    private final boolean showTotal;

    public StdoutGamesSummary(boolean showTotal) {
        this.showTotal = showTotal;
    }

    @Override
    public void init() {
        outputHeader();
    }

    @Override
    public void done(GameSearcher.SearchResult result) {
        if (showTotal) {
            if (result.getTotalHits() == 0) {
                System.out.printf("No hits (%.2f s)%n", result.getElapsedTime() / 1000.0);
            } else {
                System.out.println();
                if (result.getConsumedHits() < result.getTotalHits()) {
                    System.out.printf("%d out of %d hits displayed (%.2f s)%n", result.getHits().size(), result.getTotalHits(), result.getElapsedTime() / 1000.0);
                } else {
                    System.out.printf("%d hits  (%.2f s)%n", result.getTotalHits(), result.getElapsedTime() / 1000.0);
                }
            }
        }
    }

    private void outputHeader() {
        output("Game #",
                "White",
                "",
                "Black",
                "",
                "Res",
                "Mov",
                "ECO",
                "Tournament",
                "Date");
        System.out.println("-".repeat(120));
    }

    private void output(String... columns) {
        System.out.printf("%8s  %-20s %4s  %-20s %4s  %3s %3s  %3s  %-30s  %10s%n", (Object[]) columns);
    }

    @Override
    public void accept(GameSearcher.Hit hit) {
        GameHeader header = hit.getGameHeader();

        int gameId = header.getId();

        String whitePlayer, blackPlayer, whiteElo, blackElo, result, numMoves, eco, tournament;
        if (header.isGuidingText()) {
            whitePlayer = "?"; // TODO: Where is the Text title stored?
            blackPlayer = "";
            whiteElo = "";
            blackElo = "";
            result = "Txt";
            numMoves = "";
            eco = "";
            tournament = "";
        } else {
            PlayerEntity white = hit.getWhite();
            PlayerEntity black = hit.getBlack();
            whitePlayer = white.getFullNameShort();
            blackPlayer = black.getFullNameShort();
            whiteElo = header.getWhiteElo() == 0 ? "" : Integer.toString(header.getWhiteElo());
            blackElo = header.getBlackElo() == 0 ? "" : Integer.toString(header.getBlackElo());
            result = header.getResult().toString();
            if (header.getResult() == GameResult.DRAW) {
                result = "½-½";
            } else if (header.getResult() == GameResult.NOT_FINISHED) {
                result = header.getLineEvaluation().toASCIIString();
            }
            numMoves = header.getNoMoves() > 0 ? Integer.toString(header.getNoMoves()) : "";
            eco = header.getEco().toString().substring(0, 3);
            if (eco.equals("???")) {
                eco = "";
            }
            tournament = hit.getTournament().getTitle();
        }

        String playedDate = header.getPlayedDate().toPrettyString();

        output(Integer.toString(gameId),
                limit(whitePlayer, 20),
                limit(whiteElo, 4),
                limit(blackPlayer, 20),
                limit(blackElo, 4),
                limit(result, 3),
                limit(numMoves, 3),
                limit(eco, 3),
                limit(tournament, 30),
                limit(playedDate, 10));
    }

    private String limit(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }
}
