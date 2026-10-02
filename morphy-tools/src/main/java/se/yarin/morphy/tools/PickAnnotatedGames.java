package se.yarin.morphy.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import se.yarin.chess.GameModel;
import se.yarin.chess.NAG;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.cb2.Database2Cbh;
import se.yarin.morphy.cb2.Game;
import se.yarin.morphy.cb2.ReadTransaction;
import se.yarin.morphy.cb2.annotations.AnnotationBlockCodec;
import se.yarin.morphy.chessbase.Nation;
import se.yarin.morphy.chessbase.annotations.BlackClockAnnotation;
import se.yarin.morphy.chessbase.annotations.ComputerEvaluationAnnotation;
import se.yarin.morphy.chessbase.annotations.CorrespondenceMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.CriticalPositionAnnotation;
import se.yarin.morphy.chessbase.annotations.GameQuotationAnnotation;
import se.yarin.morphy.chessbase.annotations.GraphicalAnnotationColor;
import se.yarin.morphy.chessbase.annotations.GraphicalArrowsAnnotation;
import se.yarin.morphy.chessbase.annotations.GraphicalSquaresAnnotation;
import se.yarin.morphy.chessbase.annotations.MedalAnnotation;
import se.yarin.morphy.chessbase.annotations.PawnStructureAnnotation;
import se.yarin.morphy.chessbase.annotations.PiecePathAnnotation;
import se.yarin.morphy.chessbase.annotations.SymbolAnnotation;
import se.yarin.morphy.chessbase.annotations.TextAfterMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.TextBeforeMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.TimeControlAnnotation;
import se.yarin.morphy.chessbase.annotations.TimeSpentAnnotation;
import se.yarin.morphy.chessbase.annotations.VariationColorAnnotation;
import se.yarin.morphy.chessbase.annotations.VideoStreamTimeAnnotation;
import se.yarin.morphy.chessbase.annotations.WebLinkAnnotation;
import se.yarin.morphy.chessbase.annotations.WhiteClockAnnotation;

/**
 * Picks a few games for every kind of annotation out of a big v2 database, like Mega Database, and
 * writes them to a new v2 database, to have real games to test showing annotations with.
 *
 * <p>Only the annotations of a game are read while looking, not its moves, and only for games that
 * have any. A game is taken if it has a kind that hasn't been found often enough yet; the search
 * stops when every kind has been, or the whole database has been looked through. Some kinds are
 * split further: text in a language, symbols before the move, arrows in uncommon colors and
 * evaluations of mate. Training annotations and those of unknown kinds are left out.
 *
 * <p>Usage: PickAnnotatedGames &lt;source.2cbh&gt; &lt;target.2cbh&gt; [games per kind, default 3]
 */
public class PickAnnotatedGames {

