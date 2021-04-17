package se.yarin.morphy;

import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.games.Medal;
import se.yarin.morphy.games.annotations.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * Class that generates random games, with random meta data, random variations and random annotations.
 */
public class GameGenerator {

    private final Random random;

    private static final String[] players = new String[] {
        "Carlsen, Magnus", "Vachier-Lagrave, Maxime", "Kramnik, Vladimir",
        "Caruana, Fabiano", "Aronian, Levon", "Nakamura, Hikaru",
        "So, Wesley", "Anand, Viswanathan", "Giri, Anish",
        "Karjakin, Sergey", "Mamedyarov, Shakhriyar", "Topalov, Veselin",
        "Ding, Liren", "Grischuk, Alexander", "Li, Chao b", "Harikrishna, P.",
        "Rapport, Richard", "Svidler, Peter", "Gelfand, Boris", "Navara, David" };

    private static final String[] teams = new String[] {
        "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
        "Utrecht", "Vasas", "Ajka", "Haladas", "Torekves", "Clichy" };

    private static final String[] tournaments = new String[] {
        "Bilbao 2016", "Shamkir 2016", "Stavanger 2016", "Moscow Candidates 2016",
        "Wijk aan Zee 2016", "London 2016", "Baku 2015", "Saint Louis 2015",
        "Biel 2015", "Dortmund 2015", "Khanty-Mansisyk 2015" };

    private static final String[] annotators = new String[] {
        "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
        "Ftacnik, L", "Bulletin", "ChessBase", "Ribli", "Hecht", "Finkel, A", "Stohl, I" };

    private static final String[] sources = new String[] {
        "CBM 173", "CBM 172", "CBM 171", "CBM 170", "CBM 169", "CBM 168" };

    public GameGenerator() {
        this(0);
    }

    public GameGenerator(int seed) {
        random = new Random(seed);
    }

    public GameModel getRandomGame() {
        return getRandomGame(random.nextDouble() < 0.1, random.nextDouble() < 0.05);
    }

    public GameModel getRandomGame(boolean withVariations, boolean withAnnotations) {
        GameHeaderModel header = getRandomGameHeader();
        GameMovesModel moves = getRandomGameMoves(20 + random.nextInt(80));
        if (withVariations) {
            addRandomVariationMoves(moves, random.nextInt(200) + 5);
        }
        withAnnotations = false;
        if (withAnnotations) {
            int posCnt = moves.getAllNodes().size();
            addRandomAnnotations(moves, random.nextInt(posCnt) + 5);
        }

        return new GameModel(header, moves);
    }

    /**
     * Generates a random game header
     * @return a game header model
     */
    public GameHeaderModel getRandomGameHeader() {
        GameHeaderModel model = new GameHeaderModel();
        model.setWhite(players[random.nextInt(players.length)]);
        model.setBlack(players[random.nextInt(players.length)]);
        model.setEvent(tournaments[random.nextInt(tournaments.length)]);
        model.setAnnotator(annotators[random.nextInt(annotators.length)]);
        model.setSourceTitle(sources[random.nextInt(sources.length)]);
        model.setWhiteTeam(teams[random.nextInt(teams.length)]);
        model.setBlackTeam(teams[random.nextInt(teams.length)]);
        model.setDate(new Date(2016, random.nextInt(12) + 1, random.nextInt(28) + 1));
        model.setEco(Eco.fromInt(random.nextInt(500)));
        model.setRound(random.nextInt(11) + 1);
        model.setSubRound(random.nextInt(10) == 0 ? (random.nextInt(3) + 1) : 0);
        model.setWhiteElo(random.nextInt(100) + 2750);
        model.setBlackElo(random.nextInt(100) + 2750);
        model.setResult(GameResult.values()[random.nextInt(3)]);
        return model;
    }

    /**
     * Generates a game containing a random sequence of moves in one main line
     * @param noMoves the number of moves in the game
     * @return the moves model
     */
    public GameMovesModel getRandomGameMoves(int noMoves) {
        GameMovesModel model = new GameMovesModel();
        GameMovesModel.Node current = model.root();
        while (noMoves > 0) {
            Move move = getRandomMove(current.position());
            if (move == null) break;
            current = current.addMove(move);
            noMoves--;
        }
        return model;
    }

    /**
     * Adds extra random moves to an existing game at random nodes in the game tree, creating variations
     * @param model a game model
     * @param noVariationMoves the number of moves to add
     */
    public void addRandomVariationMoves(GameMovesModel model, int noVariationMoves) {
        List<GameMovesModel.Node> nodes = model.getAllNodes();
        while (noVariationMoves > 0) {
            GameMovesModel.Node node = nodes.get(random.nextInt(nodes.size()));
            Move move = getRandomMove(node.position());
            if (move != null) {
                nodes.add(node.addMove(move));
            }
            noVariationMoves--;
        }
    }

    private String randomString(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append((char)('a' + random.nextInt(26)));
            if (random.nextInt(7) == 0) sb.append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * Adds random annotations to a game at random nodes
     * @param model a game model
     * @param noAnnotations the number of annotations to add
     */
    public void addRandomAnnotations(GameMovesModel model, int noAnnotations) {
        List<GameMovesModel.Node> nodes = model.getAllNodes();
        while (noAnnotations > 0) {
            GameMovesModel.Node node = nodes.get(random.nextInt(nodes.size()));

            Annotation annotation = null;
            switch (random.nextInt(12)) {
                case 0, 1, 2, 3 -> annotation = ImmutableTextAfterMoveAnnotation.of(randomString(random.nextInt(15) + 3));
                case 4 -> annotation = ImmutableTextBeforeMoveAnnotation.of(randomString(random.nextInt(15) + 3));
                case 5, 6, 7, 8 -> annotation = ImmutableSymbolAnnotation.of(NAG.values()[1 + random.nextInt(6)]);
                case 9 -> {
                    ArrayList<GraphicalSquaresAnnotation.Square> squares = new ArrayList<>();
                    int cnt = random.nextInt(3) + 1;
                    for (int i = 0; i < cnt; i++) {
                        GraphicalAnnotationColor color = GraphicalAnnotationColor.values()[2 + random.nextInt(3)];
                        squares.add(ImmutableSquare.of(color, random.nextInt(64)));
                    }
                    annotation = ImmutableGraphicalSquaresAnnotation.of(squares);
                }
                case 10 -> {
                    EnumSet<Medal> medals = EnumSet.noneOf(Medal.class);
                    for (Medal medal : Medal.values()) {
                        if (random.nextInt(8) == 0) {
                            medals.add(medal);
                        }
                    }
                    if (medals.size() == 0) medals.add(Medal.USER);
                    annotation = ImmutableMedalAnnotation.of(medals);
                }
                case 11 -> annotation = ImmutableCriticalPositionAnnotation.of(CriticalPositionAnnotation.CriticalPositionType.OPENING);
            }

            node.addAnnotation(annotation);

            noAnnotations--;
        }
    }

    private Move getRandomMove(Position position) {
        List<Move> moves = position.generateAllPseudoLegalMoves();
        if (moves.size() == 0) return null;
        Move move = moves.get(random.nextInt(moves.size()));
        if (!position.isMoveLegal(move)) {
            moves = position.generateAllLegalMoves();
            if (moves.size() == 0) return null;
            move = moves.get(random.nextInt(moves.size()));
        }
        return move;
    }
}
