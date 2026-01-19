package se.yarin.morphy.tools;

import se.yarin.chess.*;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseWriteTransaction;
import se.yarin.morphy.games.annotations.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Tool to generate a small test database with various annotation types.
 * This database is used by PgnDatabaseRoundTripTest to verify that annotations
 * survive a full round-trip through PGN export and import.
 */
public class GenerateTestDatabase {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: GenerateTestDatabase <output-path>");
            System.err.println("Example: GenerateTestDatabase src/test/resources/test-annotations.cbh");
            System.exit(1);
        }

        String outputPath = args[0];
        File outputFile = new File(outputPath);

        // Delete existing database if it exists
        if (outputFile.exists()) {
            System.out.println("Deleting existing database: " + outputPath);
            deleteDatabase(outputFile);
        }

        System.out.println("Creating test database: " + outputPath);
        Database db = Database.create(outputFile);

        try (var txn = new DatabaseWriteTransaction(db)) {
            // Game 1: Simple game with NAG annotations
            txn.addGame(createGameWithNAGs());

            // Game 2: Game with text comments
            txn.addGame(createGameWithTextComments());

            // Game 3: Game with mixed annotations (NAGs + text)
            txn.addGame(createGameWithMixedAnnotations());

            // Game 4: Game with multiple NAGs consolidated
            txn.addGame(createGameWithMultipleNAGs());

            // Game 5: Game with variations and annotations
            txn.addGame(createGameWithVariations());

            // Game 6: Game with graphical annotations
            txn.addGame(createGameWithGraphicalAnnotations());

            txn.commit();
        }

        db.close();

        System.out.println("Successfully created test database with 6 games");
    }

    private static GameModel createGameWithNAGs() {
        GameModel game = new GameModel();
        game.header().setEvent("Test NAGs");
        game.header().setWhite("Player 1");
        game.header().setBlack("Player 2");
        game.header().setDate(new Date(2024, 1, 15));
        game.header().setResult(GameResult.WHITE_WINS);

        GameMovesModel.Node node = game.moves().root();

        // 1. e4 $1 (good move)
        node = node.addMove(new ShortMove(Chess.E2, Chess.E4));
        node.addAnnotation(ImmutableSymbolAnnotation.of(NAG.GOOD_MOVE, NAG.NONE, NAG.NONE));

        // 1... e5 $2 (bad move)
        node = node.addMove(new ShortMove(Chess.E7, Chess.E5));
        node.addAnnotation(ImmutableSymbolAnnotation.of(NAG.BAD_MOVE, NAG.NONE, NAG.NONE));

        return game;
    }

    private static GameModel createGameWithTextComments() {
        GameModel game = new GameModel();
        game.header().setEvent("Test Text");
        game.header().setWhite("Player 1");
        game.header().setBlack("Player 2");
        game.header().setDate(new Date(2024, 1, 15));
        game.header().setResult(GameResult.WHITE_WINS);

        GameMovesModel.Node node = game.moves().root();

        // 1. e4 {Best move}
        node = node.addMove(new ShortMove(Chess.E2, Chess.E4));
        node.addAnnotation(ImmutableTextAfterMoveAnnotation.of("Best move"));

        return game;
    }

    private static GameModel createGameWithMixedAnnotations() {
        GameModel game = new GameModel();
        game.header().setEvent("Test Mixed");
        game.header().setWhite("Player 1");
        game.header().setBlack("Player 2");
        game.header().setDate(new Date(2024, 1, 15));
        game.header().setResult(GameResult.WHITE_WINS);

        GameMovesModel.Node node = game.moves().root();

        // 1. e4 $1 {King's pawn}
        node = node.addMove(new ShortMove(Chess.E2, Chess.E4));
        node.addAnnotation(ImmutableSymbolAnnotation.of(NAG.GOOD_MOVE, NAG.NONE, NAG.NONE));
        node.addAnnotation(ImmutableTextAfterMoveAnnotation.of("King's pawn"));

        return game;
    }

    private static GameModel createGameWithMultipleNAGs() {
        GameModel game = new GameModel();
        game.header().setEvent("Test Multiple NAGs");
        game.header().setWhite("Player 1");
        game.header().setBlack("Player 2");
        game.header().setDate(new Date(2024, 1, 15));
        game.header().setResult(GameResult.WHITE_WINS);

        GameMovesModel.Node node = game.moves().root();

        // 1. e4 $1 $14 (good move + white has slight advantage)
        node = node.addMove(new ShortMove(Chess.E2, Chess.E4));
        node.addAnnotation(ImmutableSymbolAnnotation.of(
            NAG.GOOD_MOVE,              // MOVE_COMMENT
            NAG.NONE,                    // MOVE_PREFIX
            NAG.WHITE_SLIGHT_ADVANTAGE  // LINE_EVALUATION
        ));

        return game;
    }

    private static GameModel createGameWithVariations() {
        GameModel game = new GameModel();
        game.header().setEvent("Test Variations");
        game.header().setWhite("Player 1");
        game.header().setBlack("Player 2");
        game.header().setDate(new Date(2024, 1, 15));
        game.header().setResult(GameResult.WHITE_WINS);

        GameMovesModel.Node node = game.moves().root();

        // 1. e4 $1
        GameMovesModel.Node e4 = node.addMove(new ShortMove(Chess.E2, Chess.E4));
        e4.addAnnotation(ImmutableSymbolAnnotation.of(NAG.GOOD_MOVE, NAG.NONE, NAG.NONE));

        // 1... e5 (main line)
        GameMovesModel.Node e5 = e4.addMove(new ShortMove(Chess.E7, Chess.E5));

        // 1... c5 $3 {Sicilian} (variation)
        GameMovesModel.Node c5 = e4.addMove(new ShortMove(Chess.C7, Chess.C5));
        c5.addAnnotation(ImmutableSymbolAnnotation.of(NAG.VERY_GOOD_MOVE, NAG.NONE, NAG.NONE));
        c5.addAnnotation(ImmutableTextAfterMoveAnnotation.of("Sicilian"));

        return game;
    }

    private static GameModel createGameWithGraphicalAnnotations() {
        GameModel game = new GameModel();
        game.header().setEvent("Test Graphical");
        game.header().setWhite("Player 1");
        game.header().setBlack("Player 2");
        game.header().setDate(new Date(2024, 1, 15));
        game.header().setResult(GameResult.WHITE_WINS);

        GameMovesModel.Node node = game.moves().root();

        // 1. e4 with graphical squares and arrows
        node = node.addMove(new ShortMove(Chess.E2, Chess.E4));

        // Add colored squares annotation [%csl Ga4,Rb5]
        node.addAnnotation(ImmutableGraphicalSquaresAnnotation.of(List.of(
            ImmutableSquare.of(GraphicalAnnotationColor.GREEN, Chess.strToSqi("a4")),
            ImmutableSquare.of(GraphicalAnnotationColor.RED, Chess.strToSqi("b5"))
        )));

        // Add colored arrows annotation [%cal Ge2e4,Rh1h8]
        node.addAnnotation(ImmutableGraphicalArrowsAnnotation.of(List.of(
            ImmutableArrow.of(GraphicalAnnotationColor.GREEN, Chess.strToSqi("e2"), Chess.strToSqi("e4")),
            ImmutableArrow.of(GraphicalAnnotationColor.RED, Chess.strToSqi("h1"), Chess.strToSqi("h8"))
        )));

        return game;
    }

    private static void deleteDatabase(File file) {
        String basePath = file.getAbsolutePath();
        if (basePath.endsWith(".cbh")) {
            basePath = basePath.substring(0, basePath.length() - 4);
        }

        new File(basePath + ".cbh").delete();
        new File(basePath + ".cbg").delete();
        new File(basePath + ".cba").delete();
        new File(basePath + ".cbe").delete();
        new File(basePath + ".cbs").delete();
        new File(basePath + ".cbc").delete();
        new File(basePath + ".cbp").delete();
        new File(basePath + ".cbt").delete();
        new File(basePath + ".cbtt").delete();
        new File(basePath + ".cit").delete();
    }
}