  /** The kinds looked for, in the order they are reported. */
  private static final List<String> KINDS =
      List.of(
          "textBefore",
          "textAfter",
          "textInLanguage",
          "symbols",
          "symbolsPrefix",
          "squares",
          "arrows",
          "arrowsUncommonColor",
          "whiteClock",
          "blackClock",
          "timeSpent",
          "timeControl",
          "eval",
          "evalMate",
          "critical",
          "medals",
          "pawnStructure",
          "piecePath",
          "variationColor",
          "videoStreamTime",
          "webLink",
          "quote",
          "correspondence");

  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      System.err.println("Usage: PickAnnotatedGames <source.2cbh> <target.2cbh> [games per kind]");
      System.exit(1);
    }
    File target = new File(args[1]);
    int perKind = args.length > 2 ? Integer.parseInt(args[2]) : 3;

    Map<String, Integer> found = new LinkedHashMap<>();
    KINDS.forEach(kind -> found.put(kind, 0));
    List<GameModel> picked = new ArrayList<>();
    List<Integer> pickedIds = new ArrayList<>();
    List<String> pickedInfo = new ArrayList<>();

    try (Database2Cbh source = Database2Cbh.open(new File(args[0]), AccessMode.READ_ONLY);
        ReadTransaction txn = new ReadTransaction(source)) {
      int count = txn.count();
      long start = System.currentTimeMillis();
      for (int id = 1; id <= count; id++) {
        if (id % 500_000 == 0) {
          System.out.printf(
              "%d/%d games looked at, %d picked, %.0f s%n",
              id, count, picked.size(), (System.currentTimeMillis() - start) / 1000.0);
        }
        Game game;
        try {
          game = txn.getGame(id);
          if (!game.isGame() || game.deleted() || game.record().annotationOffset() == 0) {
            continue;
          }
        } catch (RuntimeException e) {
          continue;
        }

        Set<String> kinds;
        try {
          byte[] content = source.annotationFile().read(game.record().annotationOffset()).content();
          List<AnnotationBlockCodec.Positioned> annotations = AnnotationBlockCodec.read(content);
          if (annotations == null) {
            continue;
          }
          kinds = kindsOf(annotations);
        } catch (RuntimeException e) {
          continue;
        }

        List<String> needed =
            kinds.stream().filter(kind -> found.get(kind) < perKind).toList();
        if (needed.isEmpty()) {
          continue;
        }
        GameModel model;
        try {
          model = game.model();
        } catch (RuntimeException e) {
          continue;
        }
        model.header().clearEntityIds();
        picked.add(model);
        pickedIds.add(id);
        kinds.forEach(kind -> found.merge(kind, 1, Integer::sum));
        pickedInfo.add(
            String.format(
                "%d: %s - %s, %s  %s",
                id,
                model.header().getWhite(),
                model.header().getBlack(),
                model.header().getEvent(),
                kinds));
        if (found.values().stream().allMatch(n -> n >= perKind)) {
          System.out.println("Every kind found after " + id + " games");
          break;
        }
      }
    }

    System.out.println();
    found.forEach((kind, n) -> System.out.printf("%-20s %d%n", kind, n));
    System.out.println();
    pickedInfo.forEach(System.out::println);

    int written = 0;
    try (Database2Cbh out = Database2Cbh.create(target)) {
      for (int i = 0; i < picked.size(); i++) {
        try {
          out.addGame(picked.get(i));
          written++;
        } catch (RuntimeException e) {
          System.out.println("Game " + pickedIds.get(i) + " can't be written: " + e);
        }
      }
    }
    System.out.println();
    System.out.println(written + " games written to " + target);
  }

  private static Set<String> kindsOf(List<AnnotationBlockCodec.Positioned> annotations) {
    Set<String> kinds = new TreeSet<>();
    for (AnnotationBlockCodec.Positioned positioned : annotations) {
      Annotation annotation = positioned.annotation();
      switch (annotation) {
        case TextBeforeMoveAnnotation a -> {
          kinds.add("textBefore");
          if (isOtherLanguage(a.language())) kinds.add("textInLanguage");
        }
        case TextAfterMoveAnnotation a -> {
          kinds.add("textAfter");
          if (isOtherLanguage(a.language())) kinds.add("textInLanguage");
        }
        case SymbolAnnotation a -> {
          kinds.add("symbols");
          if (a.movePrefix() != NAG.NONE) kinds.add("symbolsPrefix");
        }
        case GraphicalSquaresAnnotation a -> kinds.add("squares");
        case GraphicalArrowsAnnotation a -> {
          kinds.add("arrows");
          if (a.arrows().stream().anyMatch(r -> isUncommon(r.color()))) {
            kinds.add("arrowsUncommonColor");
          }
        }
        case WhiteClockAnnotation a -> kinds.add("whiteClock");
        case BlackClockAnnotation a -> kinds.add("blackClock");
        case TimeSpentAnnotation a -> kinds.add("timeSpent");
        case TimeControlAnnotation a -> kinds.add("timeControl");
        case ComputerEvaluationAnnotation a -> {
          kinds.add("eval");
          if (a.evalType() == 1) kinds.add("evalMate");
        }
        case CriticalPositionAnnotation a -> kinds.add("critical");
        case MedalAnnotation a -> kinds.add("medals");
        case PawnStructureAnnotation a -> kinds.add("pawnStructure");
        case PiecePathAnnotation a -> kinds.add("piecePath");
        case VariationColorAnnotation a -> kinds.add("variationColor");
        case VideoStreamTimeAnnotation a -> kinds.add("videoStreamTime");
        case WebLinkAnnotation a -> kinds.add("webLink");
        case GameQuotationAnnotation a -> kinds.add("quote");
        case CorrespondenceMoveAnnotation a -> kinds.add("correspondence");
        default -> {}
      }
    }
    return kinds;
  }

  private static boolean isOtherLanguage(Nation language) {
    return language != Nation.NONE && !language.getIocCode().toUpperCase(Locale.ROOT).equals("ENG");
  }

  private static boolean isUncommon(GraphicalAnnotationColor color) {
    return color != GraphicalAnnotationColor.GREEN
        && color != GraphicalAnnotationColor.RED
        && color != GraphicalAnnotationColor.YELLOW;
  }
}
