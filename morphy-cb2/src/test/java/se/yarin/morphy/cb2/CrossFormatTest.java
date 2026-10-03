package se.yarin.morphy.cb2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameModelComparator;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.NAG;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.DatabaseCbh;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.chessbase.annotations.GameQuotationAnnotation;
import se.yarin.morphy.chessbase.annotations.SymbolAnnotation;
import se.yarin.morphy.chessbase.annotations.TextAfterMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.TextBeforeMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.TrainingAnnotation;
import se.yarin.morphy.chessbase.annotations.UnknownAnnotation;

/**
 * The sample database was converted by ChessBase from the v1 database next to it, keeping the
 * game ids, so each game read from one must equal the same game read from the other, except where
 * the conversion itself changed something:
 *
 * <ul>
 *   <li>tournament places and nations were revised (e.g. URS became RUS);
 *   <li>annotators got a space after the comma, as they became player entities;
 *   <li>v2 stores some symbols in another of its three slots, e.g. the only-move symbol;
 *   <li>engine evaluations and training questions have a different binary layout in each format,
 *       kept as it is, and v2 splits a quoted game's names into last and first;
 *   <li>v1 guesses the charset of a comment, and guesses wrong for some that v2 reads right;
 *   <li>links in guiding texts were rewritten, and old plain-text texts were made HTML.
 * </ul>
 */
class CrossFormatTest {

  private static final Set<String> CONVERTED_FIELDS = Set.of("eventSite", "eventCountry");

  @Test
  void gamesMatchTheV1Database() throws IOException {
    Map<String, Integer> differences = new TreeMap<>();
    int games = 0;
    try (DatabaseCbh v1 = DatabaseCbh.open(TestDatabases.worldCh(), DatabaseMode.READ_ONLY);
        Database2Cbh v2 = Database2Cbh.open(TestDatabases.wch2(), AccessMode.READ_ONLY);
        ReadTransaction txn = new ReadTransaction(v2)) {
      assertEquals(v1.count(), v2.count());
      for (int id = 1; id <= v1.count(); id++) {
        se.yarin.morphy.Game g1 = v1.getGame(id);
        Game g2 = txn.getGame(id);
        assertEquals(g1.guidingText(), g2.isText(), "kind of " + id);
        if (g2.isText()) {
          // v1 holds an empty HTML document for languages v2 leaves empty, and older v1 texts
          // (format 1 and 2) are plain text that the conversion made into HTML
          var v1Contents = g1.getTextModel().contents();
          if (v1Contents.format() != 3) {
            continue;
          }
          var contents1 = v1Contents.contents();
          g2.textContents()
              .contents()
              .forEach(
                  (language, html) -> {
                    String other = contents1.get(language);
                    if (!html.isEmpty() && !html.equals(other) && !linksDiffer(other, html)) {
                      count(differences, "text", g2.id(), language.toString());
                    }
                  });
          continue;
        }
        games++;
        GameModel m1 = g1.getModel(), m2 = g2.model();
        GameModelComparator.ComparisonResult result = GameModelComparator.compare(m1, m2);
        for (GameModelComparator.FieldDifference d : result.getHeaderDifferences()) {
          String field = d.fieldName();
          if (CONVERTED_FIELDS.contains(field)
              || (field.equals("annotator")
                  && d.value1().toString().equals(d.value2().toString().replace(", ", ",")))) {
            continue;
          }
          count(differences, "header " + field, id, d.toString());
        }
        if (!result.variationsMatch()) {
          count(differences, "moves", id, result.toString());
        }
        if (result.setupPositionsDiffer() || result.setupPlyDiffers()) {
          count(differences, "setup", id, "");
        }
        List<GameMovesModel.Node> nodes1 = m1.moves().getAllNodes();
        List<GameMovesModel.Node> nodes2 = m2.moves().getAllNodes();
        for (int i = 0; i < nodes1.size(); i++) {
          List<String> a1 = describe(nodes1.get(i)), a2 = describe(nodes2.get(i));
          if (!matches(a1, a2) && !symbolsOnlyDiffer(nodes1.get(i), nodes2.get(i), a1, a2)) {
            count(differences, "annotations", id, a1 + " vs " + a2);
          }
        }
      }
    }
    System.out.println(games + " games compared; differences: " + differences);
    assertEquals(Map.of(), differences);
  }

  // The symbols of each node described, to compare them as sets
  private static final Map<GameMovesModel.Node, Set<NAG>> symbols = new IdentityHashMap<>();

  /** The annotations of a node as comparable text, leaving out what the formats store apart. */
  private static List<String> describe(GameMovesModel.Node node) {
    List<String> out = new ArrayList<>();
    for (Annotation a : node.getAnnotations()) {
      switch (a) {
        case UnknownAnnotation u -> {}
        case TrainingAnnotation t -> {}
        // v2 stores the last and first name apart, joined here with ", "
        case GameQuotationAnnotation q ->
            out.add(
                "quotation "
                    + q.header().getWhite().replace(", ", ",")
                    + " "
                    + q.header().getResult());
        case SymbolAnnotation s -> {
          Set<NAG> nags = new TreeSet<>(List.of(s.moveComment(), s.lineEvaluation(), s.movePrefix()));
          nags.remove(NAG.NONE);
          out.add("symbols " + nags);
          symbols.put(node, nags);
        }
        case TextAfterMoveAnnotation t -> addText(out, "after", t.language(), t.text());
        case TextBeforeMoveAnnotation t -> addText(out, "before", t.language(), t.text());
        default -> out.add(a.toString());
      }
    }
    out.sort(null);
    return out;
  }

  /**
   * Whether two texts are the same up to a link the conversion rewrote: one into the media folder
   * of the database, which is named after the database, or a ChessBase search link.
   */
  private static boolean linksDiffer(String v1, String v2) {
    int at = 0;
    while (at < Math.min(v1.length(), v2.length()) && v1.charAt(at) == v2.charAt(at)) {
      at++;
    }
    return (v1.startsWith("World-ch.html", at) && v2.startsWith("wch2.html", at))
        || v1.substring(Math.max(0, at - 40), at).contains("CBLink(");
  }

  private static void addText(List<String> out, String kind, Object language, String text) {
    out.add(kind + " " + language + " " + text);
  }

  /**
   * Whether the v1 annotations match the v2 ones. v1 guesses the charset of a comment, and some
   * guesses are wrong, so a comment read by v1 is only compared up to its first non-ASCII
   * character.
   */
  private static boolean matches(List<String> v1, List<String> v2) {
    if (v1.size() != v2.size()) {
      return false;
    }
    for (int i = 0; i < v1.size(); i++) {
      String a = v1.get(i), b = v2.get(i);
      int end = 0;
      while (end < a.length() && a.charAt(end) < 128) {
        end++;
      }
      if (end == a.length() ? !a.equals(b) : !b.startsWith(a.substring(0, end))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Whether the annotations differ only in that v2 has more symbols. A v1 symbol annotation keeps
   * one symbol of each kind, while v2 may store two of a kind in different slots.
   */
  private static boolean symbolsOnlyDiffer(
      GameMovesModel.Node n1, GameMovesModel.Node n2, List<String> a1, List<String> a2) {
    Set<NAG> s1 = symbols.get(n1), s2 = symbols.get(n2);
    if (s1 == null || s2 == null || !s2.containsAll(s1)) {
      return false;
    }
    List<String> r1 = new ArrayList<>(a1), r2 = new ArrayList<>(a2);
    r1.removeIf(x -> x.startsWith("symbols "));
    r2.removeIf(x -> x.startsWith("symbols "));
    return matches(r1, r2);
  }

  private static final Map<String, Integer> SHOWN = new TreeMap<>();

  private static void count(Map<String, Integer> differences, String kind, int id, String detail) {
    differences.merge(kind, 1, Integer::sum);
    if (SHOWN.merge(kind, 1, Integer::sum) <= 3) {
      System.out.println("game " + id + ": " + kind + ": " + detail);
    }
  }
}
